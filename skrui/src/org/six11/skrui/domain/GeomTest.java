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

  private static final int OBS_YES = 0;
  private static final int OBS_MAYBE = 1;
  private static final int OBS_NO = 2;

  public static void main(String[] args) {

    Debug.setDecimalOutputFormat("0.000");
    Support.showOutput = false;
    Support.showLocation = false;
    Support.showImportant = false;
    Debug.useColor = false;
    Debug.useTime = false;
    double[] lineTrueProb = new double[] {
        0.98, 0.01999999, 0.0000001
    };
    double[] lineFalseProb = new double[] {
        0.122, 0.258, 0.620
    };

    double[] circuit3TrueProb = new double[] {
        0.94, 0.03, 0.03
    };

    double[] circuit3FalseProb = new double[] {
        0.1644, 0.2637, 0.5719
    };

    double[] perpTrueProb = new double[] {
        0.8917, 0.1083, 0
    };

    double[] perpFalseProb = new double[] {
        0.0923, 0.0462, 0.8615
    };

    long start = System.currentTimeMillis();
    BayesianNetwork net = new BayesianNetwork();

    // first make the hypothesis nodes and add them to the network.
    HypothesisNode triangle = new HypothesisNode("Triangle", 0.1);
    HypothesisNode rectangle = new HypothesisNode("Rectangle", 0.3);
    HypothesisNode lineA = new HypothesisNode("Line A", 0.4);
    HypothesisNode lineB = new HypothesisNode("Line B", 0.4);
    HypothesisNode lineC = new HypothesisNode("Line C", 0.4);
    HypothesisNode lineD = new HypothesisNode("Line D", 0.4);
    net.addNode(triangle);
    net.addNode(rectangle);
    net.addNode(lineA);
    net.addNode(lineB);
    net.addNode(lineC);
    net.addNode(lineD);

    // Constraints are just hypotheses.
    HypothesisNode threeLegCircuit = new HypothesisNode("3-Circuit", 0.05);
    HypothesisNode perpendicularLines = new HypothesisNode("Perp", 0.43);
    net.addNode(threeLegCircuit);
    net.addNode(perpendicularLines);

    // now connect the hypothesis nodes together as either subshapes or constraints.
    rectangle.addSubshape(lineA);
    rectangle.addSubshape(lineB);
    rectangle.addSubshape(lineC);
    rectangle.addSubshape(lineD);
    rectangle.addConstraint(perpendicularLines);
    
    triangle.addSubshape(lineA);
    triangle.addSubshape(lineB);
    triangle.addSubshape(lineC);
    triangle.addConstraint(threeLegCircuit);


    ObservationNode obsA = new ObservationNode(lineA);
    ObservationNode obsB = new ObservationNode(lineB);
    ObservationNode obsC = new ObservationNode(lineC);
    ObservationNode obsD = new ObservationNode(lineD);
    ObservationNode obs3Circuit = new ObservationNode(triangle);
    ObservationNode obsPerp = new ObservationNode(perpendicularLines);
    obsA.setSlotProbabilities(copy(lineTrueProb), copy(lineFalseProb));
    obsB.setSlotProbabilities(copy(lineTrueProb), copy(lineFalseProb));
    obsC.setSlotProbabilities(copy(lineTrueProb), copy(lineFalseProb));
    obsD.setSlotProbabilities(copy(lineTrueProb), copy(lineFalseProb));
    obs3Circuit.setSlotProbabilities(copy(circuit3TrueProb), copy(circuit3FalseProb));
    obsPerp.setSlotProbabilities(copy(perpTrueProb), copy(perpFalseProb));
    net.addNode(obsA);
    net.addNode(obsB);
    net.addNode(obsC);
    net.addNode(obsD);
    net.addNode(obs3Circuit);
    net.addNode(obsPerp);
    net.initializeNetwork();

    // Support.mondoDebug(net, false); // when doing mondoDebug, never reset the variables if you've
    // // already initialized the network.

    // Observe the triangle as true.
    // net.updateTree(triangle.slotKey(YES_INDEX));

    // Observe the triangle as true.
    // net.updateTree(rectangle.slotKey(YES_INDEX));

    // Observe lines as true.
    net.updateTree(obsA.slotKey(OBS_YES));
    net.updateTree(obsB.slotKey(OBS_YES));
    net.updateTree(obsC.slotKey(OBS_YES));
    net.updateTree(obsD.slotKey(OBS_MAYBE));
    net.updateTree(obs3Circuit.slotKey(OBS_YES));
    net.updateTree(obsPerp.slotKey(OBS_MAYBE));
    System.out.println("~");
    Support.mondoDebug(net, false);

    bug("Triangle: " + Debug.num(triangle.getInferredBelief(net.getActivatedStates())[YES_INDEX]));
    bug("Rectangle: " + Debug.num(rectangle.getInferredBelief(net.getActivatedStates())[YES_INDEX]));
    long end = System.currentTimeMillis();
    bug("Elapsed time: " + (end - start) + " ms");
  }

  private static double[] copy(double[] in) {
    double[] ret = new double[in.length];
    System.arraycopy(in, 0, ret, 0, in.length);
    return ret;
  }

  private static void bug(String what) {
    Debug.out("GeomTest", what);
  }

  static class ObservationNode extends Node {

    HypothesisNode mom;

    public ObservationNode(HypothesisNode mom) {
      super("Obs[" + mom.getName() + "]", new String[] {
          "Yes", "Maybe", "No"
      });
      this.mom = mom;
      Node.link(mom, this);
    }

    void setSlotProbabilities(double[] parentTrueDist, double[] parentFalseDist) {
      bug("mom's slot keys: ");
      bug("parentTrueDist: " + Debug.num(parentTrueDist));
      bug("parentFalseDist: " + Debug.num(parentFalseDist));
      cpt.setRow(parentTrueDist, mom.slotKey(0));
      cpt.setRow(parentFalseDist, mom.slotKey(1));
    }
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

    public void addConstraint(HypothesisNode constraint) {
      children.add(0, constraint);
      constraint.addParent(this); // do constraints ever have more than one parent?
      constraint.accommodate(this);
    }

    public void addSubshape(HypothesisNode kid) {
      children.add(0, kid);
      kid.addParent(this);
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
