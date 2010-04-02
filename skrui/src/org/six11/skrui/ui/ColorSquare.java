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

import org.six11.util.pen.Pt;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class ColorSquare extends PenSquare {
  Color color;
  

  public ColorSquare(Color c) {
    this.color = c;
   
    setBackground(c);
    
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

  @Override
  public void go(Pt pt) {
    fireColorChange();
  }

}
