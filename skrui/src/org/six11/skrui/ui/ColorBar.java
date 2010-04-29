package org.six11.skrui.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Point;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

import org.six11.skrui.DrawingBufferRoutines;
import org.six11.util.Debug;
import org.six11.util.gui.Colors;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Pt;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class ColorBar extends JPanel {

  private ArrayList<PenSquare> squares;
  private Color currentColor;
  private double thickness;
  List<PropertyChangeListener> pcls;

  public ColorBar() {
    pcls = new ArrayList<PropertyChangeListener>();
    double fullDist = 100;
    squares = new ArrayList<PenSquare>();
    this.thickness = 4.0;
    squares.add(new ThicknessSquare(0.05, 24.0, thickness));
    ColorSquare black = new ColorSquare(this, Color.BLACK, fullDist);
    squares.add(black);
    squares.add(new ColorSquare(this, Color.BLUE, fullDist));
    squares.add(new ColorSquare(this, Color.GREEN, fullDist));
    squares.add(new ColorSquare(this, Color.LIGHT_GRAY, fullDist));
    squares.add(new ColorSquare(this, Color.RED, fullDist));
    squares.add(new ColorSquare(this, Color.WHITE, fullDist));
    squares.add(new ColorSquare(this, null, fullDist));
    squares.add(new UndoSquare(this, 40));
    
    currentColor = black.getColor();

    PropertyChangeListener handler = new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent ev) {
        if (ev.getPropertyName().equals("color")) {
          Color oldColor = currentColor;
          currentColor = (Color) ev.getNewValue();
          firePropertyChange(new PropertyChangeEvent(this, "pen color", oldColor, ev.getNewValue()));
          whackThicknessForeground();
        } else if (ev.getPropertyName().equals("thickness")) {
          double oldThickness = thickness;
          thickness = (Double) ev.getNewValue();
          firePropertyChange(new PropertyChangeEvent(this, "pen thickness", oldThickness, ev
              .getNewValue()));
        } else if (ev.getPropertyName().equals("undoEvent")) {
          firePropertyChange(ev); // relay
        }
      }
    };
    setLayout(new GridLayout(1, 0));
    for (PenSquare sq : squares) {
      sq.addPropertyChangeListener(handler);
      add(sq);
    }
  }

  private static void bug(String what) {
    Debug.out("ColorBar", what);
  }
  
  public Cursor getCursor() {
    DrawingBuffer db = new DrawingBuffer();
    double t2 = thickness / 2;
    Pt hot = new Pt(t2, t2);
    if (Colors.isDark(currentColor)) {
      DrawingBufferRoutines.dot(db, hot, Math.max(t2, 1.5), 1.0, Color.white, currentColor);
    } else {
      DrawingBufferRoutines.dot(db, hot, Math.max(t2, 1.5), 1.0, Color.black, currentColor);
    }
    Image im = db.getImage();
    Cursor ret = java.awt.Toolkit.getDefaultToolkit().createCustomCursor(im,
        new Point(hot.ix(), hot.iy()), "pen");
    return ret;
  }

  protected void whackThicknessForeground() {
    for (PenSquare ps : squares) {
      if (ps instanceof ThicknessSquare) {
        ((ThicknessSquare) ps).setColor(currentColor);
      }
    }
  }

  public void addPropertyChangeListener(PropertyChangeListener pcl) {
    pcls.add(pcl);
  }

  public void removePropertyChangeListener(PropertyChangeListener pcl) {
    pcls.remove(pcl);
  }

  protected void firePropertyChange(PropertyChangeEvent ev) {
    for (PropertyChangeListener pcl : pcls) {
      pcl.propertyChange(ev);
    }
  }

  public Color getCurrentColor() {
    return currentColor;
  }

  public double getCurrentThickness() {
    return thickness;
  }
}
