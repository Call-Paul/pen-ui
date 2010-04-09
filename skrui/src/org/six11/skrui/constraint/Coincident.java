package org.six11.skrui.constraint;

import org.six11.skrui.script.Neanderthal;
import org.six11.skrui.shape.Primitive;
import org.six11.skrui.script.Neanderthal.Certainty;
import org.six11.util.Debug;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Coincident extends Constraint {

  /**
   * Make a constraint that determines if two line segments 
   * @param name
   * @param data
   * @param slotA
   * @param slotB
   */
  public Coincident(String name, Neanderthal data, String slotA, String slotB) {
    super(name, data, slotA, slotB);
  }

  @Override
  public Certainty check(Primitive[] p) {
    String subA = Constraint.subshape(getSlotNames().get(0));
    String subB = Constraint.subshape(getSlotNames().get(1));
    Primitive primA = p[0];
    Primitive primB = p[1];

    Certainty certainty;
    certainty = checkAdjacent(primA, subA, primB, subB);
    if (!ok(certainty) && primB.isFlippable()) {
      primB.flipSubshapeBinding();
      certainty = checkAdjacent(primA, subA, primB, subB);
    }
    if (!ok(certainty) && primA.isFlippable()) {
      primA.flipSubshapeBinding();
      certainty = checkAdjacent(primA, subA, primB, subB);
    }
    if (!ok(certainty) && primB.isFlippable()) {
      primB.flipSubshapeBinding();
      certainty = checkAdjacent(primA, subA, primB, subB);
    }
    if (ok(certainty)) {
      primA.setSubshapeBindingFixed(true);
      primB.setSubshapeBindingFixed(true);
    }
    return certainty;
  }

  @SuppressWarnings("unused")
  private static void bug(String what) {
    Debug.out("Coincident", what);
  }

  private Certainty checkAdjacent(Primitive lineA, String subslotA, Primitive lineB,
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
    return "Coinc";
  }

}
