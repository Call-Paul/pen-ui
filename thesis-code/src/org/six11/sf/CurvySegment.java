package org.six11.sf;

import java.util.List;

import org.six11.util.pen.Pt;

/**
 * A curvy segment is any segment that can't be nicely fit to another type. This will most likely be
 * presented as a spline.
 */
public class CurvySegment extends SegmentDelegate {

  public CurvySegment(Ink ink, List<Pt> points, boolean termA, boolean termB) {
    super(ink, points, termA, termB);
    this.type = Segment.Type.Curve;
  }
  
  public CurvySegment(List<Pt> points) {
    init(null, points, false, false, Segment.Type.Curve);
  }

}
