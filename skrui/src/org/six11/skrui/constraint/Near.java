package org.six11.skrui.constraint;

import org.six11.skrui.script.Dot;
import org.six11.skrui.script.LineSegment;
import org.six11.skrui.script.Neanderthal;
import org.six11.skrui.script.Primitive;
import org.six11.skrui.script.Neanderthal.Certainty;
import org.six11.util.Debug;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Near extends Constraint {

  /**
   * @param name
   * @param data
   * @param sNames
   */
  public Near(String name, Neanderthal data, String slotA, String slotB) {
    super(name, data, slotA, slotB);
  }

  @Override
  public Certainty check(Primitive[] p) {
    Certainty ret = Certainty.No;
    Primitive primA = p[0];
    Primitive primB = p[1];

    if (primA instanceof Dot && primB instanceof LineSegment) {
      ret = checkNearDotLine((Dot) primA, (LineSegment) primB);
    } else {
      bug("Sorry! Edit Near.java and add a check function for types: " + primA.getClass() + " and "
          + primB.getClass());
    }
    return ret;
  }

  private Certainty checkNearDotLine(Dot dot, LineSegment line) {
    Pt dotPt = dot.getMidPt();
    Line l = line.getGeometryLine();
    double length = line.getLength();
    double dist = Functions.getDistanceBetweenPointAndLine(dotPt, l);
    Certainty ret = Certainty.No;
    if (dist < Math.max(12, length / 4)) {
      ret = Certainty.Yes;
    } else if (dist < Math.max(20, length / 3)) {
      ret = Certainty.Maybe;
    }
    return ret;
  }

  public static void bug(String what) {
    Debug.out("Near", what);
  }

  @Override
  public String getShortStr() {
    return "Near";
  }

}
