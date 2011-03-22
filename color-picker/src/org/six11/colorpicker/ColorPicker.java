package org.six11.colorpicker;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Shape;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.six11.util.Debug;
import org.six11.util.gui.BoundingBox;
import org.six11.util.gui.Components;
import org.six11.util.gui.shape.ShapeFactory;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Line;
import org.six11.util.pen.MouseThing;
import org.six11.util.pen.Pt;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class ColorPicker extends JComponent {

  /**
   * 
   *
   * @author Gabe Johnson <johnsogg@cmu.edu>
   */
  private class CPMouseThing extends MouseThing {
    
    public void mousePressed(MouseEvent ev) {
      Pt loc = new Pt(ev);
      int where = findRegion(loc);
      if (where >= 0) {
        dragColor = colors[where];
        sourceColor = currentColor;
      } else {
        dragColor = null;
        moving = isPointInInnerRadius(loc);
        if (moving) {
          movingPoint = new Pt(ev.getLocationOnScreen());
        }
      }
      dragLast = loc.copy();
      dragDist = 0;
      repaint();
    }
    public void mouseDragged(MouseEvent ev) {
      if (dragColor != null) {
        Pt loc = new Pt(ev);
        double dd = loc.distance(dragLast);
        dragLast = loc.copy();
        dragDist += dd;
        double tweenAmt = calculateTweenAmount(dragDist);
        double distR = dragColor.getRed() - sourceColor.getRed();
        double distG = dragColor.getGreen() - sourceColor.getGreen();
        double distB = dragColor.getBlue() - sourceColor.getBlue();
        double newR = (tweenAmt * distR) + sourceColor.getRed();
        double newG = (tweenAmt * distG) + sourceColor.getGreen();
        double newB = (tweenAmt * distB) + sourceColor.getBlue();
        currentColor = new Color((int) newR, (int) newG, (int) newB);
      }
      if (moving) {
        Pt loc = new Pt(ev.getLocationOnScreen());
        int dx = loc.ix() -  movingPoint.ix() ;
        int dy = loc.iy() - movingPoint.iy();
        movingPoint = loc;
        requestMove(dx, dy);
      }
      repaint();
    }
    public void mouseReleased(MouseEvent ev) {
      sourceColor = null;
      dragColor = null;
      dragLast = null;
      dragDist = 0;
      moving = false;
      repaint();
    }
    public void mouseMoved(MouseEvent ev) {
      Pt loc = new Pt(ev);
      if (isPointInInnerRadius(loc)) {
        currentCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
      } else if (findRegion(loc) >= 0) {
        currentCursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
      } else {
        currentCursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
      }
      repaint();
    };
    
  }

  int numRegions;
  double innerRadius;
  double outerRadius;
  Color[] colors;
  Color sourceColor;
  Color currentColor;
  Color dragColor;
  Pt dragLast;
  double dragDist;

  private transient Map<Integer, Shape> cachedRegions;
  private transient Pt oldCenter;
  private transient Cursor currentCursor;
  private transient boolean moving = false;
  private transient Pt movingPoint;

  /**
   * 
   */
  public ColorPicker() {
    super();
    numRegions = 8;
    innerRadius = 40;
    outerRadius = 100;
    colors = new Color[numRegions];
    setColor(0, Color.WHITE);
    setColor(1, Color.RED);
    setColor(2, Color.GREEN);
    setColor(3, Color.BLUE);
    setColor(4, Color.CYAN);
    setColor(5, Color.MAGENTA);
    setColor(6, Color.YELLOW);
    setColor(7, Color.BLACK);
    currentColor = Color.BLACK;
    cachedRegions = new HashMap<Integer, Shape>();
    MouseThing mt = new CPMouseThing();
    addMouseListener(mt);
    addMouseMotionListener(mt);
    currentCursor = Cursor.getDefaultCursor();
  }
  
  protected void requestMove(int dx, int dy) {
    Window mom = SwingUtilities.getWindowAncestor(this);
    Point current = mom.getLocation();
    mom.setLocation(current.x + dx, current.y + dy);
  }
  
  @Override
  public Cursor getCursor() {
    return currentCursor;
  }
  
  
  private double calculateTweenAmount(double dist) {
    double amt = dist / 300; // totally arbitrary to pick 300...
    return Math.min(amt, 1.0);
  }
  
  private boolean isPointInInnerRadius(Pt pt) {
    Pt center = getCenter();
    return center.distance(pt) < innerRadius;
  }

  protected int findRegion(Pt pt) {
    int ret = -1;
    Pt center = getCenter();
    double dist = center.distance(pt);
    if (dist > innerRadius && dist < outerRadius) {
      double dx = (center.x - pt.x);
      double dy = (center.y - pt.y);
      double angle = Math.atan2(dy, dx) + Math.PI;
      int region = (int) Math.floor(numRegions * (angle / (2 * Math.PI)));
      ret = Math.min(region, numRegions - 1); // could get numRegions if clicking on line.
    }
    return ret;
  }

  public final void setColor(int idx, Color c) {
    if (idx >= 0 && idx < numRegions) {
      colors[idx] = c;
    } else {
      throw new RuntimeException("Index out of range: " + idx + " not in [0, " + numRegions + ")");
    }
  }

  @Override
  protected void paintComponent(Graphics g1) {
    Graphics2D g = (Graphics2D) g1;
    Components.antialias(g);
    Pt center = getCenter();
    if (oldCenter == null || !center.isSameLocation(oldCenter)) {
      cachedRegions.clear();
    }
    DrawingBuffer db = new DrawingBuffer();
    DrawingBufferRoutines.dot(db, center, outerRadius, 1.3, Color.BLACK, null);
    DrawingBufferRoutines.dot(db, center, innerRadius, 1.3, Color.BLACK, currentColor);
    for (int i = 0; i < numRegions; i++) {
      drawRegion(db, i);
    }
    Image img = db.getImage();
    BoundingBox bb = db.getBoundingBox();
    g.drawImage(img, (int) bb.getX(), (int) bb.getY(), null);
    oldCenter = center;
    super.paintComponent(g);
  }

  private void drawRegion(DrawingBuffer db, int idx) {
    Color c = colors[idx];
    ensureRegionsAreCached();
    DrawingBufferRoutines.fillShape(db, cachedRegions.get(idx), c, 1);
  }

  private void ensureRegionsAreCached() {
    for (int idx = 0; idx < numRegions; idx++) {
      if (!cachedRegions.containsKey(idx)) {
        // draw line from center to a location along the outer circle.
        double angleThis = ((2 * Math.PI) / numRegions) * idx;
        double angleMid = ((2 * Math.PI) / numRegions) * (idx + 0.5);
        double angleNext = ((2 * Math.PI) / numRegions) * (idx + 1);
        Pt center = getCenter();
        Pt outerA = computeArcPoint(center, angleThis, outerRadius);
        Pt outerMid = computeArcPoint(center, angleMid, outerRadius);
        Pt outerB = computeArcPoint(center, angleNext, outerRadius);
        Pt innerA = computeArcPoint(center, angleThis, innerRadius);
        Pt innerMid = computeArcPoint(center, angleMid, innerRadius);
        Pt innerB = computeArcPoint(center, angleNext, innerRadius);

        Shape outerArc = ShapeFactory.makeArc(outerA, outerMid, outerB); // A -> mid -> B
        Shape outToIn = new Line(outerB, innerB);
        Shape innerArc = ShapeFactory.makeArc(innerB, innerMid, innerA); // B -> mid -> A
        Shape inToOut = new Line(innerA, outerA);
        GeneralPath gp = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
        gp.append(outerArc, false);
        gp.append(outToIn, true);
        gp.append(innerArc, true);
        gp.append(inToOut, true);
        cachedRegions.put(idx, gp);
      }
    }
  }

  private static void bug(String what) {
    Debug.out("ColorPicker", what);
  }

  private Pt computeArcPoint(Pt center, double angle, double radius) {
    double ptX = center.x + (radius * Math.cos(angle));
    double ptY = center.y + (radius * Math.sin(angle));
    return new Pt(ptX, ptY);
  }

  public Pt getCenter() {
    double cx = outerRadius;
    double cy = outerRadius;
    return new Pt(cx, cy);
  }
}
