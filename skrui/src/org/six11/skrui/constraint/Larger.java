package org.six11.skrui.constraint;

import org.six11.skrui.script.Neanderthal;
import org.six11.skrui.script.Primitive;
import org.six11.skrui.script.Neanderthal.Certainty;

/**
 * 
 *
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Larger extends Constraint {

  /**
   * @param name
   * @param data
   * @param sNames
   */
  public Larger(String name, Neanderthal data, String slotA, String slotB) {
    super(name, data, slotA, slotB);
  }

  @Override
  public Certainty check(Primitive[] p) {
    Certainty ret = Certainty.No;
    Primitive primA = p[0];
    Primitive primB = p[1];
    double a = primA.getLength();
    double b = primB.getLength();
    double ratio = b / a;
    if (ratio < 0.5) {
      ret = Certainty.Yes;
    } else if (ratio < 0.85) { // This is < 1 because the point is to find lines that
      ret = Certainty.Maybe;   // people would readily identify as larger.
    }
    return ret;
  }

  @Override
  public String getShortStr() {
    return "Lg";
  }

}
