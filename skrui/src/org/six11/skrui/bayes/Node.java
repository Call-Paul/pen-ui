package org.six11.skrui.bayes;

import java.util.ArrayList;
import java.util.List;

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
  
  
  public Node(String name, String... states) {
    this.name = name;
    parents = new ArrayList<Node>();
    children = new ArrayList<Node>();
    cpt = new ConditionalProbabilityTable(name, states);
    cpt.setRandomData();
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

}
