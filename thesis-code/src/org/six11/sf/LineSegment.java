package org.six11.sf;

import java.util.List;

import org.six11.util.pen.Pt;

public class LineSegment extends Segment {

  public LineSegment(List<Pt> points, boolean termA, boolean termB) {
    super(points, termA, termB);
    this.type = Type.Line;
  }

}
