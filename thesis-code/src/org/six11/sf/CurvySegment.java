package org.six11.sf;

import java.util.List;

import org.six11.util.pen.Pt;

/**
 * A curvy segment is any segment that can't be nicely fit to another type. This will most likely be
 * presented as a spline.
 */
public class CurvySegment extends Segment {

  public CurvySegment(List<Pt> points, boolean termA, boolean termB) {
    super(points, termA, termB);
    this.type = Type.Curve;
  }

}
