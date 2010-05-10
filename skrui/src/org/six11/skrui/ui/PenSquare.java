package org.six11.skrui.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

import org.six11.util.Debug;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Pt;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public abstract class PenSquare extends JPanel {
  protected DrawingBuffer db;
  protected Font font = new Font("sansserif", Font.PLAIN, 9);
  private List<PropertyChangeListener> pcls;

  public PenSquare() {
    pcls = new ArrayList<PropertyChangeListener>();
    setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
    addMouseListener(new MouseAdapter() {

      public void mouseReleased(MouseEvent e) {
        up();
      }

      public void mousePressed(MouseEvent ev) {
        down();
        go(new Pt(ev));
      }

      public void mouseClicked(MouseEvent ev) {
        down();
        go(new Pt(ev));
      }
    });
    addMouseMotionListener(new MouseAdapter() {
      public void mouseDragged(MouseEvent ev) {
        go(new Pt(ev));
      }
    });
    addComponentListener(new ComponentAdapter() {
      public void componentResized(ComponentEvent e) {
        whackUI();
      }
    });
    setPreferredSize(new Dimension(50, 80));
    setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
  }

  protected void initDB() {
    // subclasses that have a UI should (re)instantiate 'db' and load it up with something groovy
  }

  protected void whackUI() {
    db = null;
    repaint();
  }

  @Override
  public void paintComponent(Graphics g) {
    if (db == null) {
      initDB();
    }
    Graphics2D g2 = (Graphics2D) g;
    g2.setPaint(getBackground());
    g2.fill(getVisibleRect());
    if (this instanceof UndoSquare) {
      bug("paintComponent... db null ?" + (db == null));
    }
    if (db != null) {
      db.paste(g2);
    }
  }
  
  @SuppressWarnings("unused")
  private static void bug(String what) {
    Debug.out("PenSquare", what);
  }

  protected void firePCE(PropertyChangeEvent ev) {
    for (PropertyChangeListener pcl : pcls) {
      pcl.propertyChange(ev);
    }
  }

  public void addPropertyChangeListener(PropertyChangeListener pcl) {
    pcls.add(pcl);
  }

  public void removePropertyChangeListener(PropertyChangeListener pcl) {
    pcls.remove(pcl);
  }

  public abstract void up();

  public abstract void go(Pt pt);

  public abstract void down();
}
