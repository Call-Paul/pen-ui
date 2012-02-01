package org.six11.sf;

import java.util.ArrayList;
import java.util.List;

import org.six11.util.pen.Pt;
import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

public class LineSegment extends SegmentDelegate {

  public LineSegment(Ink ink, List<Pt> points, boolean termA, boolean termB) {
    super(ink, points, termA, termB);
    this.type = Segment.Type.Line;
  }

  public LineSegment(Pt p1, Pt p2) {
    List<Pt> points = new ArrayList<Pt>();
    points.add(p1);
    points.add(p2);
    init(null, points, false, false, Segment.Type.Line);
  }

}
