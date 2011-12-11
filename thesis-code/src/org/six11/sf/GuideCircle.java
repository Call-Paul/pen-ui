package org.six11.sf;

import java.awt.Color;
import java.awt.Rectangle;

import org.six11.sf.rec.RecognizedItemTemplate;
import org.six11.util.gui.shape.Circle;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Pt;

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

}
