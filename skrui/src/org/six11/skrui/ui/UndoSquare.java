package org.six11.skrui.ui;

import java.beans.PropertyChangeEvent;

import org.six11.util.Debug;
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


  
  @SuppressWarnings("unused")
  private final void bug(String what) {
    Debug.out("UndoSquare", what);
  }
  
  protected void fireUndo(String dir) {
    firePCE(new PropertyChangeEvent(this, "undoEvent", null, dir));
  }

}
