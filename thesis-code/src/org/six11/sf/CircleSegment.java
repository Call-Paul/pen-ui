package org.six11.sf;

import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;
import static java.lang.Math.cos;
import static java.lang.Math.sin;

import org.six11.util.Debug;
import org.six11.util.gui.shape.Circle;
import org.six11.util.gui.shape.ShapeFactory;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.RotatedEllipse;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

public class CircleSegment extends SegmentDelegate {
  private Circle circ;
  private transient double cachedR;
  private transient double cachedX;
  private transient double cachedY;
  private transient List<Pt> cachedPL;

  public CircleSegment(Ink ink, List<Pt> points) {
    bug("Creating closed circle!");
    RotatedEllipse ellie = Functions.createEllipse(points, false);
    double a = ellie.getMajorRadius();
    double b = ellie.getMinorRadius();
    this.circ = new Circle(ellie.getCentroid(), (a + b) / 2.0);
    this.ink = ink;
    this.p1 = points.get(0);
    this.p2 = p1;
    this.type = Segment.Type.Circle;
  }

  public boolean hasEndCaps() {
    return false;
  }

  public Shape asCircle() {
    return circ;
  }

  public Circle getCircle() {
    return circ;
  }

  public List<Pt> getPointList() {
    List<Pt> ret = null;
    if (cachedR == circ.getRadius() && cachedX == circ.x && cachedY == circ.y && cachedPL != null) {
      bug("Using cached circle pointlist");
      ret = cachedPL;
    } else {
      ret = new ArrayList<Pt>();
      double r = circ.getRadius();
      double circumference = 2.0 * r * Math.PI;
      int numSteps = (int) circumference;
      bug("Using " + numSteps + " steps with circle of circumference " + num(circumference));
      double step = (2.0 * Math.PI) / numSteps;
      for (int i = 0; i <= numSteps; i++) {
        double ang = ((double) i) * step;
        double x = circ.x + r + (r * cos(ang));
        double y = circ.y + r + (r * sin(ang));
        ret.add(new Pt(x, y));
      }
      cachedR = r;
      cachedX = circ.x;
      cachedY = circ.y;
      cachedPL = ret;
    }
    return ret;
  }

  public List<Pt> asPolyline() {
    return getPointList();
  }

}
