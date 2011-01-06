package org.six11.skruifab;

import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.geom.GeneralPath;
import java.util.Collection;
import java.util.List;

import org.six11.util.Debug;
import org.six11.util.gui.shape.Circle;
import org.six11.util.pen.CircleArc;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.Vec;

/**
 * A collection of drawing routines for DrawingBuffer instances.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public abstract class DrawingBufferRoutines {

  public static Font defaultFont = new Font("sansserif", Font.PLAIN, 11);

  public static void rect(DrawingBuffer db, Pt where, double sx, double sy, Color borderColor,
      Color fillColor, double borderThickness) {
    db.up();
    if (borderColor != null) {
      db.setColor(borderColor);
    }
    if (fillColor != null) {
      db.setFillColor(fillColor);
      db.setFilling(true);
    } else {
      db.setFilling(false);
    }
    if (borderThickness > 0.0) {
      db.setThickness(borderThickness);
    }
    db.moveTo(where.x, where.y);
    db.forward(sy / 2.0);
    db.turn(90);
    db.down();

    db.forward(sx / 2.0);
    db.turn(90);
    db.forward(sy);
    db.turn(90);
    db.forward(sx);
    db.turn(90);
    db.forward(sy);
    db.turn(90);
    db.forward(sx / 2.0);
    db.up();
    if (fillColor != null) {
      db.setFilling(false);
    }
  }

  public static void spline(DrawingBuffer db, List<Pt> ctrl, Color lineColor, double lineThickness,
      int numSteps) {
    Sequence spline = new Sequence();
    int last = ctrl.size() - 1;
    for (int i = 0; i < last; i++) {
      Functions.getSplinePatch(ctrl.get(Math.max(0, i - 1)), ctrl.get(i), ctrl.get(i + 1), ctrl
          .get(Math.min(last, i + 2)), spline, numSteps);
    }
    db.up();
    if (lineColor != null) {
      db.setColor(lineColor);
    }
    if (lineThickness > 0.0) {
      db.setThickness(lineThickness);
    }
    boolean first = true;
    for (Pt pt : spline) {
      db.moveTo(pt.getX(), pt.getY());
      if (first) {
        db.down();
        first = false;
      }
    }
    db.up();
  }

  public static void bug(String what) {
    Debug.out("DrawingBufferRoutines", what);
  }

  public static void line(DrawingBuffer db, Pt start, Pt end, Color color, double thick) {
    db.up();
    db.setColor(color);
    db.setThickness(thick);
    db.moveTo(start.x, start.y);
    db.down();
    db.moveTo(end.x, end.y);
    db.up();
  }

  public static void line(DrawingBuffer db, Pt start, Pt end, Color color) {
    line(db, start, end, color, 1.0);
  }

  public static void lines(DrawingBuffer db, List<Pt> points, Color color, double thick) {
    if (points.size() > 0) {
      db.up();
      db.setColor(color);
      db.setThickness(thick);
      db.moveTo(points.get(0).x, points.get(0).y);
      db.down();
      for (Pt pt : points) {
        db.moveTo(pt.x, pt.y);
      }
      db.up();
    }
  }

  public static void arc(DrawingBuffer db, CircleArc arc, Color color) {
    db.up();
    Pt s = arc.start;
    Pt m = arc.mid;
    Pt e = arc.end;
    db.setColor(color);
    db.down();
    db.circleTo(s.x, s.y, m.x, m.y, e.x, e.y);
    db.up();
  }

  public static void drawShape(DrawingBuffer db, List<Pt> corners, Color color, double thickness) {
    if (db.isVisible() && corners.size() > 3) {
      db.up();
      GeneralPath gp = makePath(corners);
      db.setColor(color);
      db.setThickness(thickness);
      db.down();
      db.addShape(gp);
      db.up();
    }
  }

  public static void fillShape(DrawingBuffer db, List<Pt> corners, Color fillColor,
      double borderThickness) {
    if (db.isVisible() && corners.size() > 3) {
      GeneralPath gp = makePath(corners);
      db.up();
      db.setFillColor(fillColor);
      db.setFilling(true);
      db.setColor(fillColor);
      db.setThickness(borderThickness);
      db.down();
      db.addShape(gp);
      db.up();
      db.setFilling(false);
    }
  }

  public static GeneralPath makePath(List<Pt> corners) {
    GeneralPath gp = new GeneralPath(GeneralPath.WIND_NON_ZERO, corners.size());
    gp.moveTo(corners.get(0).getX(), corners.get(1).getY());
    for (int i = 1; i < corners.size(); i++) {
      gp.lineTo(corners.get(i).getX(), corners.get(i).getY());
    }
    return gp;
  }

  public static void dot(DrawingBuffer db, Pt center, double radius, double thickness,
      Color borderColor, Color fillColor) {
    db.up();
    if (fillColor != null) {
      db.setFillColor(fillColor);
      db.setFilling(true);
    }
    db.setColor(borderColor);
    db.setThickness(thickness);
    Circle circle = new Circle(center.x, center.y, radius);
    db.down();
    db.addShape(circle);
    db.up();
    if (fillColor != null) {
      db.setFilling(false);
    }
  }

  public static void dots(DrawingBuffer db, List<Pt> points, double radius, double thickness,
      Color borderColor, Color fillColor) {
    for (Pt pt : points) {
      dot(db, pt, radius, thickness, borderColor, fillColor);
    }
  }

  public static void fill(DrawingBuffer db, Collection<Pt> points, double thick, Color border,
      Color fill) {
    db.setFillColor(fill);
    db.setFilling(true);
    db.setColor(border);
    db.setThickness(thick);
    boolean first = true;
    Pt last = null;
    for (Pt pt : points) {
      last = pt;
      db.moveTo(pt.x, pt.y);
      if (first) {
        first = false;
        db.down();
      }
    }
    if (last != null) {
      db.moveTo(last.x, last.y);
    }
    db.up();
    db.setFilling(false);
  }

  public static void line(DrawingBuffer db, Line l, Color color, double thick) {
    line(db, l.getStart(), l.getEnd(), color, thick);
  }

  public static void text(DrawingBuffer db, Pt location, String msg, Color color) {
    text(db, location, msg, color, defaultFont);
  }

  public static void text(DrawingBuffer db, Pt location, String msg, Color color, Font font) {
    db.up();
    db.moveTo(location.x, location.y);
    db.down();
    db.addText(msg, color, font);
    db.up();
  }

  public static void patch(DrawingBuffer db, Sequence seq, int startIdx, int endIdx,
      double thickness, Color color) {
    db.setColor(color);
    db.setThickness(thickness);
    boolean first = true;
    for (int i = startIdx; i <= endIdx; i++) {
      Pt pt = seq.get(i);
      db.moveTo(pt.x, pt.y);
      if (first) {
        db.down();
        first = false;
      }
    }
    db.up();
  }

  public static void arrow(DrawingBuffer db, Pt start, Pt tip, double thick, Color color) {
    double length = start.distance(tip);
    double headLength = length / 10;
    Vec tipToStart = new Vec(tip, start).getVectorOfMagnitude(headLength);
    Pt cross = tip.getTranslated(tipToStart.getX(), tipToStart.getY());
    Vec outward = tipToStart.getNormal();
    Pt head1 = cross.getTranslated(outward.getX(), outward.getY());
    outward = outward.getFlip();
    Pt head2 = cross.getTranslated(outward.getX(), outward.getY());
    line(db, new Line(start, tip), color, thick);
    line(db, new Line(head1, tip), color, thick);
    line(db, new Line(head2, tip), color, thick);
  }

  /**
   * Draws an 'X' centered at the given point. It forms a square that is 'boxSize' units on a side,
   * and connects the diagonals with lines.
   */
  public static void cross(DrawingBuffer db, Pt spot, double boxSize, Color color) {
    Pt p1 = spot.getTranslated(-boxSize, -boxSize);
    Pt p2 = spot.getTranslated(-boxSize, boxSize);
    Pt p3 = spot.getTranslated(boxSize, boxSize);
    Pt p4 = spot.getTranslated(boxSize, -boxSize);
    line(db, p1, p3, color, 2);
    line(db, p2, p4, color, 2);
  }

  public static void screenLine(DrawingBuffer db, Rectangle bounds, Line geometryLine, Color color,
      double thick) {
    // draw a line that spans the entire rectangle
    if (bounds.intersectsLine(geometryLine)) {
      // find the two intersection points and connect them.
      Pt[] ix = Functions.getIntersectionPoints(bounds, geometryLine);
      if (ix[0] != null && ix[1] != null) {
        line(db, ix[0], ix[1], color, thick);
      }
    }
  }

  /**
   * Given a sequence that represents a pen stroke, make a drawing buffer that has a colored stroke
   * of some thickness. This only makes the buffer---it does not add it to any data structures or
   * map it in any way.
   */
  public static DrawingBuffer makeSequenceBuffer(Sequence s) {
    DrawingBuffer buf = new DrawingBuffer();
    if (s.getAttribute("pen color") != null) {
      buf.setColor((Color) s.getAttribute("pen color"));
    } else {
      buf.setColor(DrawingBuffer.getBasicPen().color);
    }
    if (s.getAttribute("pen thickness") != null) {
      buf.setThickness((Double) s.getAttribute("pen thickness"));
    } else {
      buf.setThickness(DrawingBuffer.getBasicPen().thickness);
    }
    buf.up();
    buf.moveTo(s.get(0).x, s.get(0).y);
    buf.down();
    for (Pt pt : s) {
      buf.moveTo(pt.x, pt.y);
    }
    buf.up();
    return buf;
  }

}
