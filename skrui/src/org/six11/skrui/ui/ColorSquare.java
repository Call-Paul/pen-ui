package org.six11.skrui.ui;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.Timer;

import org.six11.skrui.DrawingBufferRoutines;
import org.six11.util.Debug;
import org.six11.util.gui.Colors;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

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
  List<Pt> penPath;
  List<Sequence> fading;
  Timer fadeTimer;

  public ColorSquare(ColorBar cb, Color c, double maxDist) {
    super();
    this.color = c;
    this.cb = cb;
    this.maxDist = maxDist;
    this.delta = new float[4];
    this.value = new float[4]; // stores temporary color
    penPath = new ArrayList<Pt>();
    fading = new ArrayList<Sequence>();
    fadeTimer = new Timer(40, new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        fadeTick();
      }
    });
    fadeTimer.setInitialDelay(5000);
    fadeTimer.stop();
    fadeTimer.setRepeats(true);
    setBackground(c);
  }

  protected void fadeTick() {
    if (fading.size() == 0) {
      fadeTimer.stop();
    } else {
      Set<Sequence> finished = new HashSet<Sequence>();
      for (Sequence fadeMe : fading) {
        Color c = (Color) fadeMe.getAttribute("pen color");
        c = Colors.makeAlpha(c, Math.max(0, c.getAlpha() - 1));
        fadeMe.setAttribute("pen color", c);
        if (c.getAlpha() <= 0) {
          finished.add(fadeMe);
        }
      }
      fading.removeAll(finished);
      whackUI();
    }
  }

  @Override
  protected void initDB() {
    db = new DrawingBuffer();

    for (Sequence fade : fading) {
      Color c = (Color) fade.getAttribute("pen color");
      double t = (Double) fade.getAttribute("pen thickness");
      DrawingBufferRoutines.lines(db, fade.getPoints(), c, t);
    }

    Color penColor = cb.getCurrentColor();
    double penThickness = cb.getCurrentThickness();
    if (penPath.size() == 1) {
      DrawingBufferRoutines.dot(db, penPath.get(0), penThickness / 2, 1.0, penColor, penColor);
    } else if (penPath.size() > 1) {
      DrawingBufferRoutines.lines(db, penPath, penColor, penThickness);
    } else {
      db.setEmptyOK(true); // didn't draw anything, and that's ok.
    }

    if (color == null) {
      String msg = "Alpha";
      Rectangle2D vis = getVisibleRect();
      Rectangle2D textBlock = DrawingBuffer.getTextBounds(msg, font);
      Pt where = new Pt(vis.getCenterX() - textBlock.getWidth() / 2, vis.getCenterY()
          + textBlock.getHeight() / 2);
      DrawingBufferRoutines.text(db, where, msg, Color.BLACK, font);
    }
  }

  protected void fireColorChange() {
    Color tmpVal = new Color(value[0], value[1], value[2], value[3]);
    firePCE(new PropertyChangeEvent(this, "color", null, tmpVal));
  }

  public Color getColor() {
    return color;
  }

  public void down() {
    sourceColor = cb.getCurrentColor();
    distTravelled = 0;
    lastPt = null;
    src = sourceColor.getComponents(null);
    float[] dst = new float[] {
        src[0], src[1], src[2], 0f
    };
    if (color != null) {
      dst = color.getComponents(null);
    }
    for (int i = 0; i < src.length; i++) {
      delta[i] = dst[i] - src[i];
    }
  }

  @SuppressWarnings("unused")
  private static void bug(String what) {
    Debug.out("ColorSquare", what);
  }

  @Override
  public void go(Pt pt) {
    penPath.add(pt);
    if (lastPt != null) {
      double d = lastPt.distance(pt);
      distTravelled = distTravelled + d;
      distTravelled = Math.min(distTravelled, maxDist);
    }
    float frac = (float) (distTravelled / maxDist);
    frac = frac * frac;
    if (frac > 0 && frac <= 1) {
      for (int i = 0; i < delta.length; i++) {
        value[i] = (src[i] + (frac * delta[i]));
      }
      fireColorChange();
    }
    lastPt = pt;
    whackUI();
  }

  public void up() {
    Sequence fadeMe = new Sequence(penPath, false);
    fadeMe.setAttribute("pen color", cb.getCurrentColor());
    fadeMe.setAttribute("pen thickness", cb.getCurrentThickness());
    fadeTimer.restart();
    fading.add(fadeMe);
    penPath.clear();

    whackUI();
  }

}
