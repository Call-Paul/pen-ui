package org.six11.sf;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RectangularShape;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Set;

import javax.swing.JPanel;

import org.six11.sf.Drag.Event;
import org.six11.util.gui.BoundingBox;
import org.six11.util.gui.Strokes;
import org.six11.util.pen.PenEvent;
import org.six11.util.pen.PenListener;
import org.six11.util.pen.Pt;
import org.six11.util.solve.ConstraintSolver.Listener;
import org.six11.util.solve.ConstraintSolver.State;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.pdf.DefaultFontMapper;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class CutfilePane extends JPanel implements PenListener, Drag.Listener {

  public static double CUTFILE_MAX_WIDTH_INCHES = 24.0;
  public static double CUTFILE_MAX_HEIGHT_INCHES = 16.0;

  private SkruiFabEditor editor;
  private Material material;
  private boolean addMe;
  private Rectangle clearRect;
  private boolean hoverInClear;
  private Rectangle makeRect;
  private boolean hoverInMake;
  private File recentFile;
  private String message;

  // when adding stencils sometimes the constraint solver has to wake up. use this listener to wait for it to
  // calm down. this delays printing until the solver is happy.
  private Listener lis;

  public CutfilePane(SkruiFabEditor editor) {
    this.editor = editor;
    this.material = new Material(Material.Units.Inch, CUTFILE_MAX_HEIGHT_INCHES,
        CUTFILE_MAX_HEIGHT_INCHES);
    setName("CutfilePane");
    setBackground(Color.LIGHT_GRAY);
    setPreferredSize(new Dimension(300, 200));
  }

  public void paintComponent(Graphics g1) {
    Graphics2D g = (Graphics2D) g1;
    int w = getWidth();
    int h = getHeight();
    BufferedImage im = material.getSmallImage(w, h);
    if (message == null && im != null) {
      g.setColor(Color.LIGHT_GRAY);
      g.fill(new Rectangle2D.Double(0, 0, im.getWidth(), im.getHeight()));
      g.drawImage(im, 0, 0, null);
    } else {
      g.setColor(getBackground());
      g.fill(getVisibleRect());
      g.setColor(Color.BLACK);
      FontMetrics fm = g.getFontMetrics();
      String str = message == null ? "(Drag Stencils Here)" : message;
      Rectangle2D r = fm.getStringBounds(str, g);
      int cx = w / 2;
      int cy = h / 2;
      int textX = (int) (cx - (r.getWidth() / 2));
      int textY = (int) (cy + (r.getHeight() / 2));
      g.drawString(str, textX, textY);
    }
    if (addMe) {
      editor.getGlass().drawAddMeSign(g, 4, 4, 24, ScrapGrid.ADD_ME_COLOR, Color.BLACK);
    }
    // draw clear button
    clearRect = placeButton(g, "Clear", 40, 24, 0, hoverInClear);
    
    // if there are stencils, draw the 'Make' button
    if (material.countStencils() > 0) {
      makeRect = placeButton(g, "Make", 40, 24, 1, hoverInMake);
    } else {
      makeRect = null;
    }

  }

  public Rectangle placeButton(Graphics2D g, String str, int buttonW, int buttonH, int position,
      boolean lightUp) {
    int w = getWidth();
    Rectangle2D strBounds = g.getFontMetrics().getStringBounds(str, g);
    int buttonPad = 4;
    //    int buttonW = (int) (2 * buttonPad + strBounds.getWidth());

    //    int buttonH = (int) (2 * buttonPad + strBounds.getHeight());
    int y = ((1 + position) * buttonPad) + (position * buttonH);
    Rectangle buttonRect = new Rectangle(w - (buttonPad + buttonW), y, buttonW, buttonH);
    int strX = (int) (buttonRect.getCenterX() - (strBounds.getWidth() / 2));
    int strY = (int) (buttonRect.getCenterY() + (strBounds.getHeight() / 2) - buttonPad / 2);
    g.setColor(Color.LIGHT_GRAY);
    g.fill(buttonRect);
    if (lightUp) {
      g.setColor(ScrapGrid.BUTTON_REGION_GLOW_COLOR);
    } else {
      g.setColor(Color.GRAY);
    }
    g.setStroke(Strokes.THIN_STROKE);
    g.draw(buttonRect);
    g.drawString(str, strX, strY);
    return buttonRect;
  }

  private BoundingBox getBoundingBox() {
    return material.getCutBoundingBox();
  }

  public void handlePenEvent(PenEvent ev) {
    switch (ev.getType()) {
      case Down:
        break;
      case Drag:
        break;
      case Enter:
        break;
      case Exit:
        hoverInClear = false;
        repaint();
        break;
      case Flow:
        break;
      case Hover:
        hoverInClear = whackBox(ev.getPt(), clearRect);
        hoverInMake = whackBox(ev.getPt(), makeRect);
        repaint();
        break;
      case Idle:
        if (clearRect != null && clearRect.contains(ev.getPt())) {
          clear();
        }
        if (makeRect != null && makeRect.contains(ev.getPt())) {
          chooseMakeType();
        }
        break;
      case Tap:
        break;
    }
  }

  private void chooseMakeType() {
    // printRequested();
    printPonoko();
  }
  
  private boolean whackBox(Pt pt, Rectangle2D box) {
    boolean ret = false;
    if (box != null && box.contains(pt)) {
      ret = true;
    }
    return ret;
  }

  private void clear() {
    material.clear();
    repaint();
  }

  /**
   * Prints the collection of Stencil objects in their current positions. Before printing, ensure
   * doCutfileLayout() has been called.
   * 
   * @param file
   */
  public void print(File file) {
    BoundingBox bb = getBoundingBox();
    int w = bb.getWidthInt() + 4; // give a little padding because strokes sometimes are right up against the edge
    int h = bb.getHeightInt() + 4;
    com.lowagie.text.Rectangle size = new com.lowagie.text.Rectangle(w, h);
    Document document = new Document(size, 0, 0, 0, 0);
    try {
      FileOutputStream out = new FileOutputStream(file);
      PdfWriter writer = PdfWriter.getInstance(document, out);
      writer.addViewerPreference(PdfName.PRINTSCALING, PdfName.NONE);
      document.open();
      DefaultFontMapper mapper = new DefaultFontMapper();
      PdfContentByte cb = writer.getDirectContent();
      PdfTemplate tp = cb.createTemplate(w, h);
      Graphics2D g2 = tp.createGraphics(w, h, mapper);
      g2.translate(2, 2); // give a little offset so lines aren't off the edge
      g2.setColor(Color.BLUE);
      tp.setWidth(w);
      tp.setHeight(h);
      material.drawStencils(g2, Color.BLUE, "outline");
      g2.dispose();
      cb.addTemplate(tp, 0, 0);
    } catch (DocumentException ex) {
      bug(ex.getMessage());
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    document.close();
    bug("Wrote " + file.getAbsolutePath());
  }

  @Override
  public void dragMove(Event ev) {
    // TODO Auto-generated method stub

  }

  @Override
  public void dragEnter(Event ev) {
    addMe = true;
  }

  @Override
  public void dragExit(Event ev) {
    addMe = false;
  }

  @Override
  public void dragDrop(Event ev) {
    switch (ev.getMode()) {
      case DragPage:
        Page dragPage = editor.getGrid().getDragPage();
        if (dragPage != null) {
          bug("OK, dragpage is fine. setting it current.");
          editor.getModel().getNotebook().setCurrentPage(dragPage);
          addCurrentWhenChilledOut();
        }
        break;
      case DragSelection:
        bug("Dropping selected stencils: " + editor.getModel().getSelectedStencils());
        addStencils(editor.getModel().getSelectedStencils());
        break;
      case None:
        break;
      default:
        bug("unhandled state: " + ev.getMode());
    }
    addMe = false;
    repaint();
  }

  private void addCurrentWhenChilledOut() {
    if (editor.getModel().getConstraints().getSolutionState() == State.Solved) {
      addStencils(editor.getModel().getStencils());
    } else {
      message = "Wait for constraint solver...";
      lis = new Listener() {
        public void constraintStepDone(State state, int numIterations, double err, int numPoints,
            int numConstraints) {
          if (state == State.Solved) {
            editor.getModel().getConstraints().removeListener(lis);
            message = null;
            bug("Place " + editor.getModel().getStencils().size() + " stencils in the cutfile.");
            addStencils(editor.getModel().getStencils());
          }
        }
      };
      editor.getModel().getConstraints().addListener(lis);
    }
  }

  private void addStencils(Set<Stencil> selection) {
    for (Stencil s : selection) {
      material.addStencil(s.getShape(true));
    }
    material.layoutStencils();
    repaint();
  }
  
  private void printPonoko() {
    material.drawStencilsSVG();
  }

  private void printRequested() {
    try {
      File file = editor.getPdfOutputFile();
      print(file);
      bug("Printed to '" + file.getAbsolutePath() + "'");
      recentFile = file;
      Desktop.getDesktop().open(recentFile);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}
