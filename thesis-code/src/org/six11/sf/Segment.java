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

  //  List<Pt> points; // going to replace this soon

  private Pt p1, p2; // start/end points. these can be moved around externally

  // parametric points: they are dependent on p1 and p2, so internal code here
  // might have to adjust them if p1 or p2 change.
  private double[] pri; // parametric points primary coordinate, along vector from p1 to p2
  private double[] alt; // parametric points secondary coordinate, orthogonal to the above

  // transient variables that describe the parametric point sequence for the current values
  // of p1 and p2.
  private transient Pt paraP1Loc = null;
  private transient Pt paraP2Loc = null;
  private transient List<Pt> paraPoints = null;
  private transient Shape paraShape = null;

  Type type;
  //  Sequence spline;
  Ink ink;
  boolean termA, termB;
  
  protected Segment() {
    // ensure subclass calls init();
  }

  public Segment(Ink ink, List<Pt> points, boolean termA, boolean termB) {
    this(ink, points, termA, termB, Type.Unknown);
    //    this.ink = ink;
    //    this.points = points;
    //    this.p0 = points.get(0);
    //    this.p1 = points.get(points.size() - 1);
    //    this.pri = new double[points.size()];
    //    this.alt = new double[points.size()];
    //    calculateParameters(points);
    //    this.termA = termA;
    //    this.termB = termB;
    //    for (Pt pt : points) {
    //      if (pt.getTime() == 0) {
    //        Debug.stacktrace("point has zero time stamp!", 7);
    //      }
    //    }
    //    this.type = Type.Unknown;
    //    id = ID_COUNTER++;
  }

  public Segment(Ink ink, List<Pt> points, boolean termA, boolean termB, Type t) {
    init(ink, points, termA, termB, t);
  }
  
  protected final void init(Ink ink, List<Pt> points, boolean termA, boolean termB, Type t) {
    this.ink = ink;
    this.p1 = points.get(0);
    this.p2 = points.get(points.size() - 1);
    this.pri = new double[points.size()];
    this.alt = new double[points.size()];
    calculateParameters(points);
    this.termA = termA;
    this.termB = termB;
    this.type = t;
    id = ID_COUNTER++;
  }

  private final void calculateParameters(List<Pt> points) {
    Vec v = new Vec(p1, p2);
    double vMag = v.mag();
    Line line = new Line(p1, p2);
    for (int i = 0; i < points.size(); i++) {
      if (points.get(i).isSameLocation(p1)) {
        pri[i] = 0;
        alt[i] = 0;
      } else {
        Pt ix = Functions.getNearestPointOnLine(points.get(i), line, true); // retains the 'r' double value
        int whichSide = Functions.getPartition(points.get(i), line);
        double dist = ix.distance(points.get(i)) * whichSide;
        pri[i] = ix.getDouble("r");
        alt[i] = dist / vMag;
      }
      //      System.out.println(num(pri[i]) + "\t" + num(alt[i]));
    }
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
    return p1; //points.get(0);
  }

  public Pt getP2() {
    return p2; // points.get(points.size() - 1);
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
    doPara();
    double ret = 0;
    for (int i = 0; i < paraPoints.size() - 1; i++) {
      ret += paraPoints.get(i).distance(paraPoints.get(i + 1));
    }
    return ret;
  }

  private void doPara() {
    if (paraP1Loc == null || paraP2Loc == null || paraPoints == null
        || !paraP1Loc.isSameLocation(p1) || !paraP2Loc.isSameLocation(p2)) {
      paraP1Loc = p1.copyXYT();
      paraP2Loc = p2.copyXYT();
      paraPoints = new ArrayList<Pt>();
      Vec v = new Vec(p1, p2).getUnitVector();
      double fullLen = p1.distance(p2);
      Vec vNorm = v.getNormal().getFlip();
      for (int i=0; i < pri.length; i++) {
        double priComponent = pri[i] * fullLen;
        double altComponent = alt[i] * fullLen;
        Pt spot = p1.getTranslated(v, priComponent);
        spot = spot.getTranslated(vNorm, altComponent);
        paraPoints.add(i, spot);
      }
      paraPoints.set(0, p1);
      paraPoints.set(paraPoints.size() - 1, p2);
      paraShape = null;
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
    Pt where = null;
    if (type == Segment.Type.Line) {
      where = Functions.getNearestPointOnLine(point, asLine());
    } else if (type == Segment.Type.Curve || type == Segment.Type.EllipticalArc) {
      doPara();
      where = Functions.getNearestPointOnPolyline(point, paraPoints);
    }
    if (where != null && where.distance(point) <= dist) {
      ret = true;
    }
    return ret;
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
    if (type == Type.Line) {
      ret.add(getP1());
      ret.add(getP2());
    } else {
      ret.addAll(asPolyline());
    }

    return ret;
  }

  public Segment copy() {
    List<Pt> copiedPoints = new ArrayList<Pt>();
    doPara();
    for (Pt pt : paraPoints) {
      copiedPoints.add(pt.copyXYT());
    }
    Segment ret = new Segment(this.ink, copiedPoints, termA, termB, type);
    return ret;
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
    return new Pt[] { p1, p2 };
  }

  public Pt getVisualMidpoint() {
    doPara();
    List<Pt> bigList = asPolyline();
    int midIdx = bigList.size() / 2;
    return bigList.get(midIdx);
  }

}
