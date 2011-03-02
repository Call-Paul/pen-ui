package org.six11.skruifab.analysis;

import org.six11.util.pen.CircleArc;

/**
 * 
 *
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class ArcSegment extends Primitive {

  CircleArc circleArc;
  
  /**
   * @param seq
   * @param startIdx
   * @param endIdx
   * @param cert
   */
  public ArcSegment(Stroke seq, int startIdx, int endIdx, Certainty cert) {
    super(seq, startIdx, endIdx, cert);
  }

  /**
   * @param primID
   * @param seq
   * @param startIdx
   * @param endIdx
   * @param cert
   */
  public ArcSegment(int primID, Stroke seq, int startIdx, int endIdx, Certainty cert) {
    super(primID, seq, startIdx, endIdx, cert);
  }

  @Override
  public String typeStr() {
    return "Arc";
  }

  @Override
  public String shortTypeStr() {
    return "A";
  }
  
  public CircleArc getCircleArc() {
    if (circleArc == null) {
      int a = getStartIdx();
      int b = getEndIdx();
      int c = (a + b) / 2;
      circleArc = new CircleArc(getStartPt(), seq.get(c), getEndPt());
    }
    return circleArc;
  }
}
