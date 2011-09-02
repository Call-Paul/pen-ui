package org.six11.sf;

import java.util.List;

import org.six11.util.pen.Pt;

public class CurvySegment extends Segment {

  public CurvySegment(List<Pt> points, boolean termA, boolean termB) {
    super(points, termA, termB);
    this.type = Type.Curve;
  }

}
