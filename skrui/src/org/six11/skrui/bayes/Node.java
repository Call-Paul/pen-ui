package org.six11.skrui.bayes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.six11.util.Debug;
import org.six11.util.adt.SetMap;

/**
 * A node in a bayesian network. Note that I am not using the 'factor graph' representation that so
 * many other people use. So each node represents a random variable.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Node {

  private final static String LAMBDA = "lambda"; // stupid encoding problems prevent me from
  private final static String PI = "pi"; // using the actual unicode escape sequences.

  protected String name;
  protected List<Node> parents;
  protected List<Node> children;
  protected ConditionalProbabilityTable cpt;

  double[] childValues; // lambda values
  double[] parentValues; // pi values
  Map<Node, double[]> childMessages; // message destined for my parent. strange naming convention?
  Map<Node, double[]> parentMessages; // messages for my children.
  String[] stateNames;
  SetMap<SlotKey, double[]> beliefs;

  // belief history is a memory of recent belief vectors, given some set of slotkeys. This is always
  // pruned so its size is equal to or less than the 'numRoundsStable' value.
  private SetMap<SlotKey, Stack<double[]>> beliefHistory;

  // This is the number of rounds that we must have before we will compute the change in beliefs
  // (see beliefHistory).
  private int numRoundsStable = 2;

  /**
   * Make a node for a bayesian network
   * 
   * @param name
   *          The name for the random variable.
   * @param states
   *          The name of each state of the random variable. These states are later accessable with
   *          the slotKey(int) method.
   */
  public Node(String name, String... states) {
    this.name = name;
    parents = new ArrayList<Node>();
    children = new ArrayList<Node>();
    stateNames = states;
    cpt = new ConditionalProbabilityTable(name, this);
    cpt.setRandomData();
    beliefs = new SetMap<SlotKey, double[]>();
    beliefHistory = new SetMap<SlotKey, Stack<double[]>>();
  }

  public String getName() {
    return name;
  }
  
  public void addParent(Node n) {
    parents.add(0, n);
  }

  /**
   * Called by the bayesian network when initializing. (Or, if you want debugging info, you can call
   * this directly, but it nukes all information from previous runs, and you have to re-initialize
   * your network if you do.)
   */
  void initializePropagationSlots() {
    childValues = new double[getStateCount()];
    parentValues = new double[getStateCount()];
    childMessages = new HashMap<Node, double[]>();
    for (Node child : children) {
      childMessages.put(child, new double[getStateCount()]);
    }
    parentMessages = new HashMap<Node, double[]>();
    for (Node child : children) {
      parentMessages.put(child, new double[getStateCount()]);
    }
  }

  /**
   * Gives all the state names (as provided to the constructor). The order is maintained.
   */
  public String[] getStateNames() {
    return cpt.getNames();
  }

  /**
   * Tells you how many states this random variable has. E.g., for a binary RV this returns 2.
   */
  public int getStateCount() {
    return cpt.getNumValues();
  }

  /**
   * Sets the child (lambda) value to the given.
   */
  void setChildValue(int which, double value) {
    Support.out(LAMBDA + "(" + cpt.getStateName(which) + ") = " + Debug.num(value));
    childValues[which] = value;
  }

  /**
   * Returns the child value in the given slot.
   */
  double getChildValue(int which) {
    return childValues[which];
  }

  /**
   * Like setChildValue but for parent (often denoted as 'pi' for some reason).
   */
  void setParentValue(int which, double value) {
    Support.out(PI + "(" + cpt.getStateName(which) + ") = " + Debug.num(value));
    parentValues[which] = value;
  }

  /**
   * Like getChildValue but for parent.
   */
  double getParentValue(int which) {
    return parentValues[which];
  }

  /**
   * Establishes a parent/child relationship. This adds the child to the parent's kid list, and the
   * parent the the child's parent list. It also adds the parent to the child's conditional
   * probability table.
   */
  public static void link(Node parent, Node child) {
    parent.children.add(0, child);
    child.parents.add(0, parent);
    child.cpt.addVariable(parent.cpt.getMainVariable());
  }

  /**
   * Just report this node's name.
   */
  public String toString() {
    return name;
  }

  void setChildMessage(Node n, int stateIdx, double value) {
    Support.out(LAMBDA + "_" + n.name + "(" + cpt.getStateName(stateIdx) + ") = "
        + Debug.num(value));
    if (childMessages.get(n) == null) {
      warn("childMessages.get(" + n + ") is null.");
    }
    childMessages.get(n)[stateIdx] = value;
  }

  private void warn(String what) {
    Debug.out("Node", " ** WARNING ** " + what);
  }

  double[] getChildMessage(Node n) {
    return childMessages.get(n);
  }

  void setParentMessage(Node n, int stateIdx, double value) {
    parentMessages.get(n)[stateIdx] = value;
    Support.out(PI + "_" + n.name + "(" + cpt.getStateName(stateIdx) + ") = " + Debug.num(value));
  }

  double[] getParentMessage(Node n) {
    return parentMessages.get(n);
  }

  double[] getProbabilityVector(SlotKey... slotKeys) {
    return cpt.getData(slotKeys);
  }

  /**
   * Returns a key for the given slot. These are used to form sets of related states, such as the
   * set of active (observed) states.
   */
  public SlotKey slotKey(int i) {
    return new SlotKey(this, i);
  }

  /**
   * Sets the probability values for the given parent states. This does not ensure the input is
   * normalized. (Use Support.normalize(double[]) to do that.)
   */
  public void setProbabilityDistribution(double[] ds, SlotKey... slotKeys) {
    cpt.setRow(ds, slotKeys);
  }

  /**
   * Set the inferred belief to the given vector of values and return the change between the last
   * 'numRoundsStable' times, or +Inf if there isn't enough history built up. This is useful if you
   * would like to avoid infinite loops, or if you would like to terminate a loopy search when
   * belief values stabilize.
   * 
   * Note the input vector must be normalized before you use this method.
   */
  double setInferredBelief(Set<SlotKey> slots, double[] values) {
    double ret = Double.POSITIVE_INFINITY; // indcates no prior belief
    Stack<double[]> prior = beliefHistory.get(slots);
    if (!beliefs.containsKey(slots)) {
      beliefs.put(slots, new double[getStateCount()]);
    }
    Support.out("Setting inferred belief vector for " + name + ": ");
    for (int i = 0; i < getStateCount(); i++) {
      Support.out("  P(" + slotKey(i) + " | " + Debug.num(slots, ", ") + ") = "
          + Debug.num(values[i]));
    }
    beliefs.put(slots, values);
    if (prior != null && prior.size() >= numRoundsStable) {
      Support.out("beliefHistory: Sufficient history built for " + name);
      double sum = 0;
      for (int i = 0; i < prior.size(); i++) {
        double[] priorVec = prior.get(i);
        for (int j = 0; j < priorVec.length; j++) {
          sum += Math.abs(priorVec[j] - values[j]);
        }
      }
      ret = sum;
      Support.out("beliefHistory: total delta in last " + numRoundsStable + " rounds = " + ret);
    }
    if (prior == null) {
      prior = new Stack<double[]>();
      beliefHistory.put(slots, prior);
      Support.out("beliefHistory: created history for " + name);
    }
    // store a COPY of the incoming values, because the algo uses them to store scratch values too
    double[] copyOfValues = new double[values.length];
    System.arraycopy(values, 0, copyOfValues, 0, values.length);
    prior.push(copyOfValues);
    if (prior.size() > numRoundsStable) {
      prior.remove(0);
    }
    Support.out("beliefHistory: I have a history of " + prior.size() + " for " + name);
    Support.out("Change since last time for " + name + ": " + Debug.num(ret));
    return ret;
  }

  void setInferredBelief(int slot, Set<SlotKey> slots, double value) {
    getInferredBelief(slots)[slot] = value;
    String msg = "";
    if (value > 1) {
      msg = ". It is OK that this is greater than one. The algo will normalize it eventually.";
    }
    Support.out("P(" + cpt.getStateName(slot) + " | " + Debug.num(slots, ", ") + ") = " + value
        + msg);
  }

  /**
   * This tells you what the node believes (if anything) when it has seen some evidence. This will
   * return an array of zeros if it has no clue.
   */
  public double[] getInferredBelief(Set<SlotKey> slots) {
    if (!beliefs.containsKey(slots)) {
      beliefs.put(slots, new double[getStateCount()]);
    }
    if (!beliefs.containsKey(slots)) {
      beliefs.put(slots, new double[getStateCount()]);
    }
    return beliefs.get(slots);
  }

  /**
   * Normalize the current values of the inferred belief (using the activated states as the key).
   * The propagation algorithm stores temporary values in the same place it stores the 'final'
   * version. The temporary values are all proportional to one another, but they most likely don't
   * sum to one. So you have to normalize them so they sum to one. Their proportions are maintained.
   * 
   * The return value is the magnitude of the change between the previously held belief and this one
   * (or +Inf if there was no last time). You may use this to avoid infinite loops during loopy
   * propagation.
   */
  double normalizeInferredBelief(Set<SlotKey> activatedStates) {
    Support.out("Normalizing P(" + name + " | " + Debug.num(activatedStates, ", ") + ")...");
    double[] currentValues = getInferredBelief(activatedStates);
    double[] normValues = Support.normalize(currentValues);
    return setInferredBelief(activatedStates, normValues);
  }

}
