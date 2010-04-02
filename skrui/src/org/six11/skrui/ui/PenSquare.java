package org.six11.skrui.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
public abstract class PenSquare extends JPanel {
  List<PropertyChangeListener> pcls;

  public PenSquare() {
    pcls = new ArrayList<PropertyChangeListener>();
    setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
    addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent ev) {
        go(new Pt(ev));
      }
    });
    addMouseMotionListener(new MouseAdapter() {
      public void mouseDragged(MouseEvent ev) {
        go(new Pt(ev));
      }
    });
    setPreferredSize(new Dimension(50, 80));
    setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
  }

  public abstract void go(Pt pt);
}
