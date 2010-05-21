package org.six11.skrui.ui;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.Rectangle2D;

import org.six11.skrui.DrawingBufferRoutines;
import org.six11.util.Debug;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class AlphaSquare extends ColorSquare {

  /**
   * 
   */
  public AlphaSquare(ColorBar cb, double maxDist) {
    super(cb, null, maxDist);
  }

  @Override
  public void down() {
    sourceColor = cb.getCurrentColor();
    distTravelled = 0;
    lastPt = null;
    src = sourceColor.getComponents(null);
    for (int i = 0; i < 4; i++) {
      value[i] = src[i];
    }
    addComponentListener(new ComponentAdapter() {
      public void componentResized(ComponentEvent e) {
        whackUI();
      }
    });
  }

  @Override
  public void go(Pt pt) {
    bug(" ----------- ");
    penPath.add(pt);
    if (lastPt != null) {
      double d = pt.getY() - lastPt.getY();
      distTravelled = distTravelled + d;
      bug("distTravelled: " + Debug.num(distTravelled));

      float frac = (float) (distTravelled / maxDist);
      frac = Math.max(frac, -1f); // constraint f to [-1..1]
      frac = Math.min(frac, 1f);
      frac = Math.signum(frac) * frac * frac;
      float start = src[3];
      if (frac > 0) {
        value[3] = start + frac * (1 - start);
      } else {
        value[3] = start + frac * start;
      }
      bug("transparency: " + Debug.num(value[3]));
      fireColorChange();
    }
    lastPt = pt;
    whackUI();

  }

  private static void bug(String what) {
    Debug.out("AlphaSquare", what);
  }
    
  @Override
  protected void whackUI() {
    Color cc = cb.getCurrentColor();
    Color solid = new Color(cc.getRed(), cc.getGreen(), cc.getBlue(), 255);
    Color clear = new Color(cc.getRed(), cc.getGreen(), cc.getBlue(), 0);
    Rectangle r = getVisibleRect();
    float mx = (float) r.getCenterX();
    customBackgroundPaint = new GradientPaint(mx, 0, clear, mx, (float) r.getHeight(), solid);
    super.whackUI();
  }

  @Override
  protected void initDB() {
    super.initDB(); // draw user pen marks.
    Rectangle r = getVisibleRect(); // new Rectangle(getWidth(), getHeight());
    double thirdY = r.getHeight() / 3;
    double twoThirdsY = thirdY * 2;
    double midX = r.getCenterX();

    String transMsg = "Transp.";
    Rectangle2D block = DrawingBuffer.getTextBounds(transMsg, font);
    double cursorX = midX - block.getWidth() / 2;
    double cursorYUpper = thirdY + block.getHeight() / 2;
    DrawingBufferRoutines.text(db, new Pt(cursorX, cursorYUpper), transMsg, Color.black, font);
    DrawingBufferRoutines.arrow(db, //
        new Pt(midX, cursorYUpper - 4 - block.getHeight()), // 
        new Pt(midX, cursorYUpper - 14 - block.getHeight()), 1.5, Color.BLACK);

    String opaqueMsg = "Opaque";
    block = DrawingBuffer.getTextBounds(opaqueMsg, font);
    cursorX = midX - block.getWidth() / 2;
    double cursorYLower = twoThirdsY + block.getHeight() / 2;
    DrawingBufferRoutines.text(db, new Pt(cursorX, cursorYLower), opaqueMsg, Color.black, font);
    DrawingBufferRoutines.arrow(db, //
        new Pt(midX, cursorYLower + 4), //
        new Pt(midX, cursorYLower + 14), 1.5, Color.BLACK);

  }
}
