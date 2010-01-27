package org.six11.skrui;

import java.awt.Color;
import java.util.List;

import org.six11.util.Debug;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

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

  public static void seg(DrawingBuffer db, Segment seg, Color color) {
    db.up();
    db.setColor(color);
    db.setThickness(1.0);
    if (seg.getBestType() == Segment.Type.LINE) {
      db.moveTo(seg.start.x, seg.start.y);
      db.down();
      db.moveTo(seg.end.x, seg.end.y);
    } else {
      CircleArc arc = seg.bestCircle;
      Pt s = seg.start;
      Pt e = seg.end;
      Pt c = arc.center;
      double startAngle = Math.toDegrees(-Math.atan2(s.y - c.y, s.x - c.x));
      double endAngle   = Math.toDegrees(-Math.atan2(e.y - c.y, e.x - c.x));
      double extent = endAngle - startAngle;
      double topLeftX = c.x - arc.radius;
      double topLeftY = c.y - arc.radius;
      double d = arc.radius * 2.0;

    }
    db.up();
  }
}
