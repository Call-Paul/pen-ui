package org.six11.skrui.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

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
  DrawingBuffer db;
  Pt lastDrag = null;

  public ThicknessSquare(double min, double max, double current) {
    super();
    this.min = min;
    this.max = max;
    this.current = current;
  }

  @Override
  public void go(Pt pt) {
    if (lastDrag != null && (pt.getTime() - lastDrag.getTime() < 100)) {
      double midX = getBounds().getCenterX();
      if (Math.min(lastDrag.getX(), pt.getX()) < midX
          && Math.max(lastDrag.getX(), pt.getX()) > midX) {
        double averageY = (lastDrag.getY() + pt.getY()) / 2;
        current = calcValue(averageY);
        db = null;
        repaint();
      }
    }
    lastDrag = pt;
  }

  @Override
  public void paintComponent(Graphics g) {
    if (db == null) {
      initDB();
    }
    Graphics2D g2 = (Graphics2D) g;
    g2.setPaint(Color.WHITE);
    g2.fill(getBounds());
    g.drawImage(db.getImage(), 0, 0, null);
  }

  private void initDB() {
    db = new DrawingBuffer();
    Rectangle r = getBounds();

    double midX = r.getCenterX();
    double topY = offset;
    double botY = r.getHeight() - offset;
    double maxX = r.getWidth() - offset;
    db.setFillColor(Color.BLACK);
    db.setFilling(true);
    db.moveTo(midX, topY);
    db.down();
    db.moveTo(midX + (max / 2), topY);
    db.moveTo(midX + (min / 2), botY);
    db.moveTo(midX - (min / 2), botY);
    db.moveTo(midX - (max / 2), topY);
    db.moveTo(midX, topY);
    db.up();
    db.setFilling(false);
    DrawingBufferRoutines.dot(db, new Pt(maxX, botY), current / 2, 1, Color.BLACK, Color.BLACK);
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

  private static void bug(String what) {
    Debug.out("ThicknessSquare", what);
  }
}
