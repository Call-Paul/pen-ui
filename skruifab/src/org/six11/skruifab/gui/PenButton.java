package org.six11.skruifab.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.Timer;

import org.six11.util.Debug;
import org.six11.util.gui.Components;

public class PenButton extends JPanel {

  /**
   * This is the timeout for a click. 600 ms is long, when compared to how long an average mouse
   * click lasts.
   */
  private static final int CLICK_TIMEOUT = 600;

  // Button's label and action command
  String label;

  //variables dealing with the marching ants
  boolean penHover;
  Stroke[] borderStrokes;
  int antPhase;
  Timer antTimer;
  boolean down;
  long downTime;

  // listeners for psudoclicks
  List<ActionListener> actionListeners;

  public PenButton(String label) {
    this.label = label;
    int antSize = 3;
    antTimer = new Timer(1000 / 10, new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        marchAntBorder();
      }
    });
    antTimer.setRepeats(true);
    borderStrokes = new Stroke[antSize * 2];
    for (int i = 0; i < borderStrokes.length; i++) {
      borderStrokes[i] = new BasicStroke(1.4f, // pen thickness
          BasicStroke.CAP_BUTT, // cap
          BasicStroke.JOIN_MITER, // join
          1f, // miter limit
          new float[] {
              antSize, antSize
          }, // dash
          i); // dash phase
    }
    addMouseListener(new MouseAdapter() {
      public void mouseEntered(MouseEvent ev) {
        penHover = true;
        antTimer.restart();
        getParent().repaint();
      }

      public void mouseExited(MouseEvent ev) {
        penHover = false;
        antTimer.stop();
        getParent().repaint();
      }

      public void mousePressed(MouseEvent ev) {
        down = true;
        downTime = System.currentTimeMillis();
        getParent().repaint();
      }

      public void mouseReleased(MouseEvent ev) {
        if (down) {
          down = false;
          long now = System.currentTimeMillis();
          // this is in lieu of a mouse click event, which is not dependable with pens
          if ((now - downTime) < CLICK_TIMEOUT) {
            reportClick();
          }
          getParent().repaint();
        }
      }
    });
    actionListeners = new ArrayList<ActionListener>();
  }

  public void addActionListner(ActionListener al) {
    if (!actionListeners.contains(al)) {
      actionListeners.add(al);
    }
  }
  
  protected void reportClick() {
    ActionEvent ev = new ActionEvent(this, 0, label);
    for (ActionListener al : actionListeners) {
      al.actionPerformed(ev);
    }
  }

  protected void paintComponent(Graphics g1) {
    Graphics2D g = (Graphics2D) g1;
    Rectangle rec = getBounds();
    g.setClip(getBounds());
    Components.antialias(g);
    g.setColor(Color.WHITE);
    g.fill(rec);
    Rectangle2D borderRec = new Rectangle2D.Double(getLocation().getX(), getLocation().getY(),
        getWidth() - 1, getHeight() - 1);
    g.setStroke(borderStrokes[antPhase]);
    if (penHover) {
      g.setColor(down ? Color.RED : Color.GREEN);
    } else {
      g.setColor(Color.LIGHT_GRAY);
    }
    g.draw(borderRec);
  }

  @SuppressWarnings("unused")
  private static void bug(String what) {
    Debug.out("PenButton", what);
  }

  private void marchAntBorder() {
    antPhase = (antPhase + 1) % borderStrokes.length;
    if (down) {
      if (System.currentTimeMillis() - downTime > CLICK_TIMEOUT) {
        down = false;
      }
    }
    getParent().repaint();
  }
}
