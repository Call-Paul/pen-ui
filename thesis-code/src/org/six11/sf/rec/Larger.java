package org.six11.sf.rec;

import org.six11.sf.rec.RecognizerPrimitive.Certainty;

public class Larger extends RecognizerConstraint {

  public Larger(String name, String... sNames) {
    super(name, sNames);
  }

  public Certainty check(RecognizerPrimitive... p) {
    double l0 = p[0].getLength();
    double l1 = p[1].getLength();
    double ratio = l1 / l0;
    Certainty ret = Certainty.No;
    if (ratio < 0.5) {
      ret = Certainty.Yes;
    } else if (ratio < 0.85) {
      ret = Certainty.Maybe;
    }
    return ret;
  }

}
