package org.six11.skruifab;

import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.six11.skruifab.gui.GraphicMessage;
import org.six11.util.Debug;
import org.six11.util.gui.Colors;
import org.six11.util.gui.Components;
import org.six11.util.gui.Strokes;
import org.six11.util.pen.DrawingBuffer;

/**
 * This is a Swing component capable of handling pen (mouse) input, passing that on to appropriate
 * event handlers, and repainting when necessary. This is the component with the dashed line around
 * the edge, if that helps.
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class DrawingSurface extends JComponent {

  // border and background UI variables
  private Color bgColor;
  private Color penEnabledBorderColor;
  private Color penDisabledBorderColor;
  private double borderPad;
  private boolean penEnabled = false;
  private Main main;
  private List<GraphicMessage> messages;
  private Timer msgTimer;

  public DrawingSurface(Main main) {
    this.main = main;
    this.messages = new ArrayList<GraphicMessage>();
    // establish border and background variables
    bgColor = Colors.getDefault().get(Colors.BACKGROUND);
    penEnabledBorderColor = Colors.getDefault().get(Colors.ACCENT);
    penDisabledBorderColor = Colors.getDefault().get(Colors.SELECTED_BG_INACTIVE);
    borderPad = 3.0;
    SkruiMouseThing maus = new SkruiMouseThing(main);
    addMouseMotionListener(maus);
    addMouseListener(maus);
    ChangeListener cl = new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        repaint();
      }
    };
    main.addChangeListener(cl);
    msgTimer = new Timer(1000, new ActionListener() {
      public void actionPerformed(ActionEvent ev) {
        whackMessageStack();
      }

    });
    msgTimer.stop();
  }

  public void addMessage(final GraphicMessage msg) {
    Runnable r = new Runnable() {
      public void run() {
        messages.add(msg);
        whackMessageStack();
      }
    };
    SwingUtilities.invokeLater(r);
  }

  private void whackMessageStack() {
    synchronized (messages) {
//      bug("whack");
      msgTimer.setDelay(1000 / 15);
      Collection<GraphicMessage> expired = new HashSet<GraphicMessage>();
      for (GraphicMessage msg : messages) {
        if (msg.getAge() > 7000) {
          expired.add(msg);
        }
      }
      messages.removeAll(expired);
      if (messages.size() > 0) {
//        bug("timer ON");
        msgTimer.setRepeats(true);
        msgTimer.start();
      } else {
//        bug("timer OFF");
        msgTimer.stop();
        msgTimer.setRepeats(false);
      }
      repaint();
    }
  }

  /**
   * Draws a border (the characteristic 'this is a sketching surface' visual), and the soup's
   * current sequence and all complete DrawingBuffers.
   */
  public void paintComponent(Graphics g1) {
    Graphics2D g = (Graphics2D) g1;
    AffineTransform before = new AffineTransform(g.getTransform());
    drawBorderAndBackground(g);
    g.setTransform(before);
    paintContent(g, false); // was TRUE. if the world starts burning down change this back
    g.setTransform(before);
    drawMessages(g);
  }

  private void drawMessages(Graphics2D g) {
    synchronized (messages) {
      double x, y;
      double vsep = 8; // vertical distance between messages
      double vpad = 16; // vertical distance from bottom to 1st message
      // x is always hpad + msg width away from right-most edge
      double hpad = 16;
      y = getHeight() - vpad;
      for (GraphicMessage msg : messages) {
        x = getWidth() - (msg.getWidth() + hpad);
        y = y - (msg.getHeight() + vsep);
        paintMessage(g, msg, x, y);
      }
    }
  }

  private void paintMessage(Graphics2D g, GraphicMessage msg, double x, double y) {
    BufferedImage msgImage = msg.getImage();
    long age = msg.getAge();
    float alpha = 1f;
    if (age > 3000) {
      alpha = 1f - ((float) (age - 3000) / (float) 4000);
    }
    float[] scale = new float[] {
        1f, 1f, 1f, alpha
    };
    float[] offset = new float[] {
        0f, 0f, 0f, 0f
    };
    RescaleOp transpOp = new RescaleOp(scale, offset, null);
    g.drawImage(msgImage, transpOp, (int) x, (int) y);
  }

  public void paintContent(Graphics2D g, boolean useCachedImages) {
    Shape currentSeq = main.getCurrentSequenceShape(); // the in-progress scribble
    DrawnStuff stuff = main.getDrawnStuff();
    Components.antialias(g);
    for (DrawingBuffer buffer : stuff.getDrawingBuffers()) {
      if (buffer.isVisible() && useCachedImages) {
        buffer.paste(g);
      } else if (buffer.isVisible()) {
        buffer.drawToGraphics(g);
      }
    }
    if (currentSeq != null && main.isCurrentSequenceShapeVisible()) {
      if (main.getPenColor() != null) {
        g.setColor(main.getPenColor());
      } else {
        g.setColor(DrawingBuffer.getBasicPen().color);
      }
      float thick = (float) main.getPenThickness();
      g.setStroke(Strokes.get(thick));
      g.draw(currentSeq);
    }
  }

  @SuppressWarnings("unused")
  private void bug(String what) {
    Debug.out("DrawingSurface", what);
  }

  /**
   * Paints the background white and adds the characteristic dashed border.
   */
  private void drawBorderAndBackground(Graphics2D g) {
    Components.antialias(g);
    RoundRectangle2D rec = new RoundRectangle2D.Double(borderPad, borderPad, getWidth() - 2.0
        * borderPad, getHeight() - 2.0 * borderPad, 40, 40);
    g.setColor(bgColor);
    g.fill(rec);
    g.setStroke(Strokes.DASHED_BORDER_STROKE);
    g.setColor(penEnabled ? penEnabledBorderColor : penDisabledBorderColor);
    g.draw(rec);
  }
}
