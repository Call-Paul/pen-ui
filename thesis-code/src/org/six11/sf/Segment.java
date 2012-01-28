package org.six11.sf;

import static java.lang.Math.ceil;
import static java.lang.Math.min;
import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

import java.awt.Shape;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.six11.sf.SegmentDelegate.Type;
import org.six11.util.gui.shape.ShapeFactory;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.Vec;

public class Segment implements HasFuzzyArea {

  private SegmentDelegate d;

  public Segment(SegmentDelegate delegate) {
    this.d = delegate;
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

  public Collection<EndCap> getEndCaps() {
    return d.getEndCaps();
  }

  public int getId() {
    return d.getId();
  }

  public Type getType() {
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

}
