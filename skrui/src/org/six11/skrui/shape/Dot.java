package org.six11.skrui.shape;

import org.six11.skrui.script.Neanderthal.Certainty;
import org.six11.util.pen.Sequence;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Dot extends Primitive {

  /**
   * @param seq
   */
  public Dot(Sequence seq, Certainty cert) {
    super(seq, 0, seq.size() - 1, cert);
  }

  /**
   * @param primID
   * @param seq
   * @param cert
   */
  public Dot(int primID, Sequence seq, Certainty cert) {
    super(primID, seq, 0, seq.size() - 1, cert);
  }

  @Override
  public String typeStr() {
    return "Dot";
  }

  @Override
  public String shortTypeStr() {
    return "D";
  }

}
