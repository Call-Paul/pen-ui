package org.six11.sf;

import java.util.Collections;

import org.six11.sf.rec.RecognizerPrimitive.Certainty;
import org.six11.util.pen.Pt;

public class Dot extends SegmentDelegate {

  private Certainty cert;

  public Dot(Pt mid, Certainty cert) {
    init(ink, Collections.singletonList(mid), Segment.Type.Dot, false, false);
    this.cert = cert;
  }

  public Certainty getCertainty() {
    return cert;
  }

  public boolean hasEndCaps() {
    return false;
  }

}
