package org.six11.skrui.shape;

import org.six11.skrui.script.Neanderthal.Certainty;

/**
 * 
 *
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Ellipse extends Primitive {

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

  @Override
  public String typeStr() {
    return "Ellipse";
  }

  @Override
  public String shortTypeStr() {
    return "E";
  }

}
