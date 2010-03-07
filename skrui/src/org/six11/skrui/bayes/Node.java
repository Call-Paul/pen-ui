package org.six11.skrui.bayes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.six11.util.Debug;
import org.six11.util.adt.SetMap;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Node {

  private final static String LAMBDA = "´";
  private final static String PI = "\u03C0";
  String name;
  List<Node> parents;
  List<Node> children;
  ConditionalProbabilityTable cpt;
  double[] childValues; // lambda values
  double[] parentValues; // pi values
  Map<Node, double[]> childMessages;
  Map<Node, double[]> parentMessages;
  String[] stateNames;
  // Map<Set<SlotKey>, double[]> beliefs;
  SetMap<SlotKey, double[]> beliefs2;

  public Node(String name, String... states) {
    this.name = name;
    parents = new ArrayList<Node>();
    children = new ArrayList<Node>();
    stateNames = states;
    cpt = new ConditionalProbabilityTable(name, this);
    cpt.setRandomData();
    // beliefs = new HashMap<Set<SlotKey>, double[]>();
    beliefs2 = new SetMap<SlotKey, double[]>();
  }

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

  public String[] getStateNames() {
    return cpt.getNames();
  }

  public int getStateCount() {
    return cpt.getNumValues();
  }

  void setChildValue(int which, double value) {
    Support.out(LAMBDA + "(" + cpt.getStateName(which) + ") = " + Debug.num(value));
    childValues[which] = value;
  }

  double getChildValue(int which) {
    return childValues[which];
  }

  void setParentValue(int which, double value) {
    Support.out(PI + "(" + cpt.getStateName(which) + ") = " + Debug.num(value));
    parentValues[which] = value;
  }

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
    childMessages.get(n)[stateIdx] = value;
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

  SlotKey slotKey(int i) {
    return new SlotKey(this, i);
  }

  /**
   * Sets the probability values for the given parent states. This does not ensure the input is
   * normalized. (Use Support.normalize(double[]) to do that.)
   */
  public void setProbabilityDistribution(double[] ds, SlotKey... slotKeys) {
    cpt.setRow(ds, slotKeys);
  }

  void setInferredBelief(Set<SlotKey> slots, double[] values) {
    if (!beliefs2.containsKey(slots)) {
      beliefs2.put(slots, new double[getStateCount()]);
    }
    Support.out("Setting inferred belief vector for " + name + ": ");
    for (int i = 0; i < getStateCount(); i++) {
      Support.out("  P(" + slotKey(i) + " | " + Debug.num(slots, ", ") + ") = "
          + Debug.num(values[i]));
    }
    beliefs2.put(slots, values);
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
    if (!beliefs2.containsKey(slots)) {
      beliefs2.put(slots, new double[getStateCount()]);
    }
    if (!beliefs2.containsKey(slots)) {
      beliefs2.put(slots, new double[getStateCount()]);
    }
    return beliefs2.get(slots);
  }

  void normalizeInferredBelief(Set<SlotKey> activatedStates) {
    Support.out("Normalizing P(" + name + " | " + Debug.num(activatedStates, ", ") + ")...");
    double[] currentValues = getInferredBelief(activatedStates);
    double[] normValues = Support.normalize(currentValues);
    setInferredBelief(activatedStates, normValues);
  }

}
