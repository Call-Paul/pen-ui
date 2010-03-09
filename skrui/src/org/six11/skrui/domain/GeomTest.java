package org.six11.skrui.domain;

import org.six11.skrui.bayes.BayesianNetwork;
import org.six11.skrui.bayes.Node;
import org.six11.skrui.bayes.SlotKey;
import org.six11.skrui.bayes.Support;
import org.six11.util.Debug;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class GeomTest {

  private static final int YES_INDEX = 0;
  private static final int NO_INDEX = 1;

  public static void main(String[] args) {
    
    Debug.setDecimalOutputFormat("0.000");
    Support.showOutput = false;
    Support.showLocation = false;
    Support.showImportant = false;
    Debug.useColor = false;
    Debug.useTime = false;
    long start = System.currentTimeMillis();
    BayesianNetwork net = new BayesianNetwork();
    HypothesisNode triangle = new HypothesisNode("Triangle", 0.1);
    HypothesisNode rectangle = new HypothesisNode("Rectangle", 0.3);
    HypothesisNode line1 = new HypothesisNode("Line A", 0.4);
    HypothesisNode line2 = new HypothesisNode("Line B", 0.4);
    HypothesisNode line3 = new HypothesisNode("Line C", 0.4);
    rectangle.addSubshape(line1);
    rectangle.addSubshape(line2);
    rectangle.addSubshape(line3);
    triangle.addSubshape(line1);
    triangle.addSubshape(line2);
    triangle.addSubshape(line3);
    net.addNode(triangle);
    net.addNode(rectangle);
    net.addNode(line1);
    net.addNode(line2);
    net.addNode(line3);
    net.initializeNetwork();

//    Support.mondoDebug(net, false); // when doing mondoDebug, never reset the variables if you've
//                                    // already initialized the network.
    
    // Observe the triangle as true. 
    // net.updateTree(triangle.slotKey(YES_INDEX));

    // Observe the triangle as true. 
    // net.updateTree(rectangle.slotKey(YES_INDEX));

    // Observe a line as true.
    net.updateTree(line1.slotKey(YES_INDEX));
    // Observe a line as true.
    net.updateTree(line2.slotKey(YES_INDEX));
    // Observe a line as true.
    net.updateTree(line3.slotKey(YES_INDEX));

    System.out.println("~");
//    Support.mondoDebug(net, false);

    bug("Triangle: " + Debug.num(triangle.getInferredBelief(net.getActivatedStates())[YES_INDEX]));
    bug("Rectangle: " + Debug.num(rectangle.getInferredBelief(net.getActivatedStates())[YES_INDEX]));
    long end = System.currentTimeMillis();
    bug("Elapsed time: " + (end - start) + " ms");
  }

  private static void bug(String what) {
    Debug.out("GeomTest", what);
  }

  static class HypothesisNode extends Node {

    double yesProbability;
    String roleName;

    public HypothesisNode(String name, double yesProbability) {
      super(name, new String[] {
          name + "=true", name + "=false"
      });
      this.yesProbability = yesProbability;
      cpt.setRow(new double[] {
          yesProbability, 1 - yesProbability
      });
    }

    public void addSubshape(HypothesisNode kid) {
      children.add(0, kid);
      kid.parents.add(0, this);
      kid.accommodate(this);
    }

    private void accommodate(HypothesisNode mom) {
      cpt.addVariable(mom);
      SlotKey[][] combos = cpt.getParentIndexCombinations();
      for (SlotKey[] combo : combos) {
        boolean yes = false;
        for (SlotKey key : combo) {
          if (key.getIndex() == YES_INDEX) {
            yes = true;
          }
        }
        // where one of the parents is true, the child node is also true
        if (yes) {
          cpt.setRow(new double[] {
              1, 0
          }, combo);
        } else {
          // Otherwise, the probability is simply the one provided.
          cpt.setRow(new double[] {
              yesProbability, 1 - yesProbability
          }, combo);
        }
      }
    }

  }

}
