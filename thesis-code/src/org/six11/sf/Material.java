package org.six11.sf;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.imgscalr.Scalr;
import org.six11.util.gui.BoundingBox;
import org.six11.util.gui.shape.ShapeFactory;
import org.six11.util.pen.Pt;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

// import static org.six11.util.Debug.num;

public class Material {

  public static double INCH_PER_PX = 1.0 / 72.0;
  public static double CM_PER_PX = 1.0 / 37.795275591;
  public static double M_PER_PX = 1.0 / 3779.527559055;
  public static double MM_PER_PX = 1.0 / 3.779527559;

  public enum Units {
    Inch, Centimeter, Millimeter, Meter, Pixel,
  }

  private Units units;
  private BoundingBox materialBB;
  private BoundingBox stencilBB;
  Set<Shape> unpositionedShapes;
  private Map<Shape, Pt> shapes;
  private BufferedImage big;
  private BufferedImage small;

  public Material(Units units, double width, double height) {
    this.units = units;
    this.unpositionedShapes = new HashSet<Shape>();
    this.materialBB = new BoundingBox();
    this.stencilBB = new BoundingBox();
    this.shapes = new HashMap<Shape, Pt>();
    materialBB.add(0, 0);
    materialBB.add(new Pt(toPixels(units, width), toPixels(units, height)));
    bug("Material bounds: " + materialBB);
  }

  public BufferedImage getBigImage() {
    return big;
  }

  public BufferedImage getSmallImage(int w, int h) {
    BufferedImage ret = small;
    if (small == null && big != null) {
      small = Scalr.resize(big, w, h);
    }
    return ret;
  }

  /**
   * Converts the input value (in Units) to pixels.
   */
  public static double toPixels(Units units, double v) {
    double ret = 0;
    switch (units) {
      case Centimeter:
        ret = v / CM_PER_PX;
        break;
      case Inch:
        ret = v / INCH_PER_PX;
        break;
      case Meter:
        bug("toPixels not implemented for " + units);
        break;
      case Millimeter:
        bug("toPixels not implemented for " + units);
        break;
      case Pixel:
        ret = v;
        break;
      default:
        bug("Can't convert unit type " + units + " to pixels.");
    }
    return ret;
  }

  /**
   * Converts the input value (in pixels) to the given Unit value. (e.g. params Units.Inch, 72.0
   * returns 1.0 because 72 pixels equal one inch.)
   */
  public static double fromPixels(Units units, double v) {
    double ret = 0.0;
    switch (units) {
      case Centimeter:
        ret = CM_PER_PX * v;
        break;
      case Inch:
        ret = INCH_PER_PX * v;
        break;
      case Meter:
        bug("fromPixels not implemented for " + units);
        break;
      case Millimeter:
        bug("fromPixels not implemented for " + units);
        break;
      case Pixel:
        ret = v;
        break;
      default:
        bug("Can't convert from pixels to unit type " + units);
    }
    return ret;
  }

  public void addStencil(Shape shape) {
    unpositionedShapes.add(shape);
    big = null;
    small = null;
  }

  public void layoutStencils() {
    stencilBB = new BoundingBox();
    stencilBB.add(0, 0);
    double cursorX = 0;
    double cursorY = 0;
    double maxY = 0;
    for (Shape s : unpositionedShapes) {
      BoundingBox sBB = new BoundingBox(s.getBounds2D());
      if (cursorX + sBB.getWidth() > materialBB.getMaxX()) {
        cursorX = 0;
        cursorY = maxY;
      }
      sBB.translateToOrigin();
      sBB.translate(cursorX, cursorY);
      if (materialBB.getRectangle().contains(sBB.getRectangle())) {
        place(s, cursorX, cursorY);
        cursorX += sBB.getWidth();
        maxY = Math.max(maxY, cursorY + sBB.getHeight());
      } else {
        bug("Can't put " + sBB + " inside " + materialBB);
      }
    }
    big = new BufferedImage(materialBB.getWidthInt(), materialBB.getHeightInt(),
        BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = (Graphics2D) big.getGraphics();
    drawStencils(g, Color.BLACK, "fill");
  }

  public void drawStencils(Graphics2D g, Color color, String mode) {
    g.setColor(Color.BLACK);
    for (Map.Entry<Shape, Pt> shapeAndLoc : shapes.entrySet()) {
      Shape shape = shapeAndLoc.getKey();
      Pt where = shapeAndLoc.getValue();
      Rectangle2D shapeBounds = shape.getBounds2D();
      double dx = where.getX() - shapeBounds.getMinX();
      double dy = where.getY() - shapeBounds.getMinY();
      AffineTransform before = g.getTransform();
      g.translate(dx, dy);
      if (mode.equals("fill")) {
        g.fill(shape);
      } else {
        g.draw(shape);
      }
      g.setTransform(before);
    }
  }

  private void place(Shape s, double cursorX, double cursorY) {
    bug("cursorX: " + num(cursorX) + ", cursorY: " + num(cursorY));
    Rectangle2D shapeBounds = s.getBounds2D();
    double dx = cursorX - shapeBounds.getMinX();
    double dy = cursorY - shapeBounds.getMinY();
    Rectangle2D trBounds = ShapeFactory.getTranslated(shapeBounds, dx, dy);
    stencilBB.add(trBounds);
    shapes.put(s, new Pt(cursorX, cursorY));
  }

  public int countStencils() {
    return unpositionedShapes.size();
  }

  public void clear() {
    big = null;
    small = null;
    shapes.clear();
    stencilBB = new BoundingBox();
    unpositionedShapes.clear();
  }

  public BoundingBox getCutBoundingBox() {
    return stencilBB;
  }
}
