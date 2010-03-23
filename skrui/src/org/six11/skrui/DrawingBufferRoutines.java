package org.six11.skrui;

import java.awt.Color;
import java.util.Collection;
import java.util.List;
import java.util.Set;

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
    Debug.out("DrawwingBufferRoutines", what);
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

  public static void seg(DrawingBuffer db, Segment seg, Color color) {
    db.up();
    db.setColor(color);
    db.setThickness(1.0);
    if (seg.getBestType() == Segment.Type.LINE) {
      db.moveTo(seg.start.x, seg.start.y);
      db.down();
      db.moveTo(seg.end.x, seg.end.y);
    } else if (seg.getBestType() == Segment.Type.ARC) {
      CircleArc arc = seg.bestCircle;
      Pt s = seg.start;
      Pt m = arc.mid;
      Pt e = seg.end;
      db.down();
      db.circleTo(s.x, s.y, m.x, m.y, e.x, e.y);
    } else if (seg.getBestType() == Segment.Type.SPLINE) {
      boolean is_up = true;
      for (Pt pt : seg.splinePoints) {
        db.moveTo(pt.x, pt.y);
        if (is_up) {
          db.down();
          is_up = false;
        }
      }
    }
    db.up();
    bug(seg.toString());
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
    db.up();
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
    db.up();
    db.moveTo(location.x, location.y);
    db.down();
    db.addText(msg, color);
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
    bug("Draw lines: " + Debug.num(start) + " " + Debug.num(tip) + " " + Debug.num(head1) + " "
        + Debug.num(head2));
    line(db, new Line(start, tip), color, thick);
    line(db, new Line(head1, tip), color, thick);
    line(db, new Line(head2, tip), color, thick);
  }

  public static void flowSelectEffect(DrawingBuffer db, Sequence seq, double thick) {
    if (seq.getPoints().size() > 0) {
      for (int i = 0; i < seq.getPoints().size() - 1; i++) {
        double a = seq.get(i).hasAttribute("fs strength") ? seq.get(i).getDouble("fs strength") : 0;
        double b = seq.get(i + 1).hasAttribute("fs strength") ? seq.get(i + 1).getDouble(
            "fs strength") : 0;
        double c = (a + b) / 2;
        if (c > 0) {
          Color color = new Color(1f, 1 - (float) c, 1 - (float) c, (float) c);
          line(db, seq.get(i), seq.get(i + 1), color, thick);
        }
        if (a > 0.7 && seq.get(i).hasAttribute("corner") && i > 0) {
          double p = seq.get(i - 1).hasAttribute("fs strength") ? seq.get(i - 1).getDouble(
              "fs strength") : 0;
          // See if the corner has a zero-strength neighbor. If so, the corner is a hinge at the
          // moment.
          boolean cornerIsExtent = (p * b == 0); // if either p or b is zero, p * b is zero.
          if (cornerIsExtent) {
            dot(db, seq.get(i), 7.0, 1.0, Color.BLACK, Color.GREEN);
          }
        }
      }
    }
  }
}
