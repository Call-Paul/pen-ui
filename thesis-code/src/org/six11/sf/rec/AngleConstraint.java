package org.six11.sf.rec;

import static java.lang.Math.abs;

import org.six11.sf.rec.RecognizerPrimitive.Certainty;
import org.six11.util.math.Interval;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;

public class AngleConstraint extends RecognizerConstraint {

  private Interval yesRange, maybeRange;
  
  public AngleConstraint(String name, Interval yesRange, Interval maybeRange, String... sNames) {
    super(name, sNames);
    this.yesRange = yesRange;
    this.maybeRange = maybeRange;
  }

  @Override
  public Certainty check(RecognizerPrimitive... p) {
    RecognizerPrimitive primA = p[0];
    RecognizerPrimitive primB = p[1];
    Vec[] vecs = fillVectors(primA, primB);
    double angle = abs(Functions.getSignedAngleBetween(vecs[0], vecs[1]));
    Certainty ret = Certainty.No;
    if (yesRange.contains(angle)) {
      ret = Certainty.Yes;
    } else if (maybeRange.contains(angle)) {
      ret = Certainty.Maybe;
    }
    say(p, new String[] {
      "angle"
    }, new double[] {
      Math.toDegrees(angle)
    }, ret);
    return ret;
  }

  private Vec[] fillVectors(RecognizerPrimitive primA, RecognizerPrimitive primB) {
    Pt a1 = primA.getP1();
    Pt a2 = primA.getP2();
    Pt b1 = primB.getP1();
    Pt b2 = primB.getP2();
    Pt closeA = a1;
    Pt closeB = b1;
    double closest = a1.distance(b1);
    if (closest > a1.distance(b2)) {
      closeA = a1;
      closeB = b2;
      closest = a1.distance(b2);
    }
    if (closest > a2.distance(b1)) {
      closeA = a2;
      closeB = b1;
      closest = a2.distance(b1);
    }
    if (closest > a2.distance(b2)) {
      closeA = a2;
      closeB = b2;
    }
    Pt farA = (a1 == closeA) ? a2 : a1;
    Pt farB = (b1 == closeB) ? b2 : b1;
    return new Vec[] {
        new Vec(closeA, farA), new Vec(closeB, farB)
    };
  }
}
