package org.six11.sf;

import static java.lang.Math.ceil;
import static java.lang.Math.min;
import static org.six11.util.Debug.num;
import static org.six11.util.Debug.bug;

import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.six11.util.Debug;
import org.six11.util.gui.shape.ShapeFactory;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.Vec;

public class Segment implements HasFuzzyArea {

  int id;
  private static int ID_COUNTER = 1;

  public static enum Type {
    Line, Curve, Unknown, EllipticalArc
  };

  List<Pt> points;
  Type type;
//  Sequence spline;
  Ink ink;
  boolean termA, termB;

  public Segment(Ink ink, List<Pt> points, boolean termA, boolean termB) {
    this.ink = ink;
    this.points = points;
    this.termA = termA;
    this.termB = termB;
    for (Pt pt : points) {
      if (pt.getTime() == 0) {
        Debug.stacktrace("point has zero time stamp!", 7);
      }
    }
    this.type = Type.Unknown;
    //    terms = new boolean[] {
    //        termA, termB
    //    };
    id = ID_COUNTER++;
  }

  public Segment(Ink ink, List<Pt> points, boolean termA, boolean termB, Type t) {
    this.ink = ink;
    this.points = points;
    this.termA = termA;
    this.termB = termB;
    this.type = t;
    id = ID_COUNTER++;
  }

  public Ink getOriginalInk() {
    return ink;
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

  public Collection<EndCap> getEndCaps() {
    Collection<EndCap> ret = new HashSet<EndCap>();
    ret.add(new EndCap(this, EndCap.WhichEnd.Start));
    ret.add(new EndCap(this, EndCap.WhichEnd.End));
    return ret;
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
    return new Line(getP1(), getP2());
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

  public double getMinAngle(Segment other) {
    Vec target = getStartDir();
    Vec segStart = other.getStartDir();
    Vec segEnd = other.getEndDir();
    double angleStart = Math.abs(Functions.getSignedAngleBetween(target, segStart));
    double angleEnd = Math.abs(Functions.getSignedAngleBetween(target, segEnd));
    return Math.min(angleStart, angleEnd);
  }

  public Sequence asSpline() {
    //    if (spline == null) {
    double roughLength = 0;
    for (int i = 0; i < points.size() - 1; i++) {
      roughLength = roughLength + points.get(i).distance(points.get(i + 1));
    }
    int numSteps = (int) ceil(min(roughLength / 100, 10));
    Sequence spline = Functions.makeNaturalSpline(numSteps, points);
    //    }
    return spline;
  }
//
//  /**
//   * If you modify the segment externally, call this so cached stuff is re-calcuated.
//   */
//  public void setModified() {
//    spline = null;
//  }

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

  public void replace(Pt capPt, Pt spot) {
    if (capPt == getP1()) {
      points.remove(0);
      points.add(0, spot);
    }
    if (capPt == getP2()) {
      points.remove(points.size() - 1);
      points.add(spot);
    }
  }

  /**
   * Returns a list of points that define the geometry of this segment. For lines this is simply two
   * points. For splines and elliptical arcs there might be many more. 
   */
  public List<Pt> getPointList() {
    List<Pt> ret = new ArrayList<Pt>();
    if (type == Type.Line){
      ret.add(getP1());
      ret.add(getP2());
    } else {
      ret.addAll(asPolyline());
    }
    
    return ret;
  }

  public Segment copy() {
    List<Pt> copiedPoints = new ArrayList<Pt>();
    for (Pt pt : points) {
      copiedPoints.add(pt.copyXYT());
    }
    Segment ret = new Segment(this.ink, copiedPoints, termA, termB, type);
    return ret;
  }

  public Area getFuzzyArea() {
    Area fuzzy = new Area();
    List<Pt> pl = getPointList();
    for (int i=0; i < pl.size() - 1; i++) {
      Pt a = pl.get(i);
      Pt b = pl.get(i+1);
      Shape s = ShapeFactory.getFuzzyRectangle(a, b, 5.0); 
      fuzzy.add(new Area(s));
    }
    return fuzzy;
  }

  public boolean involves(Pt p) {
    return p == getP1() || p == getP2();
  }

}
