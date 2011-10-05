package org.six11.sf;

import java.awt.Color;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.six11.sf.ScrapGrid.GridCellContent;
import org.six11.util.gui.Components;
import org.six11.util.gui.Strokes;
import org.six11.util.layout.FrontEnd;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.PenEvent;
import org.six11.util.pen.PenListener;
import org.six11.util.pen.Sequence;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.warn;
import static org.six11.util.Debug.num;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class ScrapGrid extends JComponent implements PenListener, GestureListener {

  public class GridCellContent {
    Area area;
    Image thumb;

    public GridCellContent(Area area, Image thumb) {
      this.area = area;
      this.thumb = thumb;
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
  private Point prevCellDrag;
  private GridCellContent gestureMoveCell;

  public ScrapGrid(SkruiFabEditor editor) {
    this.editor = editor;
    setName("ScrapGrid");
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

  private void setCellReference(Point cell, GridCellContent content) {
    cells.put(cell, content);
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

  //  public void mouseDragged(MouseEvent ev) {
  //    Point p = getCell(ev.getPoint());
  //    hoveredCellX = p.x;
  //    hoveredCellY = p.y;
  //    repaint();
  //  }
  //
  //  public void mouseClicked(MouseEvent ev) {
  //    Point p = getCell(ev.getPoint());
  //    selectedCellX = p.x;
  //    selectedCellY = p.y;
  //    repaint();
  //  }

  public void gestureComplete(GestureEvent ev) {
    if (ev.getTargetComponent() == this
        && ev.getGesture().getComponentStart() instanceof DrawingBufferLayers) {
      Point p = ev.getComponentPoint();
      bug("drop location in scrap coordinates: " + p.x + ", " + p.y);
      droppedCell = getCell(p);
      Gesture g = ev.getGesture();
      if (g instanceof MoveGesture) {
        MoveGesture mg = (MoveGesture) g;
        Area loc = mg.getWhere();
        Point cell = getCell(p);
        BufferedImage newThumb = new BufferedImage(mg.thumb.getWidth(null),
            mg.thumb.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        SkruiFabEditor.copyImage(mg.thumb, newThumb, 1.0);
        GridCellContent content = new GridCellContent(loc, newThumb);
        setCellReference(cell, content);
      }
      hoveredThumb = null;
      repaint();
    }
    prevCellDrag = null;
  }

  @Override
  public void gestureStart(GestureEvent ev) {
    if (ev.getTargetComponent() == this) {
      bug("Started a gesture in the scrap grid. " + (gestureMoveCell == null ? "GMC null" : "ok"));
      ev.getGesture().setThumbnail(gestureMoveCell.thumb);
      ev.getGesture().setDragImage(true);
    }
  }

  @Override
  public void gestureProgress(GestureEvent ev) {
    if (ev.getTargetComponent() == this
    //        && ev.getGesture().getComponentStart() instanceof DrawingBufferLayers
    ) {
      Point cellPoint = getCell(ev.getComponentPoint());
      hoveredCellX = cellPoint.x;
      hoveredCellY = cellPoint.y;
      hoveredThumb = ev.getGesture().getThumbnail();
    }
  }

  @Override
  public void handlePenEvent(PenEvent ev) {
    Point cellPoint;
    switch (ev.getType()) {
      case Enter:
        handlePenEnter(ev);
        break;
      case Exit:
        hoveredCellX = -1;
        hoveredCellY = -1;
        repaint();
        break;
      case Hover:
        cellPoint = getCell(new Point(ev.getPt().ix(), ev.getPt().iy()));
        hoveredCellX = cellPoint.x;
        hoveredCellY = cellPoint.y;
        repaint();
        break;
      case Drag:
        detectMoveGesture(new Point(ev.getPt().ix(), ev.getPt().iy()));
        break;
      case Idle:
        prevCellDrag = null;
      default:
        bug("Unhandled pen event: " + ev.getType());
    }
  }

  private void detectMoveGesture(Point currentMouseLoc) {
    Point currentCell = getCell(currentMouseLoc);
    if (prevCellDrag != null && !currentCell.equals(prevCellDrag)) {
      GridCellContent content = cells.get(prevCellDrag);
      if (content != null) {
        bug("Move gesture detected starting in scrap grid, cell " + num(prevCellDrag));
        gestureMoveCell = content;
        Gesture gest = new MoveGesture(this, currentMouseLoc, new Sequence(), content.area);
        editor.getModel().getGestures().setGesture(gest);
      }
    }
    prevCellDrag = currentCell;
  }

  private void handlePenEnter(PenEvent ev) {
    SketchBook model = editor.getModel();
    if (model.getGestures().hasActualGesture()) {
      bug("Gesture type on entering scrap grid: " + model.getGestures().getGesture().getHumanName());
      DrawingBuffer copyLayer = model.getLayers().getLayer(GraphicDebug.DB_COPY_LAYER);
      BufferedImage bigImage = copyLayer.getImage();
      double sw = (double) cellSize / (double) bigImage.getWidth();
      double sh = (double) cellSize / (double) bigImage.getHeight();
      double s = Math.min(sw, sh);
      BufferedImage smallImage = new BufferedImage(cellSize, cellSize, BufferedImage.TYPE_INT_ARGB);
      SkruiFabEditor.copyImage(bigImage, smallImage, s);
      model.getGestures().getGesture().setThumbnail(smallImage);
    }
  }

}
