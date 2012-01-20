package org.six11.sf;

import java.util.ArrayList;
import java.util.List;

import org.six11.util.pen.Pt;
import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

public class LineSegment extends Segment {

  public LineSegment(Ink ink, List<Pt> points, boolean termA, boolean termB) {
    super(ink, points, termA, termB);
    this.type = Type.Line;
  }

  public LineSegment(Pt p1, Pt p2) {
    List<Pt> points = new ArrayList<Pt>();
    points.add(p1);
    points.add(p2);
    bug("Building LineSegment using direct constructor with points: " + num(p1) + ", " + num(p2));
    init(null, points, false, false, Type.Line);
  }

}
