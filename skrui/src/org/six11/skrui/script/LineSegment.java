package org.six11.skrui.script;

import org.six11.skrui.script.Neanderthal.Certainty;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

/**
 * 
 *
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class LineSegment extends Primitive {

  private boolean fixedAngleValid;
  private double fixedAngle;

  /**
   * @param seq
   * @param startIdx
   * @param endIdx
   * @param cert
   */
  public LineSegment(Sequence seq, int startIdx, int endIdx, Certainty cert) {
    super(seq, startIdx, endIdx, cert);
    fixedAngleValid = false;
  }

  @Override
  public String typeStr() {
    return "Line";
  }

  public double getFixedAngle() {
    if (!fixedAngleValid) {
      Pt start = seq.get(startIdx);
      Pt end = seq.get(endIdx);
      Pt left = start;
      Pt right = end;
      if (Pt.sortByX.compare(start, end) == 1) {
        left = end;
        right = start;
      }
      double dx = right.x - left.x;
      double dy = right.y - left.y;
      fixedAngle = Math.atan2(dy, dx);
      fixedAngleValid = true;
    }
    return fixedAngle;
  }

}
