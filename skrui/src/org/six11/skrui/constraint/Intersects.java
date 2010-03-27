package org.six11.skrui.constraint;

import org.six11.skrui.script.Neanderthal;
import org.six11.skrui.script.Primitive;
import org.six11.skrui.script.Neanderthal.Certainty;
import org.six11.util.pen.Functions;
import org.six11.util.pen.IntersectionData;
import org.six11.util.pen.Line;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Intersects extends Constraint {

  /**
   * @param name
   * @param data
   * @param sNames
   */
  public Intersects(String name, Neanderthal data, String slotA, String slotB) {
    super(name, data, slotA, slotB);
  }

  @Override
  public Certainty check(Primitive[] p) {
    Primitive primA = p[0];
    Primitive primB = p[1];

    Certainty certainty;
    certainty = checkIntersection(primA, primB);
    return certainty;
  }

  private Certainty checkIntersection(Primitive primA, Primitive primB) {
    Certainty ret = Certainty.No;
    Line a = new Line(primA.getStartPt(), primA.getEndPt());
    Line b = new Line(primB.getStartPt(), primB.getEndPt());
    IntersectionData id = Functions.getIntersectionData(a, b);
    if (id.intersectsInSegments()) {
      ret = Certainty.Yes;
    }
    return ret;
  }

  @Override
  public String getShortStr() {
    return null;
  }

}
