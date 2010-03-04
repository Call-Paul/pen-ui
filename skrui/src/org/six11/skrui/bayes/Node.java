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
  private ConditionalProbabilityTable cpt;

  public Node(String name) {
    this.name = name;
    parents = new ArrayList<Node>();
    children = new ArrayList<Node>();
    cpt = new ConditionalProbabilityTable("My Thing", 2, 2, 3, 4);
    cpt.setNames("q1", "q2", "a1", "a2", "b1", "b2", "b3", "c1", "c2", "c3", "c4");
    for (int q = 0; q < 2; q++) {
      for (int a = 0; a < 2; a++) {
        for (int b = 0; b < 3; b++) {
          cpt.setRow(BayesianNetwork.makeRandomDistribution(4), q, a, b);
        }
      }
    }
    bug("Here's the CPT:\n" + cpt);
  }

  private static void bug(String what) {
    Debug.out("Node", what);
  }

  public static void link(Node parent, Node child) {
    parent.children.add(child);
    child.parents.add(parent);
  }

  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(name + ":\n");
    return buf.toString();
  }

}
