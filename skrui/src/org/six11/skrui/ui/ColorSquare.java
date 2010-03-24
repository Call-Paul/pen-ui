package org.six11.skrui.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class ColorSquare extends JPanel {
  Color color;
  List<PropertyChangeListener> pcls;

  public ColorSquare(Color c) {
    this.color = c;
    pcls = new ArrayList<PropertyChangeListener>();
    setBackground(c);
    setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
    addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        fireColorChange();
      }
    });
    addMouseMotionListener(new MouseAdapter() {
      public void mouseDragged(MouseEvent e) {
        fireColorChange();
      }
    });
    setPreferredSize(new Dimension(50, 50));
  }

  public void addPropertyChangeListener(PropertyChangeListener pcl) {
    pcls.add(pcl);
  }

  public void removePropertyChangeListener(PropertyChangeListener pcl) {
    pcls.remove(pcl);
  }

  protected void fireColorChange() {
    PropertyChangeEvent ev = new PropertyChangeEvent(this, "color", null, color);
    for (PropertyChangeListener pcl : pcls) {
      pcl.propertyChange(ev);
    }
  }

  public Color getColor() {
    return color;
  }

}
