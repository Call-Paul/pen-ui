package org.six11.sf;

import static java.lang.Math.ceil;
import static java.lang.Math.min;
import static org.six11.util.Debug.num;
import static org.six11.util.Debug.bug;

import java.awt.Shape;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.six11.util.gui.shape.ShapeFactory;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.Vec;

public class SegmentDelegate implements HasFuzzyArea {

  protected Pt p1, p2; // start/end points. these can be moved around externally

  // parametric points: they are dependent on p1 and p2, so internal code here
  // might have to adjust them if p1 or p2 change.
  protected double[] pri; // parametric points primary coordinate, along vector from p1 to p2
  protected double[] alt; // parametric points secondary coordinate, orthogonal to the above

  // transient variables that describe the parametric point sequence for the current values
  // of p1 and p2.
  protected transient Pt paraP1Loc = null;
  protected transient Pt paraP2Loc = null;
  protected transient List<Pt> paraPoints = null;
  protected transient List<Pt> deformedPoints = null;

  Segment.Type type;
  //  Sequence spline;
  Ink ink;
  boolean termA, termB;

  protected SegmentDelegate() {
    // ensure subclass calls init();
  }

  public SegmentDelegate(Ink ink, List<Pt> points, boolean termA, boolean termB) {
    this(ink, points, termA, termB, Segment.Type.Unknown);
  }

  public SegmentDelegate(Ink ink, List<Pt> points, boolean termA, boolean termB, Segment.Type t) {
    init(ink, points, termA, termB, t);
  }

  protected final void init(Ink ink, List<Pt> points, boolean termA, boolean termB, Segment.Type t) {
    this.ink = ink;
    this.p1 = points.get(0);
    this.p2 = points.get(points.size() - 1);
    calculateParameters(points);
    this.termA = termA;
    this.termB = termB;
    this.type = t;
  }

  public final void calculateParameters(List<Pt> points) {
    this.pri = new double[points.size()];
    this.alt = new double[points.size()];
    Pt start = points.get(0);
    Pt end = points.get(points.size() - 1);
    this.p1.setLocation(start.getX(), start.getY());
    this.p2.setLocation(end.getX(), end.getY());
    Vec v = new Vec(p1, p2);
    double vMag = v.mag();
    Line line = new Line(p1, p2);
    for (int i = 0; i < points.size(); i++) {
      Pt target = points.get(i);
      if (target.isSameLocation(p1)) {
        pri[i] = 0;
        alt[i] = 0;
      } else {
        Vec toTarget = calculateParameterForPoint(vMag, line, target);
        pri[i] = toTarget.getX();
        alt[i] = toTarget.getY();
      }
    }
    doPara();
  }

  /**
   * Calculate the parameter for the target point along the given line segment.
   * 
   * @param vMag
   *          the length of the line. passed in so it can be calculated one time, and used in a
   *          loop.
   * @param line
   *          the segment as a line.
   * @param target
   *          the point we are seeking to parameterize
   * @return a vector that can be used in conjunction with the line's start/end points that describe
   *         where the target point is. The X component is how far along the target is in the
   *         direction of the line (from start to end), and the Y component is orthogonal to it. The
   *         sign of the Y component is determined by Functions.getPartition(target, line).
   */
  public static Vec calculateParameterForPoint(double vMag, Line line, Pt target) {
    // TODO: move this into Segment.
    Pt ix = Functions.getNearestPointOnLine(target, line, true); // retains the 'r' double value
    int whichSide = Functions.getPartition(target, line);
    double dist = ix.distance(target) * whichSide;
    Vec toTarget = new Vec(ix.getDouble("r"), dist / vMag);
    return toTarget;
  }

  public Ink getOriginalInk() {
    return ink;
  }

  public boolean isSingular() {
    return getP1().isSameLocation(getP2());
  }

  public List<Pt> storeParaPointsForDeformation() {
    doPara();
    deformedPoints = new ArrayList<Pt>();
    deformedPoints.addAll(paraPoints);
    return deformedPoints;
  }

  public List<Pt> getDeformedPoints() {
    return deformedPoints;
  }

  public void clearDeformation() {
    deformedPoints = null;
  }

  public String toString() {
    StringBuilder buf = new StringBuilder();
    switch (getType()) {
      case Curve:
        buf.append("S");
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
      case CircularArc:
        buf.append("C");
        break;
      default:
        bug("Unknown segment type: " + getType());
    }
    if (SketchBook.n(getP1()) != null && SketchBook.n(getP2()) != null) {
      buf.append("[" + SketchBook.n(getP1()) + " to " + SketchBook.n(getP2()) + ", length: "
          + num(length()) + "]");
    } else {
      buf.append("[" + num(getP1()) + " to " + num(getP2()) + ", length: " + num(length()) + "]");
    }
    return buf.toString();
  }

  public Collection<EndCap> getEndCaps() {
    Collection<EndCap> ret = new HashSet<EndCap>();
    ret.add(new EndCap(this, EndCap.WhichEnd.Start));
    ret.add(new EndCap(this, EndCap.WhichEnd.End));
    return ret;
  }

  public Segment.Type getType() {
    return type;
  }

  public Pt getP1() {
    return p1;
  }

  public Pt getP2() {
    return p2;
  }

  public Line asLine() {
    return new Line(getP1(), getP2());
  }

  public double length() {
    double ret = 0;
    if (type == Segment.Type.Line) {
      ret = getP1().distance(getP2());
    } else if (type == Segment.Type.Curve) {
      ret = asSpline().length();
    }
    return ret;
  }

  public double ctrlPointLength() {
    doPara();
    double ret = 0;
    for (int i = 0; i < paraPoints.size() - 1; i++) {
      ret += paraPoints.get(i).distance(paraPoints.get(i + 1));
    }
    return ret;
  }

  /**
   * On exit, the variables that start with 'para' are set, most importantly, paraPoints. their
   * locations depend on the segment's authoritative state. For lines, ellipses, and splines, this
   * is simply p1 and p2. If a segment type can't be deformed by a simple affine transform, it
   * should override this.
   */
  protected void doPara() {
    if (paraP1Loc == null || paraP2Loc == null || paraPoints == null
        || !paraP1Loc.isSameLocation(p1) || !paraP2Loc.isSameLocation(p2)) {
      paraP1Loc = p1.copyXYT();
      paraP2Loc = p2.copyXYT();
      paraPoints = new ArrayList<Pt>();
      Vec v = new Vec(p1, p2).getUnitVector();
      double fullLen = p1.distance(p2);
      Vec vNorm = v.getNormal().getFlip();
      for (int i = 0; i < pri.length; i++) {
        double priComponent = pri[i] * fullLen;
        double altComponent = alt[i] * fullLen;
        Pt spot = p1.getTranslated(v, priComponent);
        spot = spot.getTranslated(vNorm, altComponent);
        paraPoints.add(i, spot);
      }
      paraPoints.set(0, p1);
      paraPoints.set(paraPoints.size() - 1, p2);
    }
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
      default:
        bug("Unknown segment type!");
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
    doPara();
    double roughLength = 0;
    for (int i = 0; i < paraPoints.size() - 1; i++) {
      roughLength = roughLength + paraPoints.get(i).distance(paraPoints.get(i + 1));
    }
    int numSteps = (int) ceil(min(roughLength / 100, 10));
    List<Pt> paraPointList = new ArrayList<Pt>();
    for (Pt pt : paraPoints) {
      paraPointList.add(pt);
    }
    Sequence spline = Functions.makeNaturalSpline(numSteps, paraPointList);
    return spline;
  }

  public List<Pt> asPolyline() {
    doPara();
    return paraPoints;
  }

  public boolean isNear(Pt point, double dist) {
    boolean ret = false;
    Pt where = getNearestPoint(point);
    //    if (type == Segment.Type.Line) {
    //      where = Functions.getNearestPointOnLine(point, asLine());
    //    } else if (type == Segment.Type.Curve || type == Segment.Type.EllipticalArc) {
    //      doPara();
    //      where = Functions.getNearestPointOnPolyline(point, paraPoints);
    //    }
    if (where != null && where.distance(point) <= dist) {
      ret = true;
    }
    return ret;
  }

  /**
   * Get the parameter that places this point along the parametric representation. 0 is coincident
   * with p1, while 1 is coincident with p2.
   * 
   * @param pt
   * @return
   */
  public double getPointParam(Pt pt) {
    double ret = Double.MAX_VALUE;
    doPara();
    List<Pt> pts = getPointList();
    double runningDist = 0;
    double foundAt = 0;
    for (int i = 0; i < pts.size() - 1; i++) {
      if (foundAt == 0) {
        double u = Functions.getPointSegmentParam(pt, pts.get(i), pts.get(i + 1));
        if (u >= 0.0 && u <= 1.0) {
          foundAt = runningDist + u * pts.get(i).distance(pts.get(i + 1));
          bug("Found correct segment. runningDist= " + num(runningDist) + ", u=" + num(u)
              + ", foundAt=" + num(foundAt));
        }
      }
      runningDist = runningDist + pts.get(i).distance(pts.get(i + 1));
    }
    ret = foundAt / runningDist;
    bug("Returning " + num(foundAt) + " / " + num(runningDist) + " = " + num(ret));
    return ret;
  }

  public Pt getNearestPoint(Pt pt) {
    Pt where = null;
    if (type == Segment.Type.Line) {
      where = Functions.getNearestPointOnLine(pt, asLine(), true);
    } else if (type == Segment.Type.Curve || type == Segment.Type.EllipticalArc) {
      doPara();
      where = Functions.getNearestPointOnPolyline(pt, paraPoints);
    } else if (type == Segment.Type.Dot) {
      where = getP1().copyXYT();
      where.setDouble("r", 0);
    } else {
      bug("getNearestPoint not implementd for type " + type);
    }
    return where;
  }

  public void replace(Pt capPt, Pt spot) {
    if (capPt == p1) {
      p1 = spot;
    }
    if (capPt == p2) {
      p2 = spot;
    }
  }

  /**
   * Returns a list of points that define the geometry of this segment. For lines this is simply two
   * points. For splines and elliptical arcs there might be many more.
   */
  public List<Pt> getPointList() {
    List<Pt> ret = new ArrayList<Pt>();
    if (type == Segment.Type.Line) {
      ret.add(getP1());
      ret.add(getP2());
    } else {
      ret.addAll(asSpline().getPoints()); // changed from asPolyline.
    }

    return ret;
  }

  public Segment copy() {
    List<Pt> copiedPoints = new ArrayList<Pt>();
    doPara();
    for (Pt pt : paraPoints) {
      copiedPoints.add(pt.copyXYT());
    }
    SegmentDelegate sd = new SegmentDelegate(this.ink, copiedPoints, termA, termB, type);
    return new Segment(sd);
  }

  public Area getFuzzyArea(double fuzzyFactor) {
    Area fuzzy = new Area();
    List<Pt> pl = getPointList();
    for (int i = 0; i < pl.size() - 1; i++) {
      Pt a = pl.get(i);
      Pt b = pl.get(i + 1);
      Shape s = ShapeFactory.getFuzzyRectangle(a, b, fuzzyFactor);
      fuzzy.add(new Area(s));
    }
    return fuzzy;
  }

  public boolean involves(Pt p) {
    return p == getP1() || p == getP2();
  }

  public Pt[] getEndpointArray() {
    return new Pt[] {
        p1, p2
    };
  }

  public Pt getVisualMidpoint() {
    doPara();
    List<Pt> bigList = asPolyline();
    int midIdx = bigList.size() / 2;
    return bigList.get(midIdx);
  }

  /**
   * Tells you if calls to getEndCaps() and getStart/EndDir() will be meaningful. By default all
   * segments do---override this and return false if your segment doesn't use this.
   * 
   * @return
   */
  public boolean hasEndCaps() {
    return true;
  }

  public Shape asArc() {
    bug("This sould never be called. override it.");
    return null;
  }

  /**
   * If cursor is p1 or p2, this returns the other one. For singular segments it returns null;
   * 
   * @param input
   *          should be either p1 or p2
   * @return p1 or p2, whichever the input is not.
   */
  public Pt getPointOpposite(Pt input) {
    Pt ret = null;
    if (getP1() == input && getP2() != input) {
      ret = getP2();
    } else if (getP2() == input && getP1() != input) {
      ret = getP1();
    }
    return ret;
  }

}
