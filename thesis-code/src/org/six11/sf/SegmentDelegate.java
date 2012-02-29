package org.six11.sf;

import static java.lang.Math.ceil;
import static java.lang.Math.min;
import static org.six11.util.Debug.num;
import static org.six11.util.Debug.bug;

import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.six11.util.Debug;
import org.six11.util.data.Lists;
import org.six11.util.gui.BoundingBox;
import org.six11.util.gui.shape.Circle;
import org.six11.util.gui.shape.ShapeFactory;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.RotatedEllipse;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.Vec;

public class SegmentDelegate implements HasFuzzyArea {

  protected Pt p1, p2; // start/end points. these can be moved around externally

  // parametric points: they are dependent on p1 and p2, so internal code here
  // might have to adjust them if p1 or p2 change.
  protected double[] pri; // parametric points primary coordinate, along vector from p1 to p2
  protected double[] alt; // parametric points secondary coordinate, orthogonal to the above

  // what kind of segment do we represent? Line? Arc? Blob? etc.
  protected Segment.Type type;

  // transient variables that describe the parametric point sequence for the current values
  // of p1 and p2.
  protected transient Pt paraP1Loc = null;
  protected transient Pt paraP2Loc = null;
  protected transient List<Pt> paraPoints = null;
  protected transient List<Pt> deformedPoints = null;
  protected transient Ink ink;

  protected SegmentDelegate() {
    // ensure subclass calls init();
  }

  public SegmentDelegate(Ink ink, List<Pt> points) {
    this(ink, points, Segment.Type.Unknown);
  }

  public SegmentDelegate(Ink ink, List<Pt> points, Segment.Type t) {
    init(ink, points, t);
  }

  protected final void init(Ink ink, List<Pt> points, Segment.Type t) {
    this.ink = ink;
    this.p1 = points.get(0);
    this.p2 = points.get(points.size() - 1);
    calculateParameters(points);
    this.type = t;
  }

  protected final void init(Ink ink, Pt p1, Pt p2, double[] primaryParaCoordinates,
      double[] secondaryParaCoordinates, Segment.Type t) {
    this.ink = ink;
    this.p1 = p1;
    this.p2 = p2;
    this.pri = primaryParaCoordinates;
    this.alt = secondaryParaCoordinates;
    doPara();
    this.type = t;
    if (p1 == null || p2 == null) {
      Debug.stacktrace("p1 or p2 is null for " + t + ": " + p1 + ", " + p2, 8);
    }
  }

  public void calculateParameters(List<Pt> points) {
    if (points == null) {
      bug("Warning: points input is null for " + bugStr());
    }
    if (points.isEmpty()) {
      bug("Warning: empty point list provided for " + bugStr());
    }
    this.pri = new double[points.size()];
    this.alt = new double[points.size()];
    paraPoints = null;
    Pt start = points.get(0);
    Pt end = points.get(points.size() - 1);
    if (p1 == null) {
      bug("Warning: p1 is null for " + bugStr());
    }
    if (p2 == null) {
      bug("Warning: p2 is null for " + bugStr());
    }
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
        Vec toTarget = Segment.calculateParameterForPoint(vMag, line, target);
        pri[i] = toTarget.getX();
        alt[i] = toTarget.getY();
      }
    }
    doPara();
  }

  public Ink getOriginalInk() {
    return ink;
  }

  public boolean isSingular() {
    return getP1().isSameLocation(getP2());
  }

  public boolean isClosed() {
    return (type == Segment.Type.Ellipse || type == Segment.Type.Blob || type == Segment.Type.Circle);
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
    buf.append(getTypeChar());
    if (SketchBook.n(getP1()) != null && SketchBook.n(getP2()) != null) {
      buf.append("[" + SketchBook.n(getP1()) + " to " + SketchBook.n(getP2()) + ", length: "
          + num(length()) + "]");
    } else {
      buf.append("[" + num(getP1()) + " to " + num(getP2()) + ", length: " + num(length()) + "]");
    }
    return buf.toString();
  }

  public String bugStr() {
    StringBuilder buf = new StringBuilder();
    buf.append(getTypeChar());
    buf.append("[");
    buf.append(Segment.bugStr(getP1()) + " to " + Segment.bugStr(getP2()));
    buf.append("]");
    return buf.toString();
  }

  public char getTypeChar() {
    if (type != null) {
      switch (getType()) {
        case Curve:
          return 'S';
        case EllipticalArc:
          return 'E';
        case Line:
          return 'L';
        case CircularArc:
          return 'C';
        case Blob:
          return 'B';
        case Ellipse:
          return 'I';
        case Circle:
          return 'R';
        default:
        case Unknown:
          bug("Unknown segment type: " + getType());
          return '?';
      }
    } else {
      return '?';
    }
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
      if (isClosed()) {
        bug("doing para for " + getType() + ". this is a bug");
      }
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
    } else if (isSingular()) {
      bug("doPara() called for singular segment. this is bad. stacktrace follows.");
      Debug.stacktrace("you should not get here.", 8);
    }
  }

  public Vec getStartDir() {
    return getTangent(getP1());
  }

  public Vec getEndDir() {
    return getTangent(getP2()).getFlip();
  }

  /**
   * Return a tangent vector at the given point. The target point should be equal (using ==) to one
   * of the points returned by getPointList(). The vector is always defined by points P[i] to P[j]
   * where i < j. In other words, the return value always points towards p2.
   * 
   * @param target
   * @return
   */
  public Vec getTangent(Pt target) {
    Vec ret = null;
    List<Pt> pts = getPointList();
    int idx = pts.indexOf(target);
    if (idx < 0) {
      bug("Error: point " + num(target) + " does not appear in " + num(pts, " "));
    } else {
      int prevIdx = idx - 1;
      int nextIdx = idx + 1;
      if (prevIdx < 0) {
        nextIdx = 1;
        prevIdx = 0;
      }
      if (nextIdx >= pts.size()) {
        nextIdx = pts.size() - 1;
        prevIdx = nextIdx - 1;
      }
      ret = new Vec(pts.get(prevIdx), pts.get(nextIdx)).getUnitVector();
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
    spline.replace(0, getP1());
    spline.replace(spline.size() - 1, getP2());
    //    bug("returning spline of " + spline.size() + " points for " + getType() + ": " + num(spline.getPoints(), " "));
    return spline;
  }

  public List<Pt> asPolyline() {
    doPara();
    return paraPoints;
  }

  public boolean isNear(Pt point, double dist) {
    boolean ret = false;
    Pt where = getNearestPoint(point);
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
      //      where = Functions.getNearestPointOnLine(pt, asLine(), true);
      where = Functions.getNearestPointWithinSegment(pt, asLine(), true);
    } else if (type == Segment.Type.Curve || type == Segment.Type.EllipticalArc) {
      doPara();
      where = Functions.getNearestPointOnPolyline(pt, paraPoints);
    } else if (type == Segment.Type.Dot) {
      where = getP1().copyXYT();
      where.setDouble("r", 0);
    } else if (type == Segment.Type.Circle || type == Segment.Type.Ellipse
        || type == Segment.Type.Blob) {
      where = Functions.getNearestPointOnPolyline(pt, getPointList());
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

  public Set<Pt> getPoints() {
    Set<Pt> ret = new HashSet<Pt>();
    ret.add(getP1());
    if (!isSingular()) {
      ret.add(getP2());
    }
    return ret;
  }

  public Segment copy() {
    List<Pt> copiedPoints = new ArrayList<Pt>();
    doPara();
    for (Pt pt : paraPoints) {
      copiedPoints.add(pt.copyXYT());
    }
    SegmentDelegate sd = new SegmentDelegate(this.ink, copiedPoints, type);
    //    SegmentDelegate sd = new SegmentDelegate(this.ink, copiedPoints, termA, termB, type);
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
    if (bigList.size() > 2) {
      int midIdx = bigList.size() / 2;
      return bigList.get(midIdx);
    } else if (bigList.size() == 2) {
      return Functions.getMean(getP1(), getP2());
    } else {
      return getP1().copyXYT();
    }
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
    Debug.stacktrace("This sould never be called. override it.", 8);
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

  public Shape asEllipse() {
    Debug.stacktrace("This sould never be called. override it.", 8);
    return null;
  }

  public RotatedEllipse getEllipse() {
    Debug.stacktrace("This sould never be called. override it.", 8);
    return null;
  }

  public Shape asCircle() {
    Debug.stacktrace("This sould never be called. override it.", 8);
    return null;
  }

  public Circle getCircle() {
    Debug.stacktrace("This sould never be called. override it.", 8);
    return null;
  }

  public Rectangle2D getBounds() {
    BoundingBox bb = new BoundingBox();
    bb.addAll(asPolyline());
    return bb.getRectangle();
  }

  public boolean isPointOnPath(Pt loc, double slop) {
    boolean ret = false;
    Rectangle2D bounds = getBounds();
    if (bounds.contains(loc)) {
      ret = Functions.isPointOnPolyline(loc, asPolyline(), slop);
    }
    return ret;
  }

  public Collection<Pt> getLatchPoints(Segment other) {
    return Lists.intersect(getPoints(), other.getPoints());
  }

  public JSONObject toJson() throws JSONException {
    Debug.errorOnNull(SketchBook.n(p1), "p1.name");
    Debug.errorOnNull(SketchBook.n(p2), "p2.name");
    Debug.errorOnNull(pri, "pri");
    Debug.errorOnNull(alt, "alt");
    Debug.errorOnNull(type, "type");
    JSONObject ret = new JSONObject();
    ret.put("p1", SketchBook.n(p1));
    ret.put("p2", SketchBook.n(p2));
    ret.put("pri", new JSONArray(pri));
    ret.put("alt", new JSONArray(alt));
    ret.put("type", type);
    return ret;
  }

  public void validate(SketchBook model) {
    Segment seg = model.getSegment(p1, p2);
    Debug.errorOnNull(seg, "seg");
  }

}
