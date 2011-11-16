package org.six11.sf.rec;

import org.six11.sf.rec.RecognizerPrimitive.Certainty;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Vec;
import static java.lang.Math.abs;

public class AcuteAngle extends RecognizerConstraint {

  public AcuteAngle(String name, String... sNames) {
    super(name, sNames);
  }

  @Override
  public Certainty check(RecognizerPrimitive... p) {
    RecognizerPrimitive primA = p[0];
    RecognizerPrimitive primB = p[1];
    Vec vA = new Vec(primA.getP1(), primA.getP2()).getUnitVector();
    Vec vB = new Vec(primB.getP1(), primB.getP2()).getUnitVector();
    double angle = abs(Functions.getAngleBetween(vA, vB));
    Certainty ret = Certainty.No;
    if (angle < 1.4) {
      ret = Certainty.Yes;
    } else if (angle < 1.66) {
      ret = Certainty.Maybe;
    }
    return ret;
  }

}
