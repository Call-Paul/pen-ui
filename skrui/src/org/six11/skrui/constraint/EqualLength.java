package org.six11.skrui.constraint;

import org.six11.skrui.script.Neanderthal;
import org.six11.skrui.shape.Primitive;
import org.six11.skrui.script.Neanderthal.Certainty;
import org.six11.util.Debug;

public class EqualLength extends Constraint {

  public EqualLength(String name, Neanderthal data, String slotA, String slotB) {
    super(name, data, slotA, slotB);
  }

  @Override
  public Certainty check(Primitive[] p) {
    Certainty ret = Certainty.No;
    Primitive primA = p[0];
    Primitive primB = p[1];
    double a = primA.getLength();
    double b = primB.getLength();
    double diff = Math.abs(a - b);
    double ratio = (Math.min(a, b) / Math.max(a,b));
    if (ratio > 0.85 || diff < 20) {
      ret = Certainty.Yes;
    } else if (ratio > 0.7 || diff < 30) {
      ret = Certainty.Maybe;
    }
    return ret;
  }

  @SuppressWarnings("unused")
  private static void bug(String what) {
    Debug.out("EqualLength", what);
  }
  
  @Override
  public String getShortStr() {
    return null;
  }

}
