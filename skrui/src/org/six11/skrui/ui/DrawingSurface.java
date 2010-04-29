package org.six11.skrui.ui;

import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.Collections;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.six11.skrui.Main;
import org.six11.util.Debug;
import org.six11.util.gui.Colors;
import org.six11.util.gui.Components;
import org.six11.util.gui.Strokes;
import org.six11.util.pen.DrawingBuffer;

public class DrawingSurface extends JComponent {

  // border and background UI variables
  private Color bgColor;
  private Color penEnabledBorderColor;
  private Color penDisabledBorderColor;
  private double borderPad;
  private boolean penEnabled = false;
  private Main main;

  public DrawingSurface(Main main) {
    this.main = main;
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
    main.getDrawnStuff().addChangeListener(cl);
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
    paintContent(g, true);
    g.setTransform(before);
  }

  public void paintContent(Graphics2D g, boolean useCachedImages) {
    Shape currentSeq = main.getCurrentSequenceShape(); // the in-progress scribble
    List<DrawingBuffer> buffers = main.getDrawnStuff().getDrawingBuffers(); 
    Components.antialias(g);
    for (DrawingBuffer buffer : buffers) {
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
