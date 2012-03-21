package org.six11.sf;

import java.util.List;

import org.six11.util.pen.Pt;

/**
 * A curvy segment is any segment that can't be nicely fit to another type. This will most likely be
 * presented as a spline.
 */
public class CurvySegment extends SegmentDelegate {

  public CurvySegment(Ink ink, List<Pt> points, boolean termA, boolean termB) {
    super(ink, points);
    this.type = Segment.Type.Curve;
    this.termA = termA;
    this.termB = termB;
  }
  
  public CurvySegment(List<Pt> points) {
    init(null, points, Segment.Type.Curve, false, false);
  }
  
  public CurvySegment(Pt p1, Pt p2, double[] primaryCoordinates, double[] secondaryCoordinates) {
    init(null, p1, p2, primaryCoordinates, secondaryCoordinates, Segment.Type.Curve);
  }

}
