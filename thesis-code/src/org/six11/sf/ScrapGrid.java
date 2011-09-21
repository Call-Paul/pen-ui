package org.six11.sf;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JComponent;

import org.six11.util.gui.Components;
import org.six11.util.gui.Strokes;
import org.six11.util.pen.DrawingBuffer;
import static org.six11.util.Debug.bug;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class ScrapGrid extends JComponent implements MouseMotionListener, MouseListener, GestureListener {

  private Color gridColor;
  private Color hoveredColor;
  private Color selectedColor;
  private Color droppedColor;

  private int cellSize = 48;
  private int hoveredCellX = -1;
  private int hoveredCellY = -1;
  private float hoveredCellThickness = 1.4f;
  private int selectedCellX = -1;
  private int selectedCellY = -1;
  private float selectedCellThickness = 2.8f;
  private Point droppedCell = new Point(-1, -1);
  
  private SketchBook model;

  public ScrapGrid(SketchBook model) {
    this.model = model;
    setName("ScrapGrid");
    setBackground(Color.WHITE);
    gridColor = new Color(240, 240, 240);
    hoveredColor = Color.blue.darker();
    selectedColor = Color.red.darker();
    droppedColor = Color.magenta.darker().darker();
    addMouseMotionListener(this);
    addMouseListener(this);
  }

  Point getCell(Point pt) {
    int cellX = pt.x / cellSize;
    int cellY = pt.y / cellSize;
    return new Point(cellX, cellY);
  }

  public void paintComponent(Graphics g1) {
    Graphics2D g = (Graphics2D) g1;
    AffineTransform before = new AffineTransform(g.getTransform());
    drawBackground(g);
    Components.antialias(g);
    paintContent(g);
    g.setTransform(before);
    drawBorder(g);
  }

  private void drawBackground(Graphics2D g) {
    Rectangle2D rec = new Rectangle2D.Double(0, 0, getWidth() - 2.0, getHeight() - 2.0);
    g.setColor(getBackground());
    g.fill(rec);
  }

  private void drawBorder(Graphics2D g) {
    Rectangle2D rec = new Rectangle2D.Double(0, 0, getWidth() - 2.0, getHeight() - 2.0);
    g.setStroke(Strokes.THIN_STROKE);
    g.setColor(Color.LIGHT_GRAY);
    g.draw(rec);
  }

  public void paintContent(Graphics2D g) {
    Components.antialias(g);
    g.setColor(gridColor);
    g.setStroke(Strokes.VERY_THIN_STROKE);
    int w = getWidth();
    int h = getHeight();
    for (int i = cellSize; i < w; i += cellSize) {
      g.draw(new Line2D.Double(i, 0, i, h));
    }
    for (int i = cellSize; i < h; i += cellSize) {
      g.draw(new Line2D.Double(0, i, w, i));
    }
    if (hoveredCellX >= 0 && hoveredCellY >= 0) {
      paintCellBG(g, hoveredColor, hoveredCellX, hoveredCellY, hoveredCellThickness);
    }
    if (selectedCellX >= 0 && selectedCellY >= 0) {
      paintCellBG(g, selectedColor, selectedCellX, selectedCellY, selectedCellThickness);
    }
    if (droppedCell != null && droppedCell.x >= 0 && droppedCell.y >= 0) {
      paintCellBG(g, droppedColor, droppedCell.x, droppedCell.y, selectedCellThickness * 2);
    }

  }

  private void paintCellBG(Graphics2D g, Color c, int x, int y, float t) {
    int drawX = cellSize * x;
    int drawY = cellSize * y;
    g.setColor(c);
    g.setStroke(Strokes.get(t));
    g.draw(new Rectangle(drawX, drawY, cellSize, cellSize));
  }

  public void mouseDragged(MouseEvent ev) {
    Point p = getCell(ev.getPoint());
    hoveredCellX = p.x;
    hoveredCellY = p.y;
    repaint();
  }

  public void mouseMoved(MouseEvent ev) {
    Point p = getCell(ev.getPoint());
    hoveredCellX = p.x;
    hoveredCellY = p.y;
    repaint();
  }

  public void mouseClicked(MouseEvent ev) {
    Point p = getCell(ev.getPoint());
    selectedCellX = p.x;
    selectedCellY = p.y;
    repaint();
  }

  public void mouseEntered(MouseEvent ev) {

  }

  public void mouseExited(MouseEvent ev) {
    hoveredCellX = -1;
    hoveredCellY = -1;
    repaint();
  }

  public void mousePressed(MouseEvent ev) {

  }

  public void mouseReleased(MouseEvent ev) {
  }

  public void gestureComplete(GestureCompleteEvent gcev) {
    if (gcev.getDragEndComponent() == this) {
      Point p = gcev.getComponentPoint();
      bug("drop location in scrap coordinates: " + p.x + ", " + p.y);
      droppedCell = getCell(p);
      bug(" ** DING ** " + droppedCell.x + ", " + droppedCell.y);
      repaint();
    }
  }

}
