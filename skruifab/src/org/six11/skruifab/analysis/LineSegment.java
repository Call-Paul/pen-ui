package org.six11.skruifab.analysis;

import org.six11.util.Debug;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class LineSegment extends Primitive {

  public LineSegment(Stroke seq, int startIdx, int endIdx, Certainty cert) {
    super(seq, startIdx, endIdx, cert);
  }

  /**
   * @param primID
   * @param seq
   * @param startIdx
   * @param endIdx
   * @param cert
   */
  public LineSegment(int primID, Stroke seq, int startIdx, int endIdx, Certainty cert) {
    super(primID, seq, startIdx, endIdx, cert);
  }

  @Override
  public String typeStr() {
    return "Line";
  }


  @SuppressWarnings("unused")
  private static void bug(String what) {
    Debug.out("LineSegment", what);
  }

  @Override
  public String shortTypeStr() {
    return "L";
  }


}
