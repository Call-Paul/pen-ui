package org.six11.skrui.constraint;

import org.six11.skrui.script.LineSegment;
import org.six11.skrui.script.Neanderthal;
import org.six11.skrui.script.Primitive;
import org.six11.skrui.script.Neanderthal.Certainty;
import org.six11.util.Debug;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Adjacent extends Constraint {

  public Adjacent(String name, Neanderthal data, String slotA, String slotB) {
    super(name, data, slotA, slotB);
  }

  @Override
  public Certainty check(Primitive[] p) {
    String subA = Constraint.subshape(getSlotNames().get(0));
    String subB = Constraint.subshape(getSlotNames().get(1));
    LineSegment lineA = (LineSegment) p[0];
    LineSegment lineB = (LineSegment) p[1];

    Certainty certainty;
    certainty = checkAdjacent(lineA, subA, lineB, subB);
    if (!ok(certainty) && lineB.isFlippable()) {
      lineB.flipSubshapeBinding();
      certainty = checkAdjacent(lineA, subA, lineB, subB);
    }
    if (!ok(certainty) && lineA.isFlippable()) {
      lineA.flipSubshapeBinding();
      certainty = checkAdjacent(lineA, subA, lineB, subB);
    }
    if (!ok(certainty) && lineB.isFlippable()) {
      lineB.flipSubshapeBinding();
      certainty = checkAdjacent(lineA, subA, lineB, subB);
    }
    if (ok(certainty)) {
      lineA.setSubshapeBindingFixed(true);
      lineB.setSubshapeBindingFixed(true);
    }
    return certainty;
  }

  private boolean ok(Certainty certainty) {
    return (certainty == Certainty.Yes || certainty == Certainty.Maybe);
  }

  private static void bug(String what) {
    Debug.out("Adjacent", what);
  }

  private Certainty checkAdjacent(LineSegment lineA, String subslotA, LineSegment lineB,
      String subslotB) {


    double dist = lineA.getSubshape(subslotA).distance(lineB.getSubshape(subslotB));

    Certainty ret = Certainty.No;
    if (dist < 30) {
      ret = Certainty.Maybe;
    }
    if (dist < 20) {
      ret = Certainty.Yes;
    }
    return ret;
  }

  @Override
  public String getShortStr() {
    return "Adj";
  }

}
