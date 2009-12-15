package org.six11.olive;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
//import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

//import com.lowagie.text.Document;
//import com.lowagie.text.DocumentException;
//import com.lowagie.text.FontFactory;
//import com.lowagie.text.Rectangle;
//import com.lowagie.text.pdf.DefaultFontMapper;
//import com.lowagie.text.pdf.PdfContentByte;
//import com.lowagie.text.pdf.PdfTemplate;
//import com.lowagie.text.pdf.PdfWriter;

//import org.six11.slippy.SlippyMachine;
import org.six11.util.Debug;
import org.six11.util.gui.BoundingBox;
import org.six11.util.gui.Components;
import org.six11.util.gui.GenericPathIterator;
import org.six11.util.gui.Strokes;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class DrawingBuffer {

  private BufferedImage img;
  public Dimension defaultSize = new Dimension(400, 400);
  private boolean dirty;

  private List<TurtleOp> turtles;
  private BoundingBox bb;

  public DrawingBuffer() {
    img = new BufferedImage(defaultSize.width, defaultSize.height, BufferedImage.TYPE_INT_ARGB_PRE);
    this.turtles = new ArrayList<TurtleOp>();
  }

  public void addOp(TurtleOp op) {
    turtles.add(op);
    bb = null;
    dirty = true;
  }

  /**
   * This completely resets the graphics buffer and draws the entire turtle sequence.
   */
  public void update() {
    if (dirty) {
      PenState pen = new PenState();
      pen.setColor(Color.BLACK);
      pen.setThickness(2.6f);
      pen.setDown(true);
      bb = new BoundingBox();
      List<FilledRegion> regions = new ArrayList<FilledRegion>();
      AffineTransform xform = new AffineTransform();
      List<Point2D> points = new ArrayList<Point2D>();
      for (TurtleOp turtle : turtles) {
        xform = turtle.go(xform, pen, bb, null, regions, points);
      }
      img = new BufferedImage(bb.getWidthInt(), bb.getHeightInt(), BufferedImage.TYPE_INT_ARGB_PRE);
      Graphics2D g = img.createGraphics();
      Components.antialias(g);
      g.setTransform(AffineTransform.getTranslateInstance(-bb.getX(), -bb.getY()));
      g.setClip(bb.getRectangleLoose());
      for (FilledRegion region : regions) {
        g.setColor(region.getColor());
        GeneralPath path = new GeneralPath();
        path.append(region.getPathIterator(), true);
        g.fill(path);
      }
      xform = new AffineTransform();
      for (TurtleOp turtle : turtles) {
        xform = turtle.go(xform, pen, bb, g, regions, points);
      }
      dirty = false;
    }
  }

  @SuppressWarnings("unused")
  private static void bug(String what) {
    Debug.out("DrawingBuffer", what);
  }

  public Image getImage() {
    if (turtles.size() > 0 && dirty) {
      update();
    }
    return img;
  }

  public BoundingBox getBoundingBox() {
    return bb;
  }

  public void paste(Graphics2D g) {
    AffineTransform before = new AffineTransform(g.getTransform());
    if (bb == null) {
      update();
    }
    g.translate(bb.getX(), bb.getY());
    g.drawImage(getImage(), 0, 0, null);
    g.setTransform(before);
  }

  public void forward(double d) {
    addOp(new TurtleOp(AffineTransform.getTranslateInstance(0.0, d)));
  }

  public void turn(double deg) {
    addOp(new TurtleOp(AffineTransform.getRotateInstance(Math.toRadians(deg))));
  }

  public void moveTo(double x, double y) {
    addOp(new TurtleOp(new Point2D.Double(x, y)));
  }

  public void up() {
    PenState p = new PenState();
    p.setDown(false);
    addOp(new TurtleOp(p));
  }

  public void down() {
    PenState p = new PenState();
    p.setDown(true);
    addOp(new TurtleOp(p));
  }

  public void setColor(double r, double g, double b, double a) {
    PenState p = new PenState();
    p.setColor(new Color((float) r, (float) g, (float) b, (float) a));
    addOp(new TurtleOp(p));
  }

  public void setThickness(double t) {
    PenState p = new PenState();
    p.setThickness((float) t);
    addOp(new TurtleOp(p));
  }

  public void setFillColor(double r, double g, double b, double a) {
    PenState p = new PenState();
    p.setFillColor(new Color((float) r, (float) g, (float) b, (float) a));
    addOp(new TurtleOp(p));
  }

  public void setFilling(boolean f) {
    PenState p = new PenState();
    p.setFilling(f);
    addOp(new TurtleOp(p));
  }

//  public void generatePdf(OutputStream out) {
//    if (bb == null) {
//      update();
//    }
//    int w = bb.getWidthInt();
//    int h = bb.getHeightInt();
//    Rectangle size = new Rectangle(w, h);
//    Document document = new Document(size, 0, 0, 0, 0);
//
//    try {
//      PdfWriter writer = PdfWriter.getInstance(document, out);
//      document.open();
//
//      DefaultFontMapper mapper = new DefaultFontMapper();
//      FontFactory.registerDirectories();
//
//      PdfContentByte cb = writer.getDirectContent();
//      PdfTemplate tp = cb.createTemplate(w, h);
//      Graphics2D g2 = tp.createGraphics(w, h, mapper);
//      tp.setWidth(w);
//      tp.setHeight(h);
//      paste(g2);
//      g2.dispose();
//      cb.addTemplate(tp, 0, 0);
//    } catch (DocumentException ex) {
//      SlippyMachine.outputStream.println(ex.getMessage());
//    }
//    document.close();
//  }

  private static class TurtleOp {

    TurtleOp parent;
    AffineTransform myTransform;
    AffineTransform resultingTransform;
    PenState myPenState;
    Point2D myMoveTo;

    public TurtleOp() {
      this.parent = null;
    }

    public TurtleOp(AffineTransform mine) {
      this();
      this.myTransform = mine;
    }

    /**
     * @param penState
     */
    public TurtleOp(PenState penState) {
      this();
      this.myPenState = penState;
    }

    /**
     * A turtle op for moving to a specific location regardless of what the current transform is.
     * 
     * @param tx
     *          A point indicating the x and y translation.
     */
    public TurtleOp(Point2D tx) {
      this();
      this.myMoveTo = tx;
    }

    public void setParent(TurtleOp p) {
      this.parent = p;
    }

    public AffineTransform go(AffineTransform xform, PenState pen, BoundingBox bb, Graphics2D g,
        List<FilledRegion> regions, List<Point2D> points) {
      AffineTransform change = xform;

      boolean movement = false;
      if (myPenState != null) {
        if (myPenState.changeFilling) {
          // off -> on = add a new FilledRegion
          if (myPenState.filling) {
            regions.add(new FilledRegion(pen.fillColor));
          }
        }
        if (myPenState.changeDown) {
          if (myPenState.down) {
            points.clear();
          } else { // pen lifted up
            if (g != null && points.size() > 0) {
              g.setColor(pen.color);
              g.setStroke(Strokes.get(pen.thickness));
              GeneralPath gp = new GeneralPath();
              boolean first = true;
              for (Point2D pt : points) {
                if (first) {
                  gp.moveTo((float) pt.getX(), (float) pt.getY());
                  first = false;
                } else {
                  gp.lineTo((float) pt.getX(), (float) pt.getY());
                }
              }
              g.draw(gp);
            }
          }
        }
        pen.incorporate(myPenState);
      } else if (myTransform != null && !myTransform.isIdentity()) {
        change = new AffineTransform(myTransform);
        change.preConcatenate(xform);
        movement = true;
      } else if (myMoveTo != null) {
        change = AffineTransform.getTranslateInstance(myMoveTo.getX(), myMoveTo.getY());
        movement = true;
      }

      if (movement && pen.down) {
        double x1, y1, x2, y2;
        x1 = xform.getTranslateX();
        y1 = xform.getTranslateY();
        bb.add(new Point2D.Double(x1, y1), (double) pen.thickness);
        if (pen.filling) {
          regions.get(regions.size() - 1).addPoint(x1, y1);
        }
        x2 = change.getTranslateX();
        y2 = change.getTranslateY();
        bb.add(new Point2D.Double(x2, y2), (double) pen.thickness);
        if (pen.filling) {
          regions.get(regions.size() - 1).addPoint(x2, y2);
        }

        if (g != null && ((Math.abs(x2 - x1) > 0.0) || (Math.abs(y2 - y1) > 0.0))) {
          Point2D pt1 = new Point2D.Double(x1, y1);
          Point2D pt2 = new Point2D.Double(x2, y2);
          if (points.size() == 0) {
            points.add(pt1);
            points.add(pt2);
          } else {
            if (points.get(points.size() - 1).distance(pt1) > 0.0) {
              points.add(pt1);
            }
            if (points.get(points.size() - 1).distance(pt2) > 0.0) {
              points.add(pt2);
            }
          }
        }
      }
      resultingTransform = change;
      return change;
    }

    public AffineTransform getResultingTransform() {
      return resultingTransform;
    }

    @SuppressWarnings("unused")
    private static void bug(String what) {
      Debug.out("TurtleOp", what);
    }
  }

  private static class PenState {

    boolean down;
    float thickness;
    Color color;
    boolean filling;
    Color fillColor;

    boolean changeDown;
    boolean changeThickness;
    boolean changeColor;
    boolean changeFilling;
    boolean changeFillColor;

    public PenState() {

    }

    public PenState(PenState base, PenState target) {
      this.down = base.down;
      this.thickness = base.thickness;
      this.color = base.color;
      this.filling = base.filling;
      this.fillColor = base.fillColor;
      incorporate(target);
    }

    public void incorporate(PenState target) {
      if (target.changeDown) {
        this.down = target.down;
      }
      if (target.changeThickness) {
        this.thickness = target.thickness;
      }
      if (target.changeColor) {
        this.color = target.color;
      }
      if (target.changeFilling) {
        this.filling = target.filling;
      }
      if (target.changeFillColor) {
        this.fillColor = target.fillColor;
      }
    }

    public String toString() {
      return "down: " + down + (changeDown ? "* " : " ") + "thickness: " + thickness
          + (changeThickness ? "* " : " ") + "color: " + color + (changeColor ? "* " : " ")
          + "filling: " + filling + (changeFilling ? "* " : " ") + "fillColor: " + fillColor
          + (changeFillColor ? "* " : " ");
    }

    public void setDown(boolean v) {
      changeDown = true;
      down = v;
    }

    public void setThickness(float t) {
      changeThickness = true;
      thickness = t;
    }

    public void setColor(Color c) {
      changeColor = true;
      color = c;
    }

    public void setFillColor(Color c) {
      changeFillColor = true;
      fillColor = c;
    }

    public void setFilling(boolean f) {
      changeFilling = true;
      filling = f;
    }
  }

  private static class FilledRegion {

    private Color color;
    private List<Point2D> points;
    private BoundingBox bb;
    private boolean dirty;
    private PathIterator pathIterator;

    public FilledRegion(Color c) {
      this.color = c;
      this.points = new ArrayList<Point2D>();
      bb = new BoundingBox();
    }

    /**
     * Adds a point to this filled region in order. It will silently fail to do this if the given
     * point is the same as the most recently added point.
     */
    public void addPoint(double x, double y) {
      if (points.size() > 0) {
        Point2D prev = points.get(points.size() - 1);
        if (prev.getX() == x && prev.getY() == y) {
          return;
        }
      }
      Point2D pt = new Point2D.Double(x, y);
      dirty = true;
      bb.add(pt);
      points.add(pt);
    }

    public Color getColor() {
      return color;
    }

    @SuppressWarnings("unused")
    private static void bug(String what) {
      Debug.out("FilledRegion", what);
    }

    public PathIterator getPathIterator() {
      if (dirty || pathIterator == null) {
        GenericPathIterator gpi = new GenericPathIterator();

        Point2D first = null;
        for (Point2D pt : points) {
          if (first == null) {
            gpi.add(PathIterator.SEG_MOVETO, pt);
            first = pt;
          } else {
            gpi.add(PathIterator.SEG_LINETO, pt);
          }
        }
        pathIterator = gpi;
        dirty = false;
      }
      return pathIterator;
    }

    public List<Point2D> getPoints() {
      return points;
    }
  }
}
