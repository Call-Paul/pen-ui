package org.six11.sf;

import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.six11.util.Debug;
import static org.six11.util.Debug.num;
import static org.six11.util.Debug.bug;

import org.six11.util.gui.shape.Circle;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.Vec;

import static java.lang.Math.min;
import static java.lang.Math.ceil;

@SuppressWarnings("unused")
public abstract class Segment {

  int id;
  private static int ID_COUNTER = 1;

  public static enum Type {
    Line, Curve, Unknown, EllipticalArc
  };

  List<Pt> points;
  Type type;
  Line line;
  Sequence spline;
  boolean[] terms;

  public Segment(List<Pt> points, boolean termA, boolean termB) {
    this.points = points;
    for (Pt pt : points) {
      if (pt.getTime() == 0) {
        Debug.stacktrace("point has zero time stamp!", 7);
      }
    }
    this.type = Type.Unknown;
    terms = new boolean[] {
        termA, termB
    };
    id = ID_COUNTER++;
  }

  public String toString() {
    StringBuilder buf = new StringBuilder();
    switch (getType()) {
      case Curve:
        buf.append("C");
        break;
      case EllipticalArc:
        buf.append("E");
        break;
      case Line:
        buf.append("L");
        break;
      case Unknown:
        buf.append("?");
        break;
    }
    buf.append("[" + num(getP1()) + " to " + num(getP2()) + ", length: " + num(length()) + "]");
    return buf.toString();
  }

  public int getId() {
    return id;
  }

  public Type getType() {
    return type;
  }

  public Pt getP1() {
    return points.get(0);
  }

  public Pt getP2() {
    return points.get(points.size() - 1);
  }

  public Line asLine() {
    if (line == null) {
      line = new Line(getP1(), getP2());
    }
    return line;
  }

  public double length() {
    double ret = 0;
    if (type == Type.Line) {
      ret = getP1().distance(getP2());
    } else if (type == Type.Curve) {
      ret = asSpline().length();
    }
    return ret;
  }

  public double ctrlPointLength() {
    double ret = 0;
    for (int i = 0; i < points.size() - 1; i++) {
      ret += points.get(i).distance(points.get(i + 1));
    }
    return ret;
  }

  public Vec getStartDir() {
    Vec ret = null;
    switch (type) {
      case Line:
        ret = new Vec(getP1(), getP2()).getUnitVector();
        break;
      case EllipticalArc:
      case Curve:
        Sequence spline = asSpline(); // spline should reliably give the direction at the ends
        ret = new Vec(spline.get(0), spline.get(1)).getUnitVector();
        break;
    }
    return ret;
  }
  
  public Vec getEndDir() {
    Vec ret = null;
    switch (type) {
      case Line:
        ret = new Vec(getP2(), getP1()).getUnitVector();
        break;
      case EllipticalArc:
      case Curve:
        Sequence spline = asSpline(); // spline should reliably give the direction at the ends
        ret = new Vec(spline.get(spline.size() - 1), spline.get(spline.size() - 2)).getUnitVector();
        break;
    }
    return ret;
  }

  public Sequence asSpline() {
    if (spline == null) {
      double roughLength = 0;
      for (int i = 0; i < points.size() - 1; i++) {
        roughLength = roughLength + points.get(i).distance(points.get(i + 1));
      }
      int numSteps = (int) ceil(min(roughLength / 100, 10));
      spline = Functions.makeNaturalSpline(numSteps, points);
    }
    return spline;
  }

  public void move(double dx, double dy) {
    for (Pt pt : points) {
      pt.setLocation(pt.getX() + dx, pt.getY() + dy);
    }
    switch (type) {
      case Line:
        Line l = asLine();
        l.setLine(l.getX1() + dx, l.getY1() + dy, l.getX2() + dx, l.getY2() + dy);
        break;
      case EllipticalArc:
      case Curve:
        Sequence s = asSpline();
        for (Pt pt : s) {
          pt.setLocation(pt.getX() + dx, pt.getY() + dy);
        }
        break;
      default:
        bug("FAIL");
        break;
    }
  }


  //  public class Terminal {
  //
  //    Pt pt;
  //    Vec dir;
  //    boolean fixed;
  //
  //    private Terminal(Pt pt, Vec dir, boolean fixed) {
  //      this.pt = pt;
  //      this.dir = dir;
  //      this.fixed = fixed;
  //    }
  //
  //    public Pt getPoint() {
  //      return pt;
  //    }
  //
  //    public Vec getDir() {
  //      return dir;
  //    }
  //
  //    public String toString() {
  //      return "S-" + id + "/T-" + (pt == getP1() ? "1" : "2");
  //    }
  //
  //    public Segment getSegment() {
  //      return Segment.this;
  //    }
  //
  //    public boolean isSame(Terminal near) {
  //      return (pt.isSameLocation(near.getPoint()) && dir.isSame(near.getDir()));
  //    }
  //
  //    public boolean isFixed() {
  //      return fixed;
  //    }
  //
  //    public Line getLine() {
  //      return new Line(getPoint(), getDir());
  //    }
  //
  //    public Pt getOpposingTermPoint() {
  //      return pt == getP1() ? getP2() : getP1();
  //    }
  //
  //    //
  //    //    /**
  //    //     * Returns a point list for this segment with the terminal's endpoint as the first element. Use
  //    //     * this when you need to traverse points along the segment beginning at one end.
  //    //     * 
  //    //     * @return
  //    //     */
  //    //    public List<Pt> getPointListFromTerm() {
  //    //      List<Pt> ret = new ArrayList<Pt>();
  //    //      ret.addAll(points);
  //    //      if (pt == getP2()) {
  //    //        Collections.reverse(ret);
  //    //      }
  //    //      return ret;
  //    //    }
  //
  //    public List<Pt> getSurfacePolyline() {
  //      List<Pt> ret = new ArrayList<Pt>();
  //      if (type == Segment.Type.Line) {
  //        ret.add(getPoint());
  //        ret.add(getOpposingTermPoint());
  //      } else if (type == Segment.Type.Curve || type == Segment.Type.EllipticalArc) {
  //        if (pt == getP1()) {
  //          ret.addAll(asPolyline());
  //        } else {
  //          ret.addAll(asPolyline());
  //          Collections.reverse(ret);
  //        }
  //      } else {
  //        Debug.warn(this, "Unknown seg type in getSurfacePolyline(): " + type);
  //      }
  //      for (Pt pt : ret) {
  //        if (pt.getTime() == 0) {
  //          Debug.stacktrace("no time stamp in " + getType(), 8);
  //        }
  //      }
  //      return ret;
  //    }
  //
  //  }
  //
  //  public List<Terminal> getTerminals() {
  //    List<Terminal> ret = new ArrayList<Terminal>();
  //
  //    if (type == Segment.Type.Line) {
  //      ret.add(new Terminal(getP1(), new Vec(getP2(), getP1()).getUnitVector(), !terms[0]));
  //    } else if (type == Segment.Type.Curve || type == Segment.Type.EllipticalArc) {
  //      ret.add(new Terminal(getP1(), new Vec(points.get(1), points.get(0)).getUnitVector(),
  //          !terms[0]));
  //    } else {
  //      bug("unknown seg type in getTerminals");
  //    }
  //
  //    if (type == Segment.Type.Line) {
  //      ret.add(new Terminal(getP2(), new Vec(getP1(), getP2()).getUnitVector(), !terms[1]));
  //    } else if (type == Segment.Type.Curve || type == Segment.Type.EllipticalArc) {
  //      ret.add(new Terminal(getP2(), new Vec(points.get(points.size() - 2),
  //          points.get(points.size() - 1)).getUnitVector(), !terms[1]));
  //    } else {
  //      bug("unknown seg type in getTerminals");
  //    }
  //
  //    return ret;
  //  }

  /**
   * If you modify the segment externally, call this so cached stuff is re-calcuated.
   */
  public void setModified() {
    spline = null;
  }

  public List<Pt> asPolyline() {
    return points;
  }

  public boolean isNear(Pt point, double dist) {
    boolean ret = false;
    Pt where = null;
    if (type == Segment.Type.Line) {
      where = Functions.getNearestPointOnLine(point, asLine());
    } else if (type == Segment.Type.Curve || type == Segment.Type.EllipticalArc) {
      where = Functions.getNearestPointOnPolyline(point, points);
    }
    if (where != null && where.distance(point) <= dist) {
      ret = true;
    }
    return ret;
  }

}
