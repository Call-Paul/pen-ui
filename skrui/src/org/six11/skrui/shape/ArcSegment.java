package org.six11.skrui.shape;

import org.six11.skrui.script.Neanderthal.Certainty;
import org.six11.util.pen.Sequence;

/**
 * 
 *
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class ArcSegment extends Primitive {

  /**
   * @param seq
   * @param startIdx
   * @param endIdx
   * @param cert
   */
  public ArcSegment(Sequence seq, int startIdx, int endIdx, Certainty cert) {
    super(seq, startIdx, endIdx, cert);
  }

  @Override
  public String typeStr() {
    return "Arc";
  }

  @Override
  public String shortTypeStr() {
    return "A";
  }

}
