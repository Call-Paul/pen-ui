package org.six11.sf;

import java.util.ArrayList;
import java.util.List;

import org.six11.util.pen.Pt;
import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

public class LineSegment extends SegmentDelegate {

  public LineSegment(Ink ink, List<Pt> points, boolean termA, boolean termB) {
    List<Pt> simplePoints = new ArrayList<Pt>();
    simplePoints.add(points.get(0));
    simplePoints.add(points.get(points.size() - 1));
    init(ink, simplePoints, Segment.Type.Line);
//    super(ink, simplePoints, termA, termB);
//    this.type = Segment.Type.Line;
  }

  public LineSegment(Pt p1, Pt p2) {
    List<Pt> points = new ArrayList<Pt>();
    points.add(p1);
    points.add(p2);
    init(null, points, Segment.Type.Line);
  }

}
