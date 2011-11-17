package org.six11.sf.rec;

import org.six11.util.math.Interval;

public class AcuteAngle extends AngleConstraint {

  public AcuteAngle(String name, String... sNames) {
    super(name, new Interval(0, 1.4), new Interval(0, 1.66), sNames);
  }

}
