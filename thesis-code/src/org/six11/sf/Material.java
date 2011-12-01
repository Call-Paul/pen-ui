package org.six11.sf;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.imgscalr.Scalr;
import org.six11.util.gui.BoundingBox;
import org.six11.util.gui.shape.ShapeFactory;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Pt;

import com.sun.xml.internal.ws.wsdl.writer.document.Part;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

// import static org.six11.util.Debug.num;

public class Material {

  public static double PX_PER_INCH = 1.0 / 72.0;

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
    materialBB.add(new Pt(toPixels(width), toPixels(height)));
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
  private double toPixels(double v) {
    double ret = 0;
    switch (units) {
      case Centimeter:
        break;
      case Inch:
        ret = v / PX_PER_INCH;
        break;
      case Meter:
        break;
      case Millimeter:
        break;
      case Pixel:
        ret = v;
        break;
      default:
        bug("Can't convert unit type " + units + " to pixels.");
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
    int stencilCount = 0;
    for (Shape s : unpositionedShapes) {
      stencilCount++;
      bug("typewriter algo for stencil " + stencilCount);
      BoundingBox sBB = new BoundingBox(s.getBounds2D());
      if (cursorX + sBB.getWidth() > materialBB.getMaxX()) {
        cursorX = 0;
        cursorY = maxY;
        bug("exceeded stencil X boundary. zipping back to x = 0, y = " + num(cursorY));
      }
      sBB.translateToOrigin();
      sBB.translate(cursorX, cursorY);
      if (materialBB.getRectangle().contains(sBB.getRectangle())) {
        bug("place stencil in rectangle.");
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
