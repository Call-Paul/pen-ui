package org.six11.sf;

import static org.six11.util.Debug.bug;

import java.awt.Shape;
import java.util.List;

import org.six11.util.gui.shape.ShapeFactory;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.RotatedEllipse;
import org.six11.util.pen.Vec;

public class EllipseSegment extends SegmentDelegate {

  RotatedEllipse ellie;
  private transient Shape cachedShape;
  private transient double cachedY;
  private transient double cachedX;
  private transient double cachedA;
  private transient double cachedB;


  public EllipseSegment(Ink ink, List<Pt> points) {
    this.ellie = Functions.createEllipse(points, false);
    this.ink = ink;
    this.p1 = points.get(0);
    this.p2 = p1;
    this.type = Segment.Type.Ellipse;
  }

  public boolean hasEndCaps() {
    return false;
  }

  public Shape asEllipse() {
    Shape ret = null;
    if (cachedA == ellie.getMajorRadius() && cachedB == ellie.getMinorRadius()
        && cachedX == ellie.getCentroid().x && cachedY == ellie.getCentroid().y
        && cachedShape != null) {
      ret = cachedShape;
    } else {
      double bigR = ellie.getMajorRadius();
      double closeToCircumference = bigR * 2.0 * Math.PI;
      int numSteps = (int) closeToCircumference;
      ret = new ShapeFactory.RotatedEllipseShape(ellie, numSteps);
      cachedA = ellie.getMajorRadius();
      cachedB = ellie.getMinorRadius();
      cachedX = ellie.getCentroid().x;
      cachedY = ellie.getCentroid().y;
      cachedShape = ret;
    }
    return ret;
  }

  public List<Pt> getPointList() {
    Shape myShape = asEllipse();
    return ShapeFactory.makePointList(myShape.getPathIterator(null));
  }

  public List<Pt> asPolyline() {
    return getPointList();
  }

  public RotatedEllipse getEllipse() {
    return ellie;
  }

}
