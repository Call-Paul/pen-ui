package org.six11.skruifab.analysis;

import org.six11.util.math.EllipseFit;
import org.six11.util.pen.RotatedEllipse;

public class EllipseSegment extends Primitive {

  RotatedEllipse ellie;
  
  public EllipseSegment(Stroke seq, int startIdx, int endIdx, Certainty cert) {
    super(seq, startIdx, endIdx, cert);
  }

  @Override
  public String typeStr() {
    return "Elli-Arc";
  }

  @Override
  public String shortTypeStr() {
    return "A";
  }

  public RotatedEllipse getEllipse() {
    if (ellie == null) {
      ellie = EllipseFit.ellipseFit(seq.getSubSequence(startIdx, endIdx).getPoints());
      ellie.setArcRegion(seq.get(startIdx), seq.get((startIdx + endIdx)/ 2), seq.get(endIdx));
    }
    return ellie;
  }
  
}
