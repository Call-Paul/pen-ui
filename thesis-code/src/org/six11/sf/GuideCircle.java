package org.six11.sf;

import java.awt.Color;
import java.awt.Rectangle;

import org.six11.sf.rec.RecognizedItemTemplate;
import org.six11.util.gui.shape.Circle;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

public class GuideCircle extends Guide {

  private Pt center;
  private Pt outside;
  private boolean fixedRadius;
  private double rad;
  Circle myCircle;

  public GuideCircle(Pt center, Pt outside) {
    this.center = center;
    this.outside = outside;
    if (outside != null) {
      this.myCircle = new Circle(center, center.distance(outside));
      this.rad = outside.distance(center);
      this.fixedRadius = true;
    } else {
      this.fixedRadius = false;
    }
  }

  public void draw(DrawingBuffer buf, Pt hoverPoint, Color in, Rectangle bounds) {
    if (hoverPoint != null) {
      double toHover = center.distance(hoverPoint);
      double residual = 0;
      if (fixedRadius) {
        residual = Math.abs(toHover - rad);
      } else {
        myCircle = new Circle(center, center.distance(hoverPoint));
      }
      float alpha = (float) RecognizedItemTemplate.getAlpha(residual, 5, 30, 0.1);
      Color c = new Color(in.getRed(), in.getGreen(), in.getBlue(), (int) (alpha * 255f));
      DrawingBufferRoutines.drawShape(buf, myCircle, c, 1.2);
    }
  }

  public boolean claims(Sequence seq, int start, int end) {
    boolean ret = false;
    if (outside == null) {
      myCircle = new Circle(center, fixedHover.distance(center));
    }
    double totalError = 0;
    for (int i = start; i <= end; i++) {
      Pt pt = seq.get(i);
      double dist = Math.abs(pt.distance(center) - myCircle.getRadius());
      totalError = totalError + (dist * dist);
    }
    double sampleError = totalError / ((end - start) + 1);
    if (sampleError < 20) {
      ret = true;
    }
    return ret;
  }

  @Override
  public String toString() {
    String d = "?";
    if (outside != null) {
      d = "static radius: " + num(center.distance(outside));
    } else if (fixedHover != null) {
      d = "hover radius:" + num(center.distance(fixedHover));
    }
    return "circle: " + num(center) + " " + d;
  }

  public boolean isDynamic() {
    return outside == null;
  }

  public Guide getFixedCopy() {
    return new GuideCircle(center, isDynamic() ? fixedHover : outside);
  }

  public Segment adjust(Ink ink, int start, int end) {
    Pt spot = isDynamic() ? fixedHover : outside;
    return new CircularArcSegment(ink, ink.seq.getSubSequence(start, end + 1).getPoints(), center,
        spot.distance(center), true, true);
  }

}
