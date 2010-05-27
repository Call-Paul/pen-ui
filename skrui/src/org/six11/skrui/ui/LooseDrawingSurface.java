package org.six11.skrui.ui;

import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

import org.six11.skrui.shape.Stroke;
import org.six11.util.Debug;
import org.six11.util.gui.ApplicationFrame;
import org.six11.util.gui.Colors;
import org.six11.util.gui.Components;
import org.six11.util.gui.Strokes;
import org.six11.util.pen.MouseThing;
import org.six11.util.pen.Pt;

public class LooseDrawingSurface extends JComponent {

  public static void main(String[] args) {
    ApplicationFrame af = new ApplicationFrame("LooseDrawingSurface Test");
    LooseDrawingSurface lds = new LooseDrawingSurface();
    af.setLayout(new BorderLayout());
    af.add(lds, BorderLayout.CENTER);
    af.setSize(600, 600);
    Components.centerComponent(af);
    af.setVisible(true);
  }

  public static String PEN_STROKE_FINISHED = "pen stroke finished";

  // border and background UI variables
  private Color bgColor;
  private Color penEnabledBorderColor;
  private Color penDisabledBorderColor;
  private double borderPad;
  private boolean penEnabled = false;
  Stroke currentStroke = null;
  GeneralPath currentStrokeGP = null;
  List<Stroke> allStrokes;
  List<GeneralPath> allStrokePaths;
  BasicStroke borderStroke;

  /**
   * Make a drawing surface that lets you draw with one pen (color/thickness). To find out when pen
   * strokes have been completed, attach a property change listener and listen to the property
   * PEN_STROKE_FINISHED.
   */
  public LooseDrawingSurface() {
    // establish border and background variables
    bgColor = Colors.getDefault().get(Colors.BACKGROUND);
    penEnabledBorderColor = Colors.getDefault().get(Colors.ACCENT);
    penDisabledBorderColor = Colors.getDefault().get(Colors.SELECTED_BG_INACTIVE);
    borderPad = 3.0;
    allStrokes = new ArrayList<Stroke>();
    allStrokePaths = new ArrayList<GeneralPath>();

    MouseThing maus = new MouseThing() {

      public void mousePressed(MouseEvent ev) {
        addRawInputBegin(ev.getX(), ev.getY(), ev.getWhen());
      }

      public void mouseDragged(MouseEvent ev) {
        addRawInputProgress(ev.getX(), ev.getY(), ev.getWhen());
      }

      public void mouseReleased(MouseEvent ev) {
        addRawInputEnd();
      }

    };
    addMouseMotionListener(maus);
    addMouseListener(maus);
    borderStroke = Strokes.THIN_STROKE;
  }
  
  public void setBorder(BasicStroke str) {
    borderStroke = str;
    repaint();
  }

  protected void addRawInputEnd() {
    allStrokes.add(currentStroke);
    allStrokePaths.add(currentStrokeGP);
    repaint();
    firePropertyChange(PEN_STROKE_FINISHED, allStrokes.size() - 1, allStrokes.size());
  }

  protected void addRawInputProgress(int x, int y, long when) {
    Pt newPt = new Pt(x, y, when);
    if (currentStroke.size() == 0 || !currentStroke.getLast().isSameLocation(newPt)) {
      currentStroke.add(newPt);
      if (currentStroke.size() == 1) {
        currentStrokeGP = new GeneralPath();
        currentStrokeGP.moveTo((float) newPt.x, (float) newPt.y);
      } else {
        currentStrokeGP.lineTo((float) newPt.x, (float) newPt.y);
      }
    }
    repaint();
  }

  protected void addRawInputBegin(int x, int y, long when) {
    currentStroke = new Stroke();
    addRawInputProgress(x, y, when);
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
    paintContent(g);
    g.setTransform(before);
  }

  public void paintContent(Graphics2D g) {
    Components.antialias(g);
    g.setColor(Color.BLACK);
    g.setStroke(Strokes.get(4.5f));
    for (GeneralPath gp : allStrokePaths) {
      g.draw(gp);
    }
    if (currentStrokeGP != null) {
      g.draw(currentStrokeGP);
    }
  }

  @SuppressWarnings("unused")
  private void bug(String what) {
    Debug.out("LooseDrawingSurface", what);
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
    if (borderStroke != null) {
      g.setStroke(borderStroke);
      g.setColor(penEnabled ? penEnabledBorderColor : penDisabledBorderColor);
      g.draw(rec);
    }
  }

  public List<Stroke> getStrokes() {
    List<Stroke> ret = new ArrayList<Stroke>();
    ret.addAll(allStrokes);
    return ret;
  }

  public void clear() {
    allStrokes = new ArrayList<Stroke>();
    allStrokePaths = new ArrayList<GeneralPath>();
    currentStroke = null;
    currentStrokeGP = null;
    repaint();
  }

}
