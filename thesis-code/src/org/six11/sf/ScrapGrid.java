package org.six11.sf;

import static org.six11.util.Debug.bug;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import javax.swing.JComponent;

import org.imgscalr.Scalr;
import org.six11.sf.Drag.Event;
import org.six11.sf.FastGlassPane.ActivityMode;
import org.six11.util.data.FSM;
import org.six11.util.data.FSM.Transition;
import org.six11.util.gui.Components;
import org.six11.util.gui.Images;
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

  private static final String DRAGGING = "dragging";
  private static final String TAPPING = "tapping";
  private static final String IDLE = "idle";
  private static final String DOWN = "down";
  private static final String MOVE = "move";
  private static final String MOVED_FAR = "moved far";
  private static final String UP = "up";
  protected static final double TAP_SLOP_DIST = 10;
  private static final Color BUTTON_REGION_GLOW_COLOR = new Color(0.44f, 0.64f, 0.88f, 1f);
  public static Color BUTTON_REGION_COLOR = new Color(0.35f, 0.35f, 0.35f, 0.7f);
  public static Color ADD_ME_COLOR = new Color(0.57f, 0.78f, 0.57f);

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

  private FSM fsm;
  private Pt downPt;
  private Pt lastPenPt;
  private Page dragPage;
  private boolean glowTop;
  private boolean glowBottom;
  private Page highlightPage;

  public ScrapGrid(final SkruiFabEditor editor) {
    setName("ScrapGrid");
    this.editor = editor;
    setBackground(Color.WHITE);
    fsm = new FSM("Notebook View FSM");
    fsm.addState(IDLE);
    fsm.setStateEntryCode(IDLE, new Runnable() {
      public void run() {
        downPt = null;
      }
    });
    fsm.addState(TAPPING);
    fsm.addState(DRAGGING);
    fsm.setStateEntryCode(DRAGGING, new Runnable() {
      public void run() {
        // are we really dragging a page? if so, tell glasspane we're dragging.
        dragPage = getPageAt(downPt);
        if (dragPage != null) {
          editor.getGlass().setActivity(ActivityMode.DragPage);
        }
        // At any rate, go back to idle.
        fsm.setState(IDLE);
      }
    });
    fsm.addTransition(new Transition(DOWN, IDLE, TAPPING) {
      public void doBeforeTransition() {
        downPt = lastPenPt.copyXYT();
      }
    });
    fsm.addTransition(new Transition(MOVE, TAPPING, TAPPING) {
      public void doAfterTransition() {
        if (lastPenPt.distance(downPt) > TAP_SLOP_DIST) {
          fsm.addEvent(MOVED_FAR);
        }
      }
    });
    fsm.addTransition(new Transition(MOVED_FAR, TAPPING, DRAGGING));
    fsm.addTransition(new Transition(UP, TAPPING, IDLE) {
      public void doBeforeTransition() {
        tap(downPt);
      }
    });
    fsm.addTransition(new Transition(MOVE, DRAGGING, DRAGGING));
    fsm.addTransition(new Transition(UP, DRAGGING, IDLE));

//    // debugging
//    fsm.addChangeListener(new ChangeListener() {
//      public void stateChanged(ChangeEvent ev) {
//        bug("New state: " + fsm.getState());
//      }
//    });
  }

  protected void tap(Pt pt) {
    bug("Tapping location: " + pt);
    Page target = getPageAt(pt);
    if ((target != editor.getModel().getNotebook().getCurrentPage()) && (target != null)) {
      editor.getModel().getNotebook().setCurrentPage(target);
    }
  }

  private Page getCurrentPage() {
    return editor.getModel().getNotebook().getCurrentPage();
  }

  public Image getSelectedThumb() {
    Image ret = null;
    if (dragPage != null) {
      ret = dragPage.getCachedThumb();
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
    Dimension surfaceDim = editor.getModel().getSurface().getSize();
    float aspect = (float) surfaceDim.height / (float) surfaceDim.width;

    Components.antialias(g);
    g.setColor(Color.LIGHT_GRAY);
    g.setStroke(Strokes.VERY_THIN_STROKE);
    int w = getWidth();
    int h = getHeight();

    int innerWidth = w - (2 * sidePadding);
    int pageHeight = (int) (innerWidth * aspect);

    // draw the pages
    int pageNum = 0;
    int pageX = sidePadding;
    int pageY = topBottom;
    Page currentPage = getCurrentPage();
    while (pageY < h) {
      boolean isCurrent = (currentPage != null) && (currentPage.getPageNumber() == pageNum);
      paintPage(g, pageNum, pageX, pageY, vertPad, innerWidth, pageHeight, isCurrent);
      pageNum = pageNum + 1;
      pageY = pageY + vertPad + pageHeight;
    }

    Color topColor = glowTop ? BUTTON_REGION_GLOW_COLOR : BUTTON_REGION_COLOR;
    Color bottomColor = glowBottom ? BUTTON_REGION_GLOW_COLOR : BUTTON_REGION_COLOR;
    g.setColor(topColor);
    g.fillRect(0, 0, w, topBottom);
    g.setColor(bottomColor);
    g.fillRect(0, h - topBottom, w, topBottom);
    int cx = w / 2;
    int cy = topBottom / 2;
    int bx = cx - (buttonWidth / 2);
    int by = cy - (buttonHeight / 2);
    g.setStroke(Strokes.THIN_STROKE);
    g.setColor(Color.WHITE);
    g.fillRoundRect(bx, by, buttonWidth, buttonHeight, 16, 16);
    g.setColor(bottomColor);
    g.drawRoundRect(bx, by, buttonWidth, buttonHeight, 16, 16);
    g.setStroke(Strokes.BOLD_STROKE);
    g.setColor(topColor);
    drawChevron(g, new Pt(bx + (buttonWidth / 2), by + (buttonHeight / 2)), new Vec(0, -1), chevW,
        chevH);
    cy = h - (topBottom / 2);
    by = cy - (buttonHeight / 2);
    g.setStroke(Strokes.THIN_STROKE);
    g.setColor(Color.WHITE);
    g.fillRoundRect(bx, by, buttonWidth, buttonHeight, 16, 16);
    g.setColor(bottomColor);
    g.drawRoundRect(bx, by, buttonWidth, buttonHeight, 16, 16);
    g.setStroke(Strokes.BOLD_STROKE);
    g.setColor(bottomColor);
    drawChevron(g, new Pt(bx + (buttonWidth / 2), by + (buttonHeight / 2)), new Vec(0, 1), chevW, chevH);
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
    if ((page != null) && (page.getTinyThumb() != null)) {
      BufferedImage buf = page.getCachedThumb();
      if (buf == null) {
        buf = Scalr.resize(page.getTinyThumb(), Math.max(pageWidth, pageHeight));
      } else if (!((buf.getWidth() == pageWidth) || (buf.getHeight() == pageHeight))) {
        buf = Scalr.resize(page.getTinyThumb(), Math.max(pageWidth, pageHeight));
      }
      page.setCachedThumb(buf);
      int imgX = pageX;
      int imgY = pageY + vertPad;
      g.drawImage(buf, imgX, imgY, null);
    } else {
      String empty = "(blank page)";
      Rectangle2D strBox = g.getFontMetrics().getStringBounds(empty, g);
      int cx = pageX + (pageWidth / 2);
      int cy = pageY + vertPad + (pageHeight / 2);
      int textX = (int) (cx - (strBox.getWidth() / 2));
      int textY = (int) (cy + (strBox.getHeight() / 2));
      if (page == hoverPage) {
        g.setColor(BUTTON_REGION_GLOW_COLOR);
      } else {
        g.setColor(Color.LIGHT_GRAY);
      }
      g.drawString(empty, textX, textY);
    }
    if (isCurrent) {
      g.setStroke(Strokes.MEDIUM_STROKE);
      g.setColor(BUTTON_REGION_GLOW_COLOR);
    } else if (page == hoverPage) {
      g.setStroke(Strokes.VERY_THIN_STROKE);
      g.setColor(BUTTON_REGION_GLOW_COLOR);
    } else {
      g.setStroke(Strokes.VERY_THIN_STROKE);
      g.setColor(Color.BLACK);
    }
    g.drawRect(pageX, pageY + vertPad, pageWidth, pageHeight);
    if (page == highlightPage) {
      editor.getGlass().drawAddMeSign(g, pageX + 2, pageY + vertPad + 2, 24, ADD_ME_COLOR,
          Color.BLACK);
    }
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
    lastPenPt = ev.getPt();
    highlightPage = null;
    switch (ev.getType()) {
      case Enter:
        break;
      case Exit:
        hoverPage = null;
        glowBottom = false;
        glowTop = false;
        break;
      case Hover:
        hover(ev.getPt());
        break;
      case Drag:
        //        hoverPage = null;
        //        if (editor.getGlass().getActivity() == FastGlassPane.ActivityMode.DragPage) {
        //          highlightTarget(ev.getPt());
        //        } 
        fsm.addEvent(MOVE);
        break;
      case Down:
        fsm.addEvent(DOWN);
        break;
      case Idle:
        fsm.addEvent(UP);
        break;
      case Flow:
      case Tap:
      default:
        bug("Unhandled pen event: " + ev.getType());
    }
    repaint();
  }

  void highlightTarget(Point pt) {
    highlightPage = getPageAt(pt);
    repaint();
  }

  private void hover(Pt pt) {
    // if we are hovering over the top/bottom areas, light them up and null out the hoverPage
    if ((pt.getY() < topBottom) || (pt.getY() > (getHeight() - topBottom))) {
      hoverPage = null;
      glowTop = pt.getY() < topBottom;
      glowBottom = pt.getY() > (getHeight() - topBottom);
    } else {
      glowBottom = false;
      glowTop = false;
      Page targetPage = getPageAt(pt);
      if (hoverPage != targetPage) {
        hoverPage = targetPage;
      }
    }
    repaint();
  }

  private Page getPageAt(Point2D pt) {
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
    hoverPage = null;
    if (editor.getGlass().getActivity() == FastGlassPane.ActivityMode.DragPage) {
      highlightTarget(ev.getPt());
    }
  }

  @Override
  public void dragEnter(Event ev) {
  }

  @Override
  public void dragExit(Event ev) {
    hoverPage = null;
    glowBottom = false;
    glowTop = false;
    highlightPage = null;
    repaint();
    bug("exit while dragging");
  }

  @Override
  public void dragDrop(Event ev) {
    bug("Got drag event for dropping. Re-implement this!");
    switch (editor.getGlass().getActivity()) {
      case DragSelection:
        break;
      case DragPage:
        Page targetPage = getPageAt(ev.getPt());
        if ((dragPage != null) && (targetPage != null)) {
          copy(dragPage, targetPage);
          editor.getModel().getNotebook().setCurrentPage(targetPage);
        }
        break;
      default:
        bug("unhandled state: " + editor.getGlass().getActivity());
    }
    repaint();
  }

  private void copy(Page src, Page dest) {
    Snapshot topSnap = src.getSnapshotMachine().getCurrent();
    dest.getSnapshotMachine().push(topSnap);
    //    dest.setCachedThumb(Images.deepCopy(src.getCachedThumb()));
    dest.setTinyThumb(Images.deepCopy(src.getTinyThumb()));
  }

  public void clear() {
    repaint();
  }

  public int getCurrentThumbWidth() {
    return getWidth();
  }

  public Page getDragPage() {
    return dragPage;
  }

}
