package org.six11.sf;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import javax.swing.JComponent;

import org.imgscalr.Scalr;
import org.six11.sf.Drag.Event;
import org.six11.util.gui.Components;
import org.six11.util.gui.Strokes;
import org.six11.util.pen.PenEvent;
import org.six11.util.pen.PenListener;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class ScrapGrid extends JComponent implements PenListener, Drag.Listener {

  public static Color BUTTON_REGION_COLOR = new Color(0.35f, 0.35f, 0.35f, 0.7f);

  public static class GridCellContent {
    int pageNum;

    public GridCellContent(int pageNum) {
      this.pageNum = pageNum;
    }
  }

  int sidePadding = 24;
  int topBottom = 48;
  int vertPad = 24;
  int buttonWidth = 48;
  int buttonHeight = 36;
  int chevW = 16;
  int chevH = 8;

  private SkruiFabEditor editor;
  private Page hoverPage;

  public ScrapGrid(SkruiFabEditor editor) {
    setName("ScrapGrid");
    this.editor = editor;
    setBackground(Color.WHITE);
  }

  private Page getCurrentPage() {
    return editor.getModel().getNotebook().getCurrentPage();
  }

  public Image getSelectedThumb() {
    Image ret = null;
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
    Dimension surfaceDim = editor.getModel().getSurface().getSize();
    float aspect = (float) surfaceDim.height / (float) surfaceDim.width;

    Components.antialias(g);
    g.setColor(Color.LIGHT_GRAY);
    g.setStroke(Strokes.VERY_THIN_STROKE);
    int w = getWidth();
    int h = getHeight();

    int innerWidth = w - 2 * sidePadding;
    int pageHeight = (int) ((float) innerWidth * aspect);

    // draw the pages
    int pageNum = 0;
    int pageX = sidePadding;
    int pageY = topBottom;
    Page currentPage = getCurrentPage();
    while (pageY < h) {
      boolean isCurrent = (currentPage.getPageNumber() == pageNum);
      paintPage(g, pageNum, pageX, pageY, vertPad, innerWidth, pageHeight, isCurrent);
      pageNum = pageNum + 1;
      pageY = pageY + vertPad + pageHeight;
    }

    // draw the button regions at top and bottom
    g.setColor(BUTTON_REGION_COLOR);
    g.fillRect(0, 0, w, topBottom);
    g.fillRect(0, h - topBottom, w, topBottom);
    int cx = w / 2;
    int cy = topBottom / 2;
    int bx = cx - buttonWidth / 2;
    int by = cy - buttonHeight / 2;
    g.setStroke(Strokes.THIN_STROKE);
    g.setColor(Color.WHITE);
    g.fillRoundRect(bx, by, buttonWidth, buttonHeight, 16, 16);
    g.setColor(Color.BLACK);
    g.drawRoundRect(bx, by, buttonWidth, buttonHeight, 16, 16);
    g.setStroke(Strokes.BOLD_STROKE);
    drawChevron(g, new Pt(bx + buttonWidth / 2, by + buttonHeight / 2), new Vec(0, -1), chevW,
        chevH);
    cy = h - (topBottom / 2);
    by = cy - buttonHeight / 2;
    g.setStroke(Strokes.THIN_STROKE);
    g.setColor(Color.WHITE);
    g.fillRoundRect(bx, by, buttonWidth, buttonHeight, 16, 16);
    g.setColor(Color.BLACK);
    g.drawRoundRect(bx, by, buttonWidth, buttonHeight, 16, 16);
    g.setStroke(Strokes.BOLD_STROKE);
    drawChevron(g, new Pt(bx + buttonWidth / 2, by + buttonHeight / 2), new Vec(0, 1), chevW, chevH);
  }

  private void drawChevron(Graphics2D g, Pt pt, Vec dir, int chevW, int chevH) {
    int halfW = chevW / 2;
    int halfH = chevH / 2;
    Pt a = pt.getTranslated(dir, halfH);
    Pt d = pt.getTranslated(dir.getFlip(), halfH);
    Vec n = dir.getNormal();
    Pt b = d.getTranslated(n, halfW);
    Pt c = d.getTranslated(n.getFlip(), halfW);
    Path2D path = new Path2D.Float();
    path.moveTo(b.getX(), b.getY());
    path.lineTo(a.getX(), a.getY());
    path.lineTo(c.getX(), c.getY());
    g.draw(path);
  }

  private void paintPage(Graphics2D g, int pageNum, int pageX, int pageY, int vertPad,
      int pageWidth, int pageHeight, boolean isCurrent) {
    Page page = getPage(pageNum);
    page.setRectangle(pageX, pageY + vertPad, pageWidth, pageHeight);
    g.setColor(Color.WHITE);
    g.fillRect(pageX, pageY + vertPad, pageWidth, pageHeight);
    if (page != null && page.getTinyThumb() != null) {
      BufferedImage buf = page.getCachedThumb();
      if (buf == null) {
        bug("Cached image was null. Creating anew.");
        buf = Scalr.resize(page.getTinyThumb(), Math.max(pageWidth, pageHeight));
      } else if (!(buf.getWidth() == pageWidth || buf.getHeight() == pageHeight)) {
        bug("Cached image was the wrong size. created anew.");
        bug("It was: " + buf.getWidth() + " x " + buf.getHeight());
        buf = Scalr.resize(page.getTinyThumb(), Math.max(pageWidth, pageHeight));
        bug("It is now: " + buf.getWidth() + " x " + buf.getHeight());
      }
      page.setCachedThumb(buf);
      int imgX = pageX;
      int imgY = pageY + vertPad;
      Shape priorClip = g.getClip();
      g.setClip(imgX, imgY, pageWidth, pageHeight);
      g.drawImage(buf, imgX, imgY, null);
      g.setClip(priorClip);
    } else {
      String empty = "(blank page)";
      Rectangle2D strBox = g.getFontMetrics().getStringBounds(empty, g);
      int cx = pageX + (pageWidth / 2);
      int cy = pageY + vertPad + (pageHeight / 2);
      int textX = (int) (cx - (strBox.getWidth() / 2));
      int textY = (int) (cy + (strBox.getHeight() / 2));
      g.setColor(Color.LIGHT_GRAY);
      g.drawString(empty, textX, textY);
    }
    if (isCurrent) {
      g.setStroke(Strokes.THIN_STROKE);
      g.setColor(Color.BLUE);
    } else if (page == hoverPage) {
      g.setStroke(Strokes.VERY_THIN_STROKE);
      g.setColor(Color.GREEN);
    } else {
      g.setStroke(Strokes.VERY_THIN_STROKE);
      g.setColor(Color.BLACK);
    }
    g.drawRect(pageX, pageY + vertPad, pageWidth, pageHeight);
  }

  private Page getPage(int pg) {
    Page ret = editor.getModel().getNotebook().getPage(pg);
    if (ret == null) {
      ret = editor.getModel().getNotebook().addPage(pg);
    }
    return ret; 
  }

  @Override
  public void handlePenEvent(PenEvent ev) {
    switch (ev.getType()) {
      case Enter:
        break;
      case Exit:
        break;
      case Hover:
        hover(ev.getPt());
        break;
      case Drag:
        if (editor.getGlass().getActivity() == FastGlassPane.ActivityMode.None) {
          //          select(new Point(ev.getPt().ix(), ev.getPt().iy()));
          //          if (getSelectedCellContent() != null) {
          //            editor.getGlass().setActivity(FastGlassPane.ActivityMode.DragScrap);
          //          }
        }
        break;
      case Down:
        break;
      case Idle:
        break;
      default:
        bug("Unhandled pen event: " + ev.getType());
    }
    repaint();
  }

  private void hover(Pt pt) {
    Page targetPage = getPageAt(pt);
    if (hoverPage != targetPage) {
      hoverPage = targetPage;
      repaint();
    }    
  }

  private Page getPageAt(Pt pt) {
    Page ret = null;
    for (Page p : editor.getModel().getNotebook().getPages()) {
      if (p.getRectangle().contains(pt)) {
        ret = p;
        break;
      }
    }
    return ret;
  }

  @Override
  public void dragMove(Event ev) {
    //    hover(ev.getPt());
  }

  @Override
  public void dragEnter(Event ev) {
  }

  @Override
  public void dragExit(Event ev) {
    hoverPage = null;
    bug("exit while dragging");
  }

  @Override
  public void dragDrop(Event ev) {
    bug("Got drag event for dropping. Re-implement this!");
    switch (editor.getGlass().getActivity()) {
      case DragSelection:
        break;
      case DragScrap:
        break;
      default:
        bug("unhandled state: " + editor.getGlass().getActivity());
    }
    //    clearHover();
    repaint();
  }

  public void clear() {
    //    cells.clear();
    repaint();
  }

  public int getCurrentThumbWidth() {
    return getWidth();// - 2 * sidePadding;
  }

}
