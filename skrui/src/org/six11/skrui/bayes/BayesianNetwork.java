package org.six11.skrui.bayes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.six11.util.Debug;

/**
 * I manually decrypted the singly-connected belief propagation algorithm found in
 * "Learning Bayesian Networks" by Richard Neapolitan and have attempted to reconstruct it in Java.
 * It probably contains gigantic errors and oversights and should not be used for any purpose.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class BayesianNetwork {

  private static final int HOP_LIMIT = 200;
  private static double STABLE_THRESHOLD = 0.001;

  List<Node> nodes;
  List<Node> activatedNodes;
  List<Node> stable;
  Set<SlotKey> activatedStates;

  /**
   * Make an empty network, which is a very lonely thing.
   * 
   * Generally you'll want to add some nodes, link them together into a network, set their
   * conditional probability values, and then play with the magic of bayesian inferencing. You must
   * call initializeNetwork() once, and then you may tickle nodes by activating states using the
   * updateTree function. The node's informed beliefs can then be accessed from the nodes
   * themselves.
   */
  public BayesianNetwork() {
    nodes = new ArrayList<Node>();
    activatedNodes = new ArrayList<Node>();
    activatedStates = new HashSet<SlotKey>();
    stable = new ArrayList<Node>();
  }

  /**
   * Returns the current (most recently processed) set of active states.
   */
  public Set<SlotKey> getActivatedStates() {
    return activatedStates;
  }

  /**
   * Add the given node to the network. You still have to link it with parents/children and
   * establish probabilities.
   */
  public void addNode(Node n) {
    nodes.add(n);
  }

  /**
   * Gives all the nodes this network knows about.
   */
  public List<Node> getNodes() {
    return nodes;
  }

  /**
   * Initialize the network after you've added your nodes and established their graph relations and
   * probability tables.
   */
  public void initializeNetwork() {
    Support.where("Clearing activated nodes/states.");
    activatedNodes.clear();
    activatedStates.clear();

    Support.out("Init propagation slots of " + nodes.size() + " nodes");
    for (Node n : nodes) {
      n.initializePropagationSlots();
    }

    Support.out("Initializing child val/msg and parent messages.");
    for (Node n : nodes) {
      for (int i = 0; i < n.getStateCount(); i++) {
        n.setChildValue(i, 1); // Compute lambda values
      }
      Support.out("Init " + n.parents.size() + " child messages for parents of " + n.name);
      for (Node z : n.parents) {
        for (int i = 0; i < z.getStateCount(); i++) {
          z.setChildMessage(n, i, 1); // Compute lambda messages
        }
      }
      Support.out("Init " + n.children.size() + " parent messages for children of " + n.name);
      for (Node y : n.children) {
        for (int i = 0; i < n.getStateCount(); i++) {
          n.setParentMessage(y, i, 1); // Initialize pi messages
        }
      }
    }

    Set<Node> roots = getRoots(nodes);
    Support.out("Setting parent values on " + roots.size() + " roots.");
    for (Node r : roots) {
      Support.out("Root: " + r);
      for (int i = 0; i < r.getStateCount(); i++) {
        r.setParentValue(i, r.getProbabilityVector()[i]); // Compute R's pi values.
      }
      Support.out("Sending parent message to " + r.children.size() + " children of " + r + "...");
      for (Node x : r.children) {
        sendParentMessage(r, x, 0);
      }
    }
  }

  /**
   * Observe a node. The given object merely references a node and one of its states that is
   * considered true. This slot's probability is set to one, and all other slots in the node are set
   * to zero. The provided slot is added to the list of active slots. After running this you can ask
   * other nodes about their inferred beliefs (given the current set of activated slots).
   */
  public void updateTree(SlotKey obsSlotKey) {
    Support.printObserveBox(obsSlotKey);
    stable.clear();
    activatedNodes.add(obsSlotKey.node);
    activatedStates.add(obsSlotKey);

    // Zero out other child/parent/belief values for observed node, EXCEPT observed slot becomes 1.
    for (int i = 0; i < obsSlotKey.node.getStateCount(); i++) {
      double newValue = (i == obsSlotKey.index) ? 1 : 0;
      obsSlotKey.node.setChildValue(i, newValue);
      obsSlotKey.node.setParentValue(i, newValue);
      obsSlotKey.node.setInferredBelief(i, activatedStates, newValue);
    }

    for (Node z : obsSlotKey.node.parents) {
      // Do not propagate messages to observed nodes, or those considered stable, because we already
      // have all the info we need from them. Doing this avoids infinite loops (probably).
      if ((activatedNodes.contains(z) || stable.contains(z)) == false) {
        sendChildMessage(obsSlotKey.node, z, 0);
      }
    }

    for (Node x : obsSlotKey.node.children) {
      sendParentMessage(obsSlotKey.node, x, 0);
    }
  }

  private void sendParentMessage(Node z, Node x, int hopCount) {
    Support.where(">> BEGIN   Send Parent Message: " + z + " --> " + x + " hop count: " + hopCount);
    String exitMessage = " (" + hopCount + " / " + HOP_LIMIT + ")";
    if (hopCount > HOP_LIMIT) {
      exitMessage = " (maximum hop count reached. bailing!)";
    } else {
      for (int i = 0; i < z.getStateCount(); i++) {
        double prod = 1;
        for (Node y : z.children) {
          if (y != x) {
            prod = prod * z.getChildMessage(y)[i];
          }
        }
        prod = prod * z.getParentValue(i);
        z.setParentMessage(x, i, prod); // Z sends X a pi message
      }

      if ((activatedNodes.contains(x) || stable.contains(x)) == false) {
        SlotKey[][] combos = x.cpt.getParentIndexCombinations();
        for (int i = 0; i < x.getStateCount(); i++) {
          // This involves getting the probability of every combination of X's parents. In the
          // burglary example, for a1, this is:
          // ````````` term1 ````````` term2 ```` term3 `` (there may be more Pi_* terms)
          // pi(a1) = P(a1 | b1,f1) * Pi_A(b1) * Pi_A(f1) + // line 0
          // ```````` P(a1 | b1,f2) * Pi_A(b1) * Pi_A(f2) + // line 1
          // ```````` P(a1 | b2,f1) * Pi_A(b2) * Pi_A(f1) + // line 2
          // ```````` P(a1 | b2,f2) * Pi_A(b2) * Pi_A(f2) ; // line 3
          // 
          // ... and similarly for a2.
          double[] lines = new double[combos.length];
          for (int j = 0; j < combos.length; j++) {
            SlotKey[] parentValueSlots = combos[j];
            double[] myCptData = x.cpt.getData(parentValueSlots);
            double term1 = myCptData[i];
            double[] fromParents = new double[x.parents.size()];
            for (int k = 0; k < x.parents.size(); k++) {
              Node par = x.parents.get(k);
              int parIdx = parentValueSlots[k].index;
              fromParents[k] = par.getParentMessage(x)[parIdx];
            }
            double prod = term1;
            for (int k = 0; k < fromParents.length; k++) {
              prod = prod * fromParents[k];
            }
            lines[j] = prod;
          }
          double sum = 0;
          for (int j = 0; j < lines.length; j++) {
            sum = sum + lines[j];
          }
          x.setParentValue(i, sum);
          x.setInferredBelief(i, activatedStates, x.getChildValue(i) * x.getParentValue(i));
        }
        double delta = x.normalizeInferredBelief(activatedStates);
        if (delta < STABLE_THRESHOLD) {
          stable.add(x);
          Support.important("Reached stable belief for node " + x
              + ". Adding it to the list of stable nodes.");
        }
      }

      // If any of X's children are instantiated, pass on all child messages.
      boolean ok = true;
      for (int i = 0; i < x.getStateCount(); i++) {
        ok = ok && x.getChildValue(i) == 1;
      }
      if (!ok) {
        for (Node w : x.parents) {
          // avoid observed/stable nodes, like in other spots.
          if (w != z && (activatedNodes.contains(w) || stable.contains(w)) == false) {
            sendChildMessage(x, w, hopCount + 1);
          }
        }
      }

    }
    Support.where("<< END     Send Parent Message: " + z + " --> " + x + " " + exitMessage);
  }

  private void sendChildMessage(Node y, Node x, int hopCount) {
    Support.where(">> BEGIN   Send Child Message:  " + y + " --> " + x + " hop count: " + hopCount);
    String exitMessage = " (" + hopCount + " / " + HOP_LIMIT + ")";
    if (hopCount >= HOP_LIMIT) {
      exitMessage = " (maximum hop count reached. bailing!)";
    } else {
      for (int ix = 0; ix < x.getStateCount(); ix++) {
        SlotKey[][] combos = y.cpt.getParentIndexCombinations(x.slotKey(ix));
        double collection = 0;
        for (int iy = 0; iy < y.getStateCount(); iy++) {
          double sum = 0;
          double prob = 0;
          double par;
          for (SlotKey[] combo : combos) {
            prob = y.getProbabilityVector(combo)[iy];
            par = 1;
            for (SlotKey sk : combo) {
              if (!sk.node.equals(x)) {
                par = par * sk.node.getParentMessage(y)[sk.index];
              }
            }
            Support.out(Debug.num(prob) + " * " + Debug.num(par));
            sum = sum + (prob * par);
          }
          collection = collection + (sum * y.getChildValue(iy));
        }
        x.setChildMessage(y, ix, collection);

        double childProduct = 1;
        for (Node child : x.children) {
          childProduct = childProduct * x.getChildMessage(child)[ix];
        }
        x.setChildValue(ix, childProduct);
        double newBelief = x.getChildValue(ix) * x.getParentValue(ix);
        x.setInferredBelief(ix, activatedStates, newBelief);
      }
      double delta = x.normalizeInferredBelief(activatedStates);
      Support.important("BEL: " + x.name + ": " + Debug.num(x.getInferredBelief(activatedStates)));
      if (delta < STABLE_THRESHOLD) {
        stable.add(x);
        Support.important("Reached stable belief for node " + x
            + ". Adding it to the list of stable nodes.");
      }
      for (Node z : x.parents) {
        if ((activatedNodes.contains(z) || stable.contains(z)) == false) {
          sendChildMessage(x, z, hopCount + 1);
        }
      }
      for (Node w : x.children) {
        if (w != y) {
          sendParentMessage(x, w, hopCount + 1);
        }
      }
    }
    Support.where("<< END     Send Child Message:  " + y + " --> " + x + " " + exitMessage);
  }

  public Set<Node> getRoots(List<Node> all) {
    Set<Node> ret = new HashSet<Node>();
    for (Node n : all) {
      if (n.parents.size() == 0) {
        ret.add(n);
      }
    }
    return ret;
  }
}
