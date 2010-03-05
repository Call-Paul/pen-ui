package org.six11.skrui.bayes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.six11.util.Debug;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Node {

  String name;
  List<Node> parents;
  List<Node> children;
  ConditionalProbabilityTable cpt;
  double[] childValues;
  Map<Node, double[]> childMessages;
  Map<Node, double[]> parentMessages;

  public Node(String name, String... states) {
    this.name = name;
    parents = new ArrayList<Node>();
    children = new ArrayList<Node>();
    cpt = new ConditionalProbabilityTable(name, states);
    cpt.setRandomData();
  }

  public void initializePropagationSlots() {
    childValues = new double[getStateCount()];

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
    childValues[which] = value;
  }

  private static void bug(String what) {
    Debug.out("Node", what);
  }

  public static void link(Node parent, Node child) {
    parent.children.add(child);
    child.parents.add(parent);
    child.cpt.addVariable(parent.cpt.getNames());
  }

  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(name + ":\n");
    return buf.toString();
  }

  private int getChildIndex(Node n) {
    if (!children.contains(n)) {
      bug("Warning: you are asking for the child index for node " + n + " but I don't have it.");
    }
    return children.indexOf(n);
  }

  /**
   * n is a child, stateIdx is the index of one of MY states (not the child states).
   */
  public void setChildMessage(Node n, int stateIdx, double value) {
    childMessages.get(n)[stateIdx] = value;
  }

  /**
   * n is a child, stateIdx is the index of one of MY states (not the child states).
   */
  public void setParentMessage(Node n, int stateIdx, double value) {
    parentMessages.get(n)[stateIdx] = value;
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
      for (int i=0; i < msgs.length; i++) {
        buf.append(getStateNames()[i] + "=" + msgs[i] + " ");
      }
      buf.append("\n");
    }

    buf.append("  parentMessages:\n");
    for (Node k : parentMessages.keySet()) {
      buf.append("    " + k.name + ": ");
      double[] msgs = parentMessages.get(k);
      for (int i=0; i < msgs.length; i++) {
        buf.append(getStateNames()[i] + "=" + msgs[i] + " ");
      }
      buf.append("\n");
    }

    buf.append("  child message values:\n");
    for (int i = 0; i < childValues.length; i++) {
      buf.append("    " + getStateNames()[i] + ": " + Debug.num(childValues[i]) + "\n");
    }

    buf.append("  conditional probability table:\n");
    buf.append(cpt.getFormattedTable("    "));
    buf.append("------------------------------------------------------------------------\n\n");
    
    System.out.println(buf.toString());
  }

}
