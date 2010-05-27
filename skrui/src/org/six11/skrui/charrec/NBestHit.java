package org.six11.skrui.charrec;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;

import javax.swing.JPanel;
import javax.swing.Timer;

import org.six11.util.Debug;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class NBestHit extends JPanel {

  private static final int CLICK_TIMEOUT = 600;
  String label;
  String confidence;
  int labelWidth;
  int confWidth;
  int fontHeight;
  Font labelFont;
  Font confFont;
  int totalWidth;
  boolean penHover;
  Stroke[] borderStrokes;
  int antPhase;
  Timer antTimer;
  boolean down;
  long downTime;

  public NBestHit(String label, double confidence) {
    this.label = label;
    this.confidence = Debug.num(100d * confidence) + "%";
    this.labelFont = new Font("Monospaced", Font.BOLD, 16);
    FontMetrics fm = getFontMetrics(labelFont);
    labelWidth = fm.stringWidth(label);
    fontHeight = fm.getHeight(); // not totally correct
    this.confFont = new Font("Monospaced", Font.PLAIN, 12);
    fm = getFontMetrics(confFont);
    confWidth = fm.stringWidth(this.confidence);
    totalWidth = labelWidth + confWidth + 6;
    setPreferredSize(new Dimension(totalWidth + 8, fontHeight + 6));
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
            bug("click");
          }
          repaint();
        }
      }
    });
  }

  private static void bug(String what) {
    Debug.out("NBestHit", what);
  }

  protected void paintComponent(Graphics g1) {
    Graphics2D g = (Graphics2D) g1;
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
    g.drawString(confidence, (float) curX, (float) curY);
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
