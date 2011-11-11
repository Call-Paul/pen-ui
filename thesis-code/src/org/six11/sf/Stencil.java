package org.six11.sf;

import static org.six11.util.Debug.bug;

import java.awt.Shape;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.Set;

import org.six11.util.gui.BoundingBox;
import org.six11.util.pen.Pt;

public class Stencil {

  Pt origin;
  BoundingBox bounds;
  Set<StructuredInk> structuredInk;
  Shape shape;

  public Stencil(Set<StructuredInk> struc) {
    bug("TODO: Stencil using old structured ink stuff");
//    this.structuredInk = struc;
//    BoundingBox bb = getBoundingBox();
//    for (StructuredInk ink : struc) {
//      ink.move(-bb.getMinX(), -bb.getMinY());
//    }
//    bounds = null;
  }

  public void setOrigin(double x, double y) {
    this.origin = new Pt(x, y);
  }

  public double getWidth() {
    return getBoundingBox().getWidth();
  }

  public Shape getShape() {
    Path2D path = new Path2D.Double();
    if (shape == null) {
      boolean first = true;
      for (StructuredInk struc : structuredInk) {
        path.append(struc.getPath(), first);
        first = false;
      }
      shape = new GeneralPath(path);
    }
//    debugShapePoints(shape);
    return shape;
  }

//  private void debugShapePoints(Shape shape) {
//    PathIterator pi = shape.getPathIterator(null);
//    double[] coords = new double[6];
//    int part = 1;
//    while (!pi.isDone()) {
//      int type = pi.currentSegment(coords);
//      String typeStr = getPITypeStr(type);
//      bug("Coords for part " + part + " (type " + typeStr + "): " + num(coords[0]) + ", "
//          + num(coords[1]));
//      part++;
//      pi.next();
//    }
//  }

  public static String getPITypeStr(int type) {
    switch (type) {
      case PathIterator.SEG_CLOSE: return "close";
      case PathIterator.SEG_CUBICTO: return "cubic_to";
      case PathIterator.SEG_LINETO: return "line_to";
      case PathIterator.SEG_MOVETO: return "move_to";
      case PathIterator.SEG_QUADTO: return "quad_to";
      default: return "unknown";
    }
  }

  public BoundingBox getBoundingBox() {
    if (bounds == null) {
      bounds = new BoundingBox();
      for (StructuredInk ink : structuredInk) {
        bounds.add(ink.getArea().getBounds2D());
      }
    }
    return bounds;
  }

  /**
   * Returns a bounding box that is translated to the 'origin' (the top-left corner of this stencil)
   * 
   * @return
   */
  public BoundingBox getCutfileBoundingBox() {
    BoundingBox bb = getBoundingBox().copy();
    bb.translate(origin.x, origin.y);
    return bb;
  }

  public Pt getOrigin() {
    return origin;
  }

}
