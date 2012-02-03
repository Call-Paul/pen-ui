package org.six11.sf;

import static org.six11.util.Debug.num;

import java.awt.Shape;
import java.awt.geom.Area;
import java.util.Collection;
import java.util.List;

import org.six11.util.gui.shape.Circle;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.RotatedEllipse;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.Vec;

public class Segment implements HasFuzzyArea {

  int id;
  private static int ID_COUNTER = 1;
  
  public static enum Type {
    Line, Curve, Unknown, EllipticalArc, Dot, CircularArc, Ellipse, Blob, Circle
  }

  private SegmentDelegate d;

  public Segment(SegmentDelegate delegate) {
    this.d = delegate;
    id = ID_COUNTER++;
  }

  public int getId() {
    return id;
  }
  
  public final void calculateParameters(List<Pt> points) {
    d.calculateParameters(points);
  }

  public Ink getOriginalInk() {
    return d.getOriginalInk();
  }

  public boolean isSingular() {
    return d.isSingular();
  }
  
  public boolean isClosed() {
    return d.isClosed();
  }

  public List<Pt> storeParaPointsForDeformation() {
    return d.storeParaPointsForDeformation();
  }

  public List<Pt> getDeformedPoints() {
    return d.getDeformedPoints();
  }

  public void clearDeformation() {
    d.clearDeformation();
  }

  public String toString() {
    return d.toString();
  }
  
  public String bugStr() {
    return d.bugStr();
  }

  public Collection<EndCap> getEndCaps() {
    return d.getEndCaps();
  }

  public Segment.Type getType() {
    return d.getType();
  }

  public Pt getP1() {
    return d.getP1();
  }

  public Pt getP2() {
    return d.getP2();
  }

  public Line asLine() {
    return d.asLine();
  }

  public double length() {
    return d.length();
  }

  public double ctrlPointLength() {
    return d.ctrlPointLength();
  }

  /**
   * On exit, the variables that start with 'para' are set, most importantly, paraPoints. their
   * locations depend on the segment's authoritative state. For lines, ellipses, and splines, this
   * is simply p1 and p2. If a segment type can't be deformed by a simple affine transform, it
   * should override this.
   */
  protected void doPara() {
    d.doPara();
  }

  public Vec getStartDir() {
    return d.getStartDir();
  }

  public Vec getEndDir() {
    return d.getEndDir();
  }

  public double getMinAngle(Segment other) {
    return d.getMinAngle(other);
  }

  public Sequence asSpline() {
    return d.asSpline(); // TODO: is this needed any more?
  }

  public List<Pt> asPolyline() {
    return d.asPolyline();
  }

  public boolean isNear(Pt point, double dist) {
    return d.isNear(point, dist);
  }

  public double getPointParam(Pt pt) {
    return d.getPointParam(pt);
  }

  public Pt getNearestPoint(Pt pt) {
    return d.getNearestPoint(pt);
  }

  public void replace(Pt capPt, Pt spot) {
    d.replace(capPt, spot);
  }

  public List<Pt> getPointList() {
    return d.getPointList();
  }

  public Segment copy() {
    return d.copy();
  }

  public Area getFuzzyArea(double fuzzyFactor) {
    return d.getFuzzyArea(fuzzyFactor);
  }

  public boolean involves(Pt p) {
    return d.involves(p);
  }

  public Pt[] getEndpointArray() {
    return d.getEndpointArray();
  }

  public Pt getVisualMidpoint() {
    return d.getVisualMidpoint();
  }

  public boolean hasEndCaps() {
    return d.hasEndCaps();
  }

  public Shape asArc() {
    return d.asArc();
  }


  public Pt getPointOpposite(Pt input) {
    return d.getPointOpposite(input);
  }

  public Ink getInk() {
    return d.ink;
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
  
  public static String bugStr(Pt pt) {
    StringBuilder buf = new StringBuilder();
    String name = SketchBook.n(pt);
    if (name != null) {
      buf.append(name + " ");
    }
    buf.append("(" + num(pt) + ") ");
    return buf.toString();
  }

  public Shape asEllipse() {
    return d.asEllipse();
  }
  
  public RotatedEllipse getEllipse() {
    return d.getEllipse();
  }

  public Shape asCircle() {
    return d.asCircle();
  }
  
  public Circle getCircle() {
    return d.getCircle();
  }

  public boolean isPointOnPath(Pt loc, double slop) {
    return d.isPointOnPath(loc, slop);
  }
  
  public String typeIdStr() {
    return getType() + "-" + getId();
  }
  
  public String typeIdPtStr() {
    StringBuilder buf = new StringBuilder();
    buf.append(typeIdStr());
    buf.append(" (" + SketchBook.n(getP1()));
    if (!isClosed()) {
      buf.append(", " + SketchBook.n(getP2()));
    }
    buf.append(")");
    return buf.toString();
  }
  
}
