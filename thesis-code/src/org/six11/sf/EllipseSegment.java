package org.six11.sf;

import static org.six11.util.Debug.bug;

import java.awt.Shape;
import java.util.List;

import org.six11.util.gui.shape.ShapeFactory;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.RotatedEllipse;

public class EllipseSegment extends SegmentDelegate {

  RotatedEllipse ellie;
  
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
    return new ShapeFactory.RotatedEllipseShape(ellie, 30);
  }

  public List<Pt> getPointList() {
    Shape myShape = asEllipse();
    return ShapeFactory.makePointList(myShape.getPathIterator(null));
  }
  
  public List<Pt> asPolyline() {
    return getPointList();
  }
}
