package org.six11.skruifab.analysis;

import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
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
  public Dot(Stroke seq, Certainty cert) {
    super(seq, 0, seq.size() - 1, cert);
  }

  /**
   * @param primID
   * @param seq
   * @param cert
   */
  public Dot(int primID, Stroke seq, Certainty cert) {
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

  public Pt getCentroid() {
    Sequence dotPoints = seq.getSubSequence(getStartIdx(), getEndIdx());
    return Functions.getMean(dotPoints.getPoints());
  }
}
