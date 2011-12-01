package org.six11.sf.rec;

import org.six11.sf.rec.RecognizerPrimitive.Certainty;
import org.six11.util.math.Interval;
import static org.six11.util.Debug.num;

public class LineLengthConstraint extends RecognizerConstraint {

  private Interval yesInterval;
  private Interval maybeInterval;
  
  public LineLengthConstraint(String constraintName, Interval yesInterval, Interval maybeInterval, String slotName) {
    super(constraintName, slotName);
    this.yesInterval = yesInterval;
    this.maybeInterval = maybeInterval;
  }

  public Interval getYesInterval() {
    return yesInterval;
  }
  
  public Interval getMaybeInterval() {
    return maybeInterval;
  }
  
  @Override
  public Certainty check(RecognizerPrimitive... p) {
    double len = p[0].getLength();
    Certainty ret = Certainty.Unknown;
    if (yesInterval.contains(len)) {
      ret = Certainty.Yes;
    } else if (maybeInterval.contains(len)) {
      ret = Certainty.Maybe;
    } else {
      ret = Certainty.No;
    }
    say(num(len) + " in range? " + ret);
    return ret;
  }

}
