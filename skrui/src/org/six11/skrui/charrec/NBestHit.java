package org.six11.skrui.charrec;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
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

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class NBestHit extends JPanel {

  /**
   * This is the timeout for a click. 600 ms is long, when compared to how long an average mouse
   * click lasts.
   */
  private static final int CLICK_TIMEOUT = 600;

  String label;
  String score;
  boolean scoreIsPercent;
  int labelWidth;
  int confWidth;
  int fontHeight;
  Font labelFont;
  Font confFont;
  int totalWidth;

  // variables dealing with the marching ants
  boolean penHover;
  Stroke[] borderStrokes;
  int antPhase;
  Timer antTimer;

  // variables dealing with marching ants and the pen-friendly click
  boolean down;
  long downTime;
  
  // listeners for psudoclicks
  List<ActionListener> actionListeners;

  public NBestHit(String label, double score, boolean scoreIsPercent) {
    this.scoreIsPercent = scoreIsPercent;
    setData(label, score);
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
      }

      public void mouseExited(MouseEvent ev) {
        penHover = false;
        antTimer.stop();
        repaint();
      }

      public void mousePressed(MouseEvent ev) {
        down = true;
        downTime = System.currentTimeMillis();
        repaint();
      }

      public void mouseReleased(MouseEvent ev) {
        if (down) {
          down = false;
          long now = System.currentTimeMillis();
          // this is in lieu of a mouse click event, which is not dependable with pens
          if ((now - downTime) < CLICK_TIMEOUT) {
            reportClick();
          }
          repaint();
        }
      }
    });
    actionListeners = new ArrayList<ActionListener>();
  }
  
  public void addActionListener(ActionListener al) {
    actionListeners.add(al);
  }

  protected void reportClick() {
    ActionEvent ev = new ActionEvent(this, 0, label);
    for (ActionListener al : actionListeners) {
      al.actionPerformed(ev);
    }
  }

  public void setData(String label, double score) {
    this.label = label;
    if (scoreIsPercent) {
      this.score = Debug.num(100d * score) + "%";
    } else {
      this.score = Debug.num(score);
    }
    this.labelFont = new Font("Monospaced", Font.BOLD, 16);
    FontMetrics fm = getFontMetrics(labelFont);
    labelWidth = fm.stringWidth(label);
    fontHeight = fm.getHeight(); // not totally correct
    this.confFont = new Font("Monospaced", Font.PLAIN, 12);
    fm = getFontMetrics(confFont);
    confWidth = fm.stringWidth(this.score);
    totalWidth = labelWidth + confWidth + 6;
    setPreferredSize(new Dimension(totalWidth + 8, fontHeight + 6));
    revalidate();
    repaint();
  }

  @SuppressWarnings("unused")
  private static void bug(String what) {
    Debug.out("NBestHit", what);
  }

  protected void paintComponent(Graphics g1) {
    Graphics2D g = (Graphics2D) g1;
    Components.antialias(g);
    Rectangle rec = getVisibleRect();
    g.setColor(Color.WHITE);
    g.fill(rec);
    if (penHover) {
      Rectangle2D borderRec = new Rectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1);
      g.setStroke(borderStrokes[antPhase]);
      g.setColor(down ? Color.RED : Color.GREEN);
      g.draw(borderRec);
    }
    double curX = rec.getCenterX() - (totalWidth / 2);
    double curY = rec.getCenterY() + (fontHeight / 2);
    g.setFont(labelFont);
    g.setColor(Color.BLACK);
    g.drawString(label, (float) curX, (float) curY);
    curX = curX + labelWidth + 5;
    g.setFont(confFont);
    g.setColor(Color.GRAY);
    g.drawString(score, (float) curX, (float) curY);
  }

  private void marchAntBorder() {
    antPhase = (antPhase + 1) % borderStrokes.length;
    if (down) {
      if (System.currentTimeMillis() - downTime > CLICK_TIMEOUT) {
        down = false;
      }
    }
    repaint();
  }
}
