package org.six11.sf;

import java.util.List;

import org.six11.util.pen.Pt;

public class CircularArcSegment extends Segment {

  public CircularArcSegment(Ink ink, List<Pt> points, Pt initialCenter, double initialRadius,
      boolean termA, boolean termB) {
    init(ink, points, termA, termB, Type.CircularArc);
  }

}
