package org.six11.sf.rec;

import static org.six11.util.Debug.num;

import org.six11.sf.SketchBook;
import org.six11.sf.rec.RecognizerPrimitive.Certainty;
import org.six11.util.math.Interval;

public class LineLengthConstraint extends RecognizerConstraint {

  private Interval yesInterval;
  private Interval maybeInterval;
  
  public LineLengthConstraint(SketchBook model, String constraintName, Interval yesInterval, Interval maybeInterval, String slotName) {
    super(model, constraintName, slotName);
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
