package org.six11.skrui.ui;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import org.six11.skrui.DrawingBufferRoutines;
import org.six11.util.Debug;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Pt;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class ThicknessSquare extends PenSquare {

  private static double offset = 10;
  double min, max, current;
  Pt lastDrag = null;
  Color fg;

  public ThicknessSquare(double min, double max, double current) {
    super();
    this.min = min;
    this.max = max;
    this.current = current;
    this.fg = Color.BLACK;
    addComponentListener(new ComponentAdapter() {
      public void componentResized(ComponentEvent e) {
        whackUI();
      }
    });
  }

  @Override
  public void go(Pt pt) {
    if (lastDrag != null && (pt.getTime() - lastDrag.getTime() < 100)) {
      double midX = getBounds().getCenterX();
      if (Math.min(lastDrag.getX(), pt.getX()) <= midX
          && Math.max(lastDrag.getX(), pt.getX()) >= midX) {
        double averageY = (lastDrag.getY() + pt.getY()) / 2;
        current = calcValue(averageY);
        whackUI();
        firePenThicknessChange();
      }
    }
    lastDrag = pt;
  }

  private void firePenThicknessChange() {
    firePCE(new PropertyChangeEvent(this, "thickness", null, current));
  }
  
  public void up() {
    // nothing
  }
  
  public void down() {
    // do nothing.
  }
  
  public void setColor(Color c) {
    this.fg = c;
    whackUI();
  }

  protected void initDB() {
    db = new DrawingBuffer();
//    Rectangle r = getBounds();
    Rectangle r = new Rectangle(getWidth(), getHeight());
    double midX = r.getCenterX();
    double topY = offset;
    double botY = r.getHeight() - offset;
    double maxX = r.getWidth() - offset;
    List<Pt> sliderThing = new ArrayList<Pt>();
    sliderThing.add(new Pt(midX, topY));
    sliderThing.add(new Pt(midX + (max / 2), topY));
    sliderThing.add(new Pt(midX + (min / 2), botY));
    sliderThing.add(new Pt(midX - (min / 2), botY));
    sliderThing.add(new Pt(midX - (max / 2), topY));
    sliderThing.add(sliderThing.get(0));
    DrawingBufferRoutines.fill(db, sliderThing, 1.0, Color.BLACK, fg);
    DrawingBufferRoutines.dot(db, new Pt(maxX, botY), current / 2, 1, fg, fg);
    DrawingBufferRoutines.line(db, new Pt(midX, 0), new Pt(midX, r.getHeight()), Color.RED, 1.2);
  }

  private double calcValue(double y) {
    // y represents a number that is hopefully in the range given onscreen by the vertical triangle.
    // I have to convert it to a number between 0 and 1, and this will be used to determine the
    // new thickness value.
    double low = offset;
    double high = getBounds().getHeight() - offset;
    double frac = 1 - ((y - low) / (high - low));
    double ret = min + (frac * (max - min));
    ret = Math.max(ret, min); // bind to range min..max
    ret = Math.min(ret, max);
    return ret;
  }

  @SuppressWarnings("unused")
  private static void bug(String what) {
    Debug.out("ThicknessSquare", what);
  }
}
