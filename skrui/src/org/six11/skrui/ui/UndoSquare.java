package org.six11.skrui.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;

import org.six11.skrui.DrawingBufferRoutines;
import org.six11.util.Debug;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Pt;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class UndoSquare extends PenSquare {

  ColorBar cb; // source of color data.
  boolean markerSet = false;
  double marker;
  double moveDistance;

  /**
   * @param colorBar
   * @param i
   */
  public UndoSquare(ColorBar colorBar, double moveDistance) {
    super();
    this.moveDistance = moveDistance;
    this.cb = colorBar;
  }

  @Override
  public void down() {
    markerSet = false;
  }

  public void up() {
    // nothing
  }

  @Override
  public void go(Pt pt) {
    if (!markerSet) {
      setMark(pt);
    } else {
      double dist = pt.getX() - marker;
      if (dist > moveDistance) {
        setMark(pt);
        fireUndo("redo");
      } else if (dist < -moveDistance) {
        setMark(pt);
        fireUndo("undo");
      }
    }
  }

  private void setMark(Pt pt) {
    marker = pt.getX();
    markerSet = true;
  }

  protected void initDB() {
    bug("UndoSquare initDB");
    db = new DrawingBuffer();
    Rectangle r = getVisibleRect(); // new Rectangle(getWidth(), getHeight());
    double thirdY = r.getHeight() / 3;
    double twoThirdsY = thirdY * 2;
    double midX = r.getCenterX();
    // double midY = r.getCenterY();

    String undoMsg = "Undo";
    Rectangle2D block = DrawingBuffer.getTextBounds(undoMsg, font);
    double cursorX = midX - block.getWidth() / 2;
    double cursorYUpper = thirdY + block.getHeight() / 2;
    DrawingBufferRoutines.text(db, new Pt(cursorX, cursorYUpper), undoMsg, Color.black, font);
    DrawingBufferRoutines.arrow(db,
        new Pt(midX + block.getWidth() / 2, thirdY - block.getHeight()), new Pt(cursorX, thirdY
            - block.getHeight()), 1.5, Color.BLACK);

    String redoMsg = "Redo";
    block = DrawingBuffer.getTextBounds(redoMsg, font);
    cursorX = midX - block.getWidth() / 2;
    double cursorYLower = twoThirdsY + block.getHeight() / 2;
    DrawingBufferRoutines.text(db, new Pt(cursorX, cursorYLower), redoMsg, Color.black, font);
    DrawingBufferRoutines.arrow(db, new Pt(cursorX, twoThirdsY + block.getHeight()), new Pt(midX
        + block.getWidth() / 2, twoThirdsY + block.getHeight()), 1.5, Color.BLACK);

  }

  @SuppressWarnings("unused")
  private final void bug(String what) {
    Debug.out("UndoSquare", what);
  }

  protected void fireUndo(String dir) {
    firePCE(new PropertyChangeEvent(this, "undoEvent", null, dir));
  }

}
