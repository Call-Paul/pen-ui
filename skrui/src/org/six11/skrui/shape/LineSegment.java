package org.six11.skrui.shape;

import org.six11.skrui.script.Neanderthal.Certainty;
import org.six11.util.Debug;
import org.six11.util.pen.Sequence;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class LineSegment extends Primitive {

  public LineSegment(Sequence seq, int startIdx, int endIdx, Certainty cert) {
    super(seq, startIdx, endIdx, cert);
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
