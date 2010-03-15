package org.six11.skrui.script;

import org.six11.skrui.script.Neanderthal.Certainty;
import org.six11.util.pen.Sequence;

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
  public Ellipse(Sequence seq, Certainty cert) {
    super(seq, 0, seq.size() - 1, cert);
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
