package org.six11.skrui.ui;

import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.six11.util.Debug;
import org.six11.util.pen.Pt;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class ColorSquare extends PenSquare {
  Color color;
  ColorBar cb; // source of color data.
  Color sourceColor;
  double distTravelled;
  double maxDist;
  float[] src;
  float[] delta;
  float[] value;
  Pt lastPt;

  public ColorSquare(ColorBar cb, Color c, double maxDist) {
    this.color = c;
    this.cb = cb;
    this.maxDist = maxDist;
    this.delta = new float[4];
    this.value = new float[4]; // stores temporary color
    setBackground(c);
    
  }

  protected void fireColorChange() {
    Color tmpVal = new Color(value[0], value[1], value[2], value[3]);
    PropertyChangeEvent ev = new PropertyChangeEvent(this, "color", null, tmpVal);
    for (PropertyChangeListener pcl : pcls) {
      pcl.propertyChange(ev);
    }
  }

  public Color getColor() {
    return color;
  }

  public void down() {
    sourceColor = cb.getCurrentColor();
    distTravelled = 0;
    lastPt = null;
    src = sourceColor.getComponents(null);
    float[] dst = new float[] { src[0], src[1], src[2], 0f };
    if (color != null) {
      dst = color.getComponents(null);
    }
    for (int i = 0; i < src.length; i++) {
      delta[i] = dst[i] - src[i];
    }
  }

  private static void bug(String what) {
    Debug.out("ColorSquare", what);
  }

  @Override
  public void go(Pt pt) {
    if (lastPt != null) {
      double d = lastPt.distance(pt);
      distTravelled = distTravelled + d;
      distTravelled = Math.min(distTravelled, maxDist);
    }
    float frac = (float) (distTravelled / maxDist);
    frac = frac * frac;
    if (frac > 0 && frac <= 1) {
      for (int i=0; i < delta.length; i++) {
        value[i] = (src[i] + (frac * delta[i]));
      }
      fireColorChange();
    }
    lastPt = pt;
  }

}
