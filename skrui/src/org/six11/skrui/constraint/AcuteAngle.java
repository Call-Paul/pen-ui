package org.six11.skrui.constraint;

import org.six11.skrui.script.Neanderthal;
import org.six11.skrui.script.Primitive;
import org.six11.skrui.script.Neanderthal.Certainty;
import org.six11.util.Debug;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class AcuteAngle extends Constraint {

  /**
   * @param name
   * @param data
   * @param sNames
   */
  public AcuteAngle(String name, Neanderthal data, String slotA, String slotB) {
    super(name, data, slotA, slotB);
  }

  @Override
  public Certainty check(Primitive[] p) {
    Primitive primA = p[0];
    Primitive primB = p[1];

    Certainty certainty;
    certainty = checkAcute(primA, primB);
    return certainty;

  }

  private Certainty checkAcute(Primitive primA, Primitive primB) {
    Certainty ret = Certainty.No;
    // This creates an intersection point between the two primitives' lines. Then make vectors from
    // that point and the more distant point along each primitive. Calculate the angle by taking the
    // arc cosine of the dot product of those vectors.
    Pt ix = Functions.getIntersectionPoint(primA.getGeometryLine(), primB.getGeometryLine());
    if (ix != null) {
      Pt d1 = primA.getEndPt();
      if (primA.getStartPt().distance(ix) > primA.getEndPt().distance(ix)) {
        d1 = primA.getStartPt();
      }
      Pt d2 = primB.getEndPt();
      if (primB.getStartPt().distance(ix) > primB.getEndPt().distance(ix)) {
        d2 = primB.getStartPt();
      }
      if (d1.distance(ix) > 0 && d2.distance(ix) > 0) {
        Vec v1 = new Vec(ix, d1).getUnitVector();
        Vec v2 = new Vec(ix, d2).getUnitVector();
        double dot = v1.dot(v2);
        double theta = Math.acos(dot);// v1 and v2 are unit vectors already
        if (theta > 0.04 && theta < 1.4) {
          ret = Certainty.Yes;
        } else if (theta < 1.66) {
          ret = Certainty.Maybe;
        }
      }
    }
    return ret;
  }

  @SuppressWarnings("unused")
  private static void bug(String what) {
    Debug.out("AcuteAngle", what);
  }

  @Override
  public String getShortStr() {
    return null;
  }

}
