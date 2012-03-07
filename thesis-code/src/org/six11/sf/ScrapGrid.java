package org.six11.sf;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;
import static org.six11.util.Debug.warn;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.swing.JComponent;

import org.six11.sf.Drag.Event;
import org.six11.util.gui.Components;
import org.six11.util.gui.Strokes;
import org.six11.util.pen.PenEvent;
import org.six11.util.pen.PenListener;
import org.six11.util.pen.Pt;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class ScrapGrid extends JComponent implements PenListener, Drag.Listener {

  public static class GridCellContent {
    Image thumb;
    Set<Stencil> stencils;

    public GridCellContent(Image thumb, Set<Stencil> sourceStencils) {
      this.thumb = thumb;
      this.stencils = new HashSet<Stencil>();
      stencils.addAll(sourceStencils);
    }

    public Set<Stencil> getStencils() {
      return stencils;
    }
  }

  private Color gridColor;
  private Color hoveredColor;
  private Color selectedColor;
  private Color droppedColor;

  private int cellSize = 48;
  private int hoveredCellX = -1;
  private int hoveredCellY = -1;
  private Image hoveredThumb = null;
  private float hoveredCellThickness = 1.4f;
  private int selectedCellX = -1;
  private int selectedCellY = -1;
  private float selectedCellThickness = 2.8f;
  private Point droppedCell = new Point(-1, -1);
  private Map<Point, GridCellContent> cells = new HashMap<Point, GridCellContent>();
  private SkruiFabEditor editor;

  public ScrapGrid(SkruiFabEditor editor) {
    setName("ScrapGrid");
    this.editor = editor;
    setBackground(Color.WHITE);
    gridColor = new Color(240, 240, 240);
    hoveredColor = Color.blue.darker();
    selectedColor = Color.red.darker();
    droppedColor = Color.magenta.darker().darker();
  }

  Point getCell(Point pt) {
    int cellX = pt.x / cellSize;
    int cellY = pt.y / cellSize;
    return new Point(cellX, cellY);
  }

  private GridCellContent getSelectedCellContent() {
    GridCellContent ret = null;
    if (selectedCellX >= 0 && selectedCellY >= 0) {
      ret = cells.get(new Point(selectedCellX, selectedCellY));
    }
    return ret;
  }

  public Image getSelectedThumb() {
    Image ret = null;
    GridCellContent content = getSelectedCellContent();
    if (content != null) {
      ret = content.thumb;
    }
    return ret;
  }

  public Set<Stencil> getSelectedStencils() {
    Set<Stencil> ret = new HashSet<Stencil>();
    GridCellContent content = getSelectedCellContent();
    if (content != null) {
      ret = content.stencils;
    }
    return ret;
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
    Point lastCell = getCell(new Point(w, h));
    Point cursor = new Point();
    for (int cellX = 0; cellX <= lastCell.x; cellX++) {
      for (int cellY = 0; cellY <= lastCell.y; cellY++) {
        cursor.setLocation(cellX, cellY);
        if (cells.containsKey(cursor)) {
          GridCellContent content = cells.get(cursor);
          if (content.thumb == null) {
            warn(this, "thumbnail for " + cellX + ", " + cellY + " is null. this is bad");
          }
          g.drawImage(content.thumb, cellX * cellSize, cellY * cellSize, null);
        }
      }
    }
    for (int i = cellSize; i < w; i += cellSize) {
      g.draw(new Line2D.Double(i, 0, i, h));
    }
    for (int i = cellSize; i < h; i += cellSize) {
      g.draw(new Line2D.Double(0, i, w, i));
    }
    if (hoveredCellX >= 0 && hoveredCellY >= 0) {
      if (hoveredThumb != null) {
        paintThumb(g, hoveredCellX, hoveredCellY, hoveredThumb);
      }
      paintCellBG(g, hoveredColor, hoveredCellX, hoveredCellY, hoveredCellThickness);
    }
    if (selectedCellX >= 0 && selectedCellY >= 0) {
      paintCellBG(g, selectedColor, selectedCellX, selectedCellY, selectedCellThickness);
    }
    if (droppedCell != null && droppedCell.x >= 0 && droppedCell.y >= 0) {
      paintCellBG(g, droppedColor, droppedCell.x, droppedCell.y, selectedCellThickness * 2);
    }

  }

  private void paintThumb(Graphics2D g, int x, int y, Image im) {
    int drawX = cellSize * x;
    int drawY = cellSize * y;
    g.drawImage(im, drawX, drawY, null);
  }

  private void paintCellBG(Graphics2D g, Color c, int x, int y, float t) {
    int drawX = cellSize * x;
    int drawY = cellSize * y;
    g.setColor(c);
    g.setStroke(Strokes.get(t));
    g.draw(new Rectangle(drawX, drawY, cellSize, cellSize));
  }

  @Override
  public void handlePenEvent(PenEvent ev) {
    switch (ev.getType()) {
      case Enter:
        break;
      case Exit:
        clearHover();
        break;
      case Hover:
        hover(new Point(ev.getPt().ix(), ev.getPt().iy()));
        repaint();
        break;
      case Drag:
        if (editor.getGlass().getActivity() == FastGlassPane.ActivityMode.None) {
          select(new Point(ev.getPt().ix(), ev.getPt().iy()));
          if (getSelectedCellContent() != null) {
            editor.getGlass().setActivity(FastGlassPane.ActivityMode.DragScrap);
          }
        }
        break;
      case Down:
        break;
      case Idle:
        break;
      default:
        bug("Unhandled pen event: " + ev.getType());
    }
  }

  private void clearHover() {
    hoveredCellX = -1;
    hoveredCellY = -1;
    repaint();
  }

  private void hover(Point pt) {
    Point cellPoint = getCell(pt);
    hoveredCellX = cellPoint.x;
    hoveredCellY = cellPoint.y;
    repaint();
  }

  private void select(Point pt) {
    Point cellPoint = getCell(pt);
    selectedCellX = cellPoint.x;
    selectedCellY = cellPoint.y;
    repaint();
  }

  @Override
  public void dragMove(Event ev) {
    hover(ev.getPt());
  }

  @Override
  public void dragEnter(Event ev) {
  }

  @Override
  public void dragExit(Event ev) {
    clearHover();
    bug("exit while dragging");
  }

  @Override
  public void dragDrop(Event ev) {
    bug("Got drag event for dropping.");
    switch (editor.getGlass().getActivity()) {
      case DragSelection:
        SketchBook model = editor.getModel();
        if (model.getDraggingThumb() != null) {
          BufferedImage thumb = model.getDraggingThumb();
          GridCellContent content = new GridCellContent(thumb, model.getSelectedStencils());
          Point cell = getCell(ev.getPt());
          cells.put(cell, content);
          bug("Put " + content.getStencils().size() + " stencils in grid location " + num(cell));
        } else {
          bug("damn drag thumb is null:(");
        }
        break;
      case DragScrap:
        GridCellContent content = getSelectedCellContent();
        cells.remove(new Point(selectedCellX, selectedCellY));
        Point cell = getCell(ev.getPt());
        cells.put(cell, content);
        bug("Put " + content.getStencils().size() + " stencils in grid location " + num(cell)
            + " via scrap drag");
        break;
      default:
        bug("unhandled state: " + editor.getGlass().getActivity());
    }
    clearHover();
    repaint();
  }

  public void clear() {
    cells.clear();
    repaint();
  }

  public void clearSelection() {
    selectedCellX = -1;
    selectedCellY = -1;
    repaint();
  }

}
