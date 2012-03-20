package org.six11.sf.rec;

import static java.lang.Math.toRadians;

import org.six11.sf.SketchBook;
import org.six11.util.math.Interval;

public class RightAngle extends AngleConstraint {

  public RightAngle(SketchBook model, String name, String... sNames) {
    super(model, name, new Interval(toRadians(84), toRadians(96)), new Interval(toRadians(75),
        toRadians(105)), sNames);
  }

}
