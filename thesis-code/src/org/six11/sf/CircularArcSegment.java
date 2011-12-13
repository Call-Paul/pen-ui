package org.six11.sf;

import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;

import org.six11.util.gui.shape.ShapeFactory;
import org.six11.util.pen.CircleArc;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

public class CircularArcSegment extends Segment {

  private Vec centerParameterization;
  private int arcSide;

  public CircularArcSegment(Ink ink, List<Pt> points, Pt initialCenter, double initialRadius,
      boolean termA, boolean termB) {
    init(ink, points, termA, termB, Type.CircularArc);
    // p1 and p2 are valid after init is called. just need to move them to be on the circle
    CircleArc arc = new CircleArc(initialCenter, initialRadius);
    Pt p1ix = Functions.getIntersectionPoint(arc, new Line(p1, initialCenter));
    Pt p2ix = Functions.getIntersectionPoint(arc, new Line(p2, initialCenter));
    p1.setLocation(p1ix);
    p2.setLocation(p2ix);
    // now need to determine which side the center is on, and what the parametric coordinate of the center is.
    Vec v = new Vec(p1, p2);
    double vMag = v.mag();
    Line line = new Line(p1, p2);
    Pt roughlyArcMid = points.get(points.size() / 2);
    Pt circleIntersectionPt = Functions.getIntersectionPoint(arc, new Line(roughlyArcMid,
        initialCenter));
    arcSide = Functions.getPartition(circleIntersectionPt, p1, p2);
    centerParameterization = calculateParameterForPoint(vMag, line, initialCenter);
  }

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
      //      paraShape = null;
      //      paraLength = Functions.getPathLength(paraPoints, 0, paraPoints.size() - 1);
    }
  }

  private Pt getCenter() {
    Pt ret = null;
    Vec chord = new Vec(getP1(), getP2()).getUnitVector();
    if (chord.isZero()) {
      ret = getP1().copyXYT();
    } else {
      double fullLen = getP1().distance(getP2());
      Vec vNorm = chord.getNormal().getFlip();
      double cx = centerParameterization.getX() * fullLen;
      double cy = centerParameterization.getY() * fullLen;
      Pt spot = getP1().getTranslated(chord, cx);
      spot = spot.getTranslated(vNorm, cy);
      ret = spot;
    }
    return ret;
  }

  /**
   * Returns a vector tangent to the circle at p2, facing inwards.
   */
  public Vec getEndDir() {
    // TODO: this is almost certainly wrong. have to look at where the center is---left or right of the chord?
    Pt currentCenter = getCenter();
    Vec fromCenter = new Vec(currentCenter, getP2()).getUnitVector();
    return fromCenter.getNormal();
  }

  public Vec getStartDir() {
    // TODO: this is almost certainly wrong
    Pt currentCenter = getCenter();
    Vec fromCenter = new Vec(currentCenter, getP1()).getUnitVector();
    return fromCenter.getNormal();
  }

  public Shape asArc() {
    doPara();
    Pt c = getCenter();
    double r = c.distance(p1);
    Pt chordMid = Functions.getMean(getP1(), getP2());
    Line line = new Line(c, chordMid);
    CircleArc arc = new CircleArc(c, r);
    Pt[] ix = Functions.getIntersectionPoints(arc, line);
    int centerSide = (centerParameterization.getY() < 0) ? -1 : 1;
    int which;
    if (arcSide == centerSide) {
      which = ix[0].distance(c) < ix[1].distance(c) ? 0 : 1;
    } else {
      which = ix[0].distance(chordMid) < ix[1].distance(chordMid) ? 0 : 1;
    }
    Pt mid = ix[which];
    return ShapeFactory.makeArc(getP1(), mid, getP2());
  }

}
