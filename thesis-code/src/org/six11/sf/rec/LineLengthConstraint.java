package org.six11.sf.rec;

import org.six11.sf.rec.RecognizerPrimitive.Certainty;
import org.six11.util.math.Interval;

public class LineLengthConstraint extends RecognizerConstraint {

  private Interval interval;
  
  public LineLengthConstraint(String constraintName, Interval interval, String slotName) {
    super(constraintName, slotName);
    this.interval = interval;
  }

  public Interval getInterval() {
    return interval;
  }
  
  @Override
  public Certainty check(RecognizerPrimitive... p) {
    Certainty ret = Certainty.Unknown;
    
    return ret;
  }

}
