package org.six11.skrui.ui;

import java.awt.Color;
import java.awt.Rectangle;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
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


  
  private final void bug(String what) {
    Debug.out("UndoSquare", what);
  }
  
  protected void fireUndo(String dir) {
    PropertyChangeEvent ev = new PropertyChangeEvent(this, "undoEvent", null, dir);
    for (PropertyChangeListener pcl : pcls) {
      pcl.propertyChange(ev);
    }
  }

}
