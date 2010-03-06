package org.six11.skrui.bayes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.six11.util.Debug;

/**
 * I manually decrypted the singly-connected belief propagation algorithm found in
 * "Learning Bayesian Networks" by Richard Neapolitan and have attempted to reconstruct it in Java.
 * It probably contains gigantic errors and oversights.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class BayesianNetwork {

  static Random rand = new Random(System.currentTimeMillis());

  public static void main(String[] args) {
    Debug.useColor = false;
    Debug.useTime = false;
    Debug.setDecimalOutputFormat("0.000");
    // BayesianNetwork net = BayesianNetwork.makeSimpleNetwork();
    BayesianNetwork net = BayesianNetwork.makeNeapolitanExample310();
    // net.mondoDebug(true);
    net.initializeNetwork();
  }

  private void mondoDebug(boolean initializeNodes) {
    if (initializeNodes) {
      for (Node n : nodes) {
        n.initializePropagationSlots();
      }
    }
    for (Node n : nodes) {
      n.mondoDebug();
    }
  }

  private static void bug(String what) {
    Debug.out("BayesianNetwork", what);
  }

  public static double[] makeRandomDistribution(int slots) {
    double[] ret = new double[slots];
    for (int i = 0; i < ret.length; i++) {
      ret[i] = rand.nextDouble();
    }
    return normalize(ret);
  }

  public static double[] normalize(double[] in) {
    double sum = 0;
    for (int i = 0; i < in.length; i++) {
      sum = sum + in[i];
    }
    double z = 1.0 / sum;
    double[] ret = new double[in.length];
    for (int i = 0; i < in.length; i++) {
      ret[i] = in[i] * z;
    }
    return ret;
  }

  List<Node> nodes;
  List<Node> activatedNodes;
//  Map<Node, Integer> activatedStates;
  List<SlotKey> activatedStates;

  public BayesianNetwork() {
    nodes = new ArrayList<Node>();
    activatedNodes = new ArrayList<Node>();
    activatedStates = new ArrayList<SlotKey>();
  }

  public void initializeNetwork() {
    System.out.println("Clearing activated nodes/states.");
    activatedNodes.clear();
    activatedStates.clear();
    
    System.out.println("Initializing propagation slots.");
    for (Node n : nodes) {
      n.initializePropagationSlots();
    }
    
    System.out.println("Initializing child val/msg and parent messages.");
    for (Node n : nodes) {
      for (int i = 0; i < n.getStateCount(); i++) {
        n.setChildValue(i, 1); // Compute lambda values
      }
      for (Node z : n.parents) {
        for (int i = 0; i < z.getStateCount(); i++) {
          z.setChildMessage(n, i, 1); // Compute lambda messages
        }
      }
      for (Node y : n.children) {
        for (int i = 0; i < n.getStateCount(); i++) {
          n.setParentMessage(y, i, 1); // Initialize pi messages
        }
      }
    }
    
    Set<Node> roots = getRoots(nodes);
    System.out.println("Setting parent values on " + roots.size() + " roots.");
    for (Node r : roots) {
      for (int i = 0; i < r.getStateCount(); i++) {
        r.setParentValue(i, r.getProbabilityVector()[i]); // Compute R's pi values.
      }
      for (Node x : r.children) {
        sendParentMessage(r, x);
      }
    }
    mondoDebug(false);
  }

  private void sendParentMessage(Node z, Node x) {
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

    if (!activatedNodes.contains(x)) {
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
        System.out.print("pi(" + x.cpt.getStateName(i) + ") = ");
        double[] lines = new double[combos.length];
        for (int j = 0; j < combos.length; j++) {
          SlotKey[] parentValueSlots = combos[j];
          double[] myCptData = x.cpt.getData(parentValueSlots);
          double term1 = myCptData[i];
          System.out.print(" + (" + Debug.num(term1) + ") ");
          double[] fromParents = new double[x.parents.size()];
          for (int k = 0; k < x.parents.size(); k++) {
            Node par = x.parents.get(k);
            int parIdx = parentValueSlots[k].index;
            fromParents[k] = par.getParentMessage(x)[parIdx];
            System.out.print("(" + fromParents[k] + ") ");
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
        System.out.println(" = " + Debug.num(sum));
        x.setParentValue(i, sum);
        x.setInferredBelief(i, activatedStates, x.getChildValue(i) * x.getParentValue(i));
      }
      double[] norm = normalize(x.getInferredBelief(activatedStates));
      x.setInferredBelief(activatedStates, norm);
    }

  }

  private Set<Node> getRoots(List<Node> all) {
    Set<Node> ret = new HashSet<Node>();
    for (Node n : all) {
      if (n.parents.size() == 0) {
        ret.add(n);
      }
    }
    return ret;
  }

  public static BayesianNetwork makeSimpleNetwork() {
    Node clouds = new Node("Cloudy", "Sunny", "Cloudy");
    Node rain = new Node("Rain", "Yes", "No");
    Node.link(clouds, rain);
    clouds.cpt.setRow(new double[] {
        0.6, 0.4
    }); // No qualifier necessary because clouds has no parent.
    rain.cpt.setRow(new double[] {
        0.01, 0.99
    }, clouds.slotKey(0)); // Qualifier 0 refers to parent's slot-0 variable: Sunny
    rain.cpt.setRow(new double[] {
        0.3, 0.7
    }, clouds.slotKey(1)); // Qualifier 1 refers to parent's slot-1 variable: Cloudy
    BayesianNetwork net = new BayesianNetwork();
    net.addNode(clouds);
    net.addNode(rain);
    for (Node n : net.getNodes()) {
      System.out.print(n.cpt.toString());
    }
    return net;
  }

  public static BayesianNetwork makeNeapolitanExample310() {
    Node b = new Node("Burglary", "b1", "b2");
    Node f = new Node("Freight Truck", "f1", "f2");
    Node a = new Node("Alarm", "a1", "a2");
    Node.link(f, a);
    Node.link(b, a);
    b.setProbabilityDistribution(new double[] {
        0.005, 0.995
    });
    f.setProbabilityDistribution(new double[] {
        0.03, 0.97
    });

    a.setProbabilityDistribution(new double[] { // b1, f1
            0.992, 1 - 0.992
        }, b.slotKey(0), f.slotKey(0));

    a.setProbabilityDistribution(new double[] { // b1, f2
            0.99, 1 - 0.99
        }, b.slotKey(0), f.slotKey(1));

    a.setProbabilityDistribution(new double[] { // b2, f1
            0.2, 1 - 0.2
        }, b.slotKey(1), f.slotKey(0));

    a.setProbabilityDistribution(new double[] { // b2, f2
            0.003, 1 - 0.003
        }, b.slotKey(1), f.slotKey(1));

    BayesianNetwork net = new BayesianNetwork();
    net.addNode(b);
    net.addNode(f);
    net.addNode(a);
    return net;
  }

  public void addNode(Node n) {
    nodes.add(n);
  }

  public List<Node> getNodes() {
    return nodes;
  }

}
