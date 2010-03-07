package org.six11.skrui.bayes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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

  public void initializePropagationSlots() {
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

  public void setChildValue(int which, double value) {
    System.out.println(LAMBDA + "(" + cpt.getStateName(which) + ") = " + Debug.num(value));
    childValues[which] = value;
  }

  public double getChildValue(int which) {
    return childValues[which];
  }

  public void setParentValue(int which, double value) {
    System.out.println(PI + "(" + cpt.getStateName(which) + ") = " + Debug.num(value));
    parentValues[which] = value;
  }

  public double getParentValue(int which) {
    return parentValues[which];
  }

  private static void bug(String what) {
    Debug.out("Node", what);
  }

  private static void warn(String what) {
    Debug.out("Node", "**** " + what);
  }

  public static void link(Node parent, Node child) {
    parent.children.add(0, child);
    child.parents.add(0, parent);
    child.cpt.addVariable(parent.cpt.getMainVariable());
  }

  public String toString() {
    return name;
  }

  private int getChildIndex(Node n) {
    if (!children.contains(n)) {
      warn("Warning: you are asking for the child index for node " + n + " but I don't have it.");
    }
    return children.indexOf(n);
  }

  /**
   * n is a child, stateIdx is the index of one of MY states (not the child states).
   */
  public void setChildMessage(Node n, int stateIdx, double value) {
    System.out.println(LAMBDA + "_" + n.name + "(" + cpt.getStateName(stateIdx) + ") = "
        + Debug.num(value));
    childMessages.get(n)[stateIdx] = value;
  }

  public double[] getChildMessage(Node n) {
    return childMessages.get(n);
  }

  /**
   * n is a child, stateIdx is the index of one of MY states (not the child states).
   */
  public void setParentMessage(Node n, int stateIdx, double value) {
    parentMessages.get(n)[stateIdx] = value;
    System.out.println(PI + "_" + n.name + "(" + cpt.getStateName(stateIdx) + ") = "
        + Debug.num(value));
  }

  public double[] getParentMessage(Node n) {
    return parentMessages.get(n);
  }

  public void mondoDebug() {
    StringBuilder buf = new StringBuilder();
    buf.append("Mondo Debug for node \"" + name + "\"\n");
    buf.append("  parents: (" + parents.size() + ")\n");

    for (Node n : parents) {
      buf.append("    " + n.name + "\n");
    }

    buf.append("  children: (" + children.size() + ")\n");
    for (Node n : children) {
      buf.append("    " + n.name + "\n");
    }

    buf.append("  childMessages:\n");
    for (Node k : childMessages.keySet()) {

      buf.append("    " + k.name + ": ");
      double[] msgs = childMessages.get(k);
      for (int i = 0; i < msgs.length; i++) {
        buf.append(getStateNames()[i] + "=" + msgs[i] + " ");
      }
      buf.append("\n");
    }

    buf.append("  parentMessages:\n");
    for (Node k : parentMessages.keySet()) {
      buf.append("    " + k.name + ": ");
      double[] msgs = parentMessages.get(k);
      for (int i = 0; i < msgs.length; i++) {
        buf.append(getStateNames()[i] + "=" + msgs[i] + " ");
      }
      buf.append("\n");
    }

    buf.append("  child message values:\n");
    for (int i = 0; i < childValues.length; i++) {
      buf.append("    " + getStateNames()[i] + ": " + Debug.num(childValues[i]) + "\n");
    }

    buf.append("  informed beliefs:\n");
    for (Set<SlotKey> observed : beliefs2.keySet()) {
      if (observed.size() == 0) {
        buf.append("    ¯: " + Debug.num(beliefs2.get(observed)) + "\n");
      } else {
        buf.append("    " + Debug.num(observed, " ") + ": " + Debug.num(beliefs2.get(observed))
            + "\n");
      }
    }

    buf.append("  conditional probability table:\n");
    buf.append(cpt.getFormattedTable("    "));
    buf.append("------------------------------------------------------------------------\n\n");

    System.out.println(buf.toString());
  }

  public double[] getProbabilityVector(SlotKey... slotKeys) {
    return cpt.getData(slotKeys);
  }

  public SlotKey slotKey(int i) {
    return new SlotKey(this, i);
  }

  public void setProbabilityDistribution(double[] ds, SlotKey... slotKeys) {
    // int[] where = new int[slotKeys.length];
    // for (int i=0; i < slotKeys.length; i++) {
    // int idx = cpt.variables.indexOf(slotKeys[i].node);
    // where[idx] = slotKeys[i].index;
    // }
    cpt.setRow(ds, slotKeys);
  }

  public void setInferredBelief(Set<SlotKey> slots, double[] values) {
    if (!beliefs2.containsKey(slots)) {
      bug("(1) Node " + name + " does not currently have inferred beliefs for keys: "
          + Debug.num(slots, ", ") + ", hashCode=" + slots.hashCode() + ". It does have:");
      for (Set<SlotKey> k : beliefs2.keySet()) {
        bug("  " + Debug.num(k, ", ") + ", hashCode=" + k.hashCode() + ", equal(slots): "
            + k.equals(slots));
      }
      beliefs2.put(slots, new double[getStateCount()]);
    }
    System.out.println("Setting inferred belief vector for " + name + ": ");
    for (int i = 0; i < getStateCount(); i++) {
      System.out.println("  P(" + slotKey(i) + " | " + Debug.num(slots, ", ") + ") = "
          + Debug.num(values[i]));
    }
    beliefs2.put(slots, values);
  }

  public void setInferredBelief(int slot, Set<SlotKey> slots, double value) {
    getInferredBelief(slots)[slot] = value;
    String msg = "";
    if (value > 1) {
      msg = ". It is OK that this is greater than one. The algo will normalize it eventually.";
    }
    System.out.println("P(" + cpt.getStateName(slot) + " | " + Debug.num(slots, ", ") + ") = "
        + value + msg);
  }

  public double[] getInferredBelief(Set<SlotKey> slots) {
    if (!beliefs2.containsKey(slots)) {
      bug("(2) Node " + name + " does not currently have inferred beliefs for keys: "
          + Debug.num(slots, ", ") + ", hashCode=" + slots.hashCode() + ". It does have:");
      if (beliefs2.keySet().size() > 0) {
        for (Set<SlotKey> k : beliefs2.keySet()) {
          bug("  " + Debug.num(k, ", ") + ", hashCode=" + k.hashCode() + ", equal(slots): "
              + k.equals(slots));
        }
      } else {
        bug("(no evidence)");
      }
      beliefs2.put(slots, new double[getStateCount()]);
    }
    if (!beliefs2.containsKey(slots)) {
      beliefs2.put(slots, new double[getStateCount()]);
    }
    return beliefs2.get(slots);
  }

  public void normalizeInferredBelief(Set<SlotKey> activatedStates) {
    System.out.println("Normalizing P(" + name + " | " + Debug.num(activatedStates, ", ") + ")...");
    double[] currentValues = getInferredBelief(activatedStates);
    double[] normValues = BayesianNetwork.normalize(currentValues);
    setInferredBelief(activatedStates, normValues);
  }

}
