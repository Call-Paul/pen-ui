package org.six11.sf.rec;

import org.six11.sf.SketchBook;
import org.six11.util.math.Interval;

public class AcuteAngle extends AngleConstraint {

  public AcuteAngle(SketchBook model, String name, String... sNames) {
    super(model, name, new Interval(0, 1.4), new Interval(0, 1.66), sNames);
  }

}
