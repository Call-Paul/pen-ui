package org.six11.skrui.constraint;

import java.util.Set;

import org.six11.skrui.script.Neanderthal;
import org.six11.skrui.script.Primitive;
import org.six11.skrui.script.Neanderthal.Certainty;
import org.six11.util.Debug;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Perpendicular extends Constraint {

  /**
   * @param name
   * @param data
   * @param sNames
   */
  public Perpendicular(String name, Neanderthal data, String slotA, String slotB) {
    super(name, data, slotA, slotB);
  }

  @Override
  public Certainty check(Primitive[] p) {
    Primitive primA = p[0];
    Primitive primB = p[1];

    Certainty certainty;
    certainty = checkPerpendicular(primA, primB);
    return certainty;
  }

  private Certainty checkPerpendicular(Primitive primA, Primitive primB) {
    Certainty ret = Certainty.No;
    Set<Primitive> perps = data.getAngleGraph().getNear(primA.getFixedAngle() + Math.PI / 2, 0.21);
    if (perps.contains(primB)) {
      ret = Certainty.Yes;
    } else {
      perps = data.getAngleGraph().getNear(primA.getFixedAngle() + Math.PI / 2, 0.5);
      if (perps.contains(primB)) {
        ret = Certainty.Maybe;
      }
    }
    return ret;
  }

  @SuppressWarnings("unused")
  private static void bug(String what) {
    Debug.out("Perpendicular", what);
  }

  @Override
  public String getShortStr() {
    return "Perp";
  }

}
