package org.six11.skrui.ui;

import java.awt.Color;
import java.awt.GridLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

/**
 * 
 *
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class ColorBar extends JPanel {

  private List<ColorSquare> squares;
  private Color currentColor;
  List<PropertyChangeListener> pcls;

  
  public ColorBar() {
    pcls = new ArrayList<PropertyChangeListener>();
    squares = new ArrayList<ColorSquare>();
    squares.add(new ColorSquare(Color.BLACK));
    squares.add(new ColorSquare(Color.BLUE));
    squares.add(new ColorSquare(Color.CYAN));
    squares.add(new ColorSquare(Color.DARK_GRAY));
    squares.add(new ColorSquare(Color.GRAY));
    squares.add(new ColorSquare(Color.GREEN));
    squares.add(new ColorSquare(Color.LIGHT_GRAY));
    squares.add(new ColorSquare(Color.MAGENTA));
    squares.add(new ColorSquare(Color.ORANGE));
    squares.add(new ColorSquare(Color.PINK));
    squares.add(new ColorSquare(Color.RED));
    squares.add(new ColorSquare(Color.WHITE));
    squares.add(new ColorSquare(Color.YELLOW));
    currentColor = squares.get(0).getColor();
    
    PropertyChangeListener handler = new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent ev) {
        Color oldColor = currentColor;
        currentColor = (Color) ev.getNewValue();
        fireColorChange(new PropertyChangeEvent(this, "pen color", oldColor, ev.getNewValue()));
      }
    };
    setLayout(new GridLayout(1, 0));
    for (ColorSquare sq : squares) {
      sq.addPropertyChangeListener(handler);
      add(sq);
    }
  }

  public void addPropertyChangeListener(PropertyChangeListener pcl) {
    pcls.add(pcl);
  }

  public void removePropertyChangeListener(PropertyChangeListener pcl) {
    pcls.remove(pcl);
  }

  protected void fireColorChange(PropertyChangeEvent ev) {
    for (PropertyChangeListener pcl : pcls) {
      pcl.propertyChange(ev);
    }
  }

  public Color getCurrentColor() {
    return currentColor;
  }
}
