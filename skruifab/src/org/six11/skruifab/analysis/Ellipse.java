package org.six11.skruifab.analysis;

import org.six11.util.pen.RotatedEllipse;

/**
 * 
 *
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Ellipse extends Primitive {

  RotatedEllipse rotatedEllipse;
  
  /**
   * @param seq
   * @param startIdx
   * @param endIdx
   * @param cert
   */
  public Ellipse(Stroke seq, Certainty cert) {
    super(seq, 0, seq.size() - 1, cert);
  }

  /**
   * @param primID
   * @param seq
   * @param cert
   */
  public Ellipse(int primID, Stroke seq, Certainty cert) {
    super(primID, seq, 0, seq.size() - 1, cert);
  }
  
  public RotatedEllipse getRotatedEllipse() {
    return rotatedEllipse;
  }
  
  public void setRotatedEllipse(RotatedEllipse re) {
    this.rotatedEllipse = re;
  }

  @Override
  public String typeStr() {
    return "Ellipse";
  }

  @Override
  public String shortTypeStr() {
    return "E";
  }

}
