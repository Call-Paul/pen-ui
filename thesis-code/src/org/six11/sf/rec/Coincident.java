package org.six11.sf.rec;

import org.six11.sf.rec.RecognizerPrimitive.Certainty;
import org.six11.util.pen.Pt;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

public class Coincident extends RecognizerConstraint {

  public Coincident(String name, String... sNames) {
    super(name, sNames);
  }

  public Certainty check(RecognizerPrimitive... p) {
    String subA = RecognizerConstraint.subshape(getSlotNames().get(0));
    String subB = RecognizerConstraint.subshape(getSlotNames().get(1));
    RecognizerPrimitive primA = p[0];
    RecognizerPrimitive primB = p[1];
    
    Certainty certainty = Certainty.Unknown;
    certainty = checkAdjacent(primA, subA, primB, subB);
    if (ok(certainty)) {
      say("Flipped nothing. (case 1)");
    }
    if (!ok(certainty) && primB.isFlippable()) {
      primB.flipSubshapeBinding();
      certainty = checkAdjacent(primA, subA, primB, subB);
      if (ok(certainty)) {
        say("Flipped " + primB + "! (case 2)");
      }
    }
    if (!ok(certainty) && primA.isFlippable()) {
      primA.flipSubshapeBinding();
      certainty = checkAdjacent(primA, subA, primB, subB);
      if (ok(certainty)) {
        say("Flipped " + primA + "! (case 3)");
      }
    }
    if (!ok(certainty) && primB.isFlippable()) {
      primB.flipSubshapeBinding();
      certainty = checkAdjacent(primA, subA, primB, subB);
      if (ok(certainty)) {
        say("Flipped " + primB + "! (case 4)");
      }
    }
    if (ok(certainty)) {
      say("Fixing " + primA + " and " + primB);
      primA.setSubshapeBindingFixed(true);
      primB.setSubshapeBindingFixed(true);
    }
    say(p, new String[0], new double[0], certainty);
    return certainty;
  }

  private Certainty checkAdjacent(RecognizerPrimitive lineA, String subslotA, RecognizerPrimitive lineB, String subslotB) {
    Pt one = lineA.getSubshape(subslotA);
    Pt two = lineB.getSubshape(subslotB);
    double dist = one.distance(two);
    Certainty ret = Certainty.No;
    if (dist < 30) {
      ret = Certainty.Maybe;
    }
    if (dist < 20) {
      ret = Certainty.Yes;
    }
    return ret;
  }

}
