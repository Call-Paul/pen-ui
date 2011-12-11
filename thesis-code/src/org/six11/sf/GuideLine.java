package org.six11.sf;

import java.awt.Color;
import java.awt.Rectangle;

import org.six11.sf.rec.RecognizedItemTemplate;
import org.six11.util.gui.shape.Circle;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;

public class GuideLine extends Guide {

  Pt a, b;
  Line myLine;

  public GuideLine(Pt a, Pt b) {
    this.a = a;
    this.b = b;
    if (b != null) {
      myLine = new Line(a, b);
    }
  }

  @Override
  public void draw(DrawingBuffer buf, Pt hoverPoint, Color in, Rectangle bounds) {
    if (hoverPoint != null) {
      double residual = 0;
      if (b != null) {
        residual = Functions.getDistanceBetweenPointAndLine(hoverPoint, myLine);
      }
      float alpha = (float) RecognizedItemTemplate.getAlpha(residual, 5, 30, 0.1);
      Color c = new Color(in.getRed(), in.getGreen(), in.getBlue(), (int) (alpha * 255f));
      if (b == null) {
        myLine = new Line(a, hoverPoint);
      }
      DrawingBufferRoutines.screenLine(buf, bounds, myLine, c, 1.2); 
    }
  }
}

//
//double toHover = center.distance(hoverPoint);
//double residual = 0;
//if (fixedRadius) {
//  residual = Math.abs(toHover) - rad;
//} else {
//  myCircle = new Circle(center, center.distance(hoverPoint));
//}
//float alpha = (float) RecognizedItemTemplate.getAlpha(residual, 5, 30);
//Color c = new Color(in.getRed(), in.getGreen(), in.getBlue(), (int) (alpha * 255f));
//DrawingBufferRoutines.drawShape(buf, myCircle, c, 1.2);