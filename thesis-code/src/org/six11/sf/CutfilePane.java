package org.six11.sf;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JPanel;

import org.six11.util.gui.BoundingBox;
import org.six11.util.gui.Strokes;
import org.six11.util.pen.PenEvent;
import org.six11.util.pen.PenListener;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.DefaultFontMapper;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class CutfilePane extends JPanel implements GestureListener, PenListener {
  private SkruiFabEditor editor;
  private BoundingBox cutfileBB;
  Set<Stencil> stencils;
  private boolean dropBorder;
  public static double PX_PER_INCH = 1.0 / 72.0;
  public static double CUTFILE_MAX_WIDTH_INCHES = 24.0;
  public static double CUTFILE_MAX_HEIGHT_INCHES = 16.0;

  public CutfilePane(SkruiFabEditor editor) {
    this.editor = editor;
    this.stencils = new HashSet<Stencil>();
    setName("CutfilePane");
    setBackground(new Color(250, 240, 200));
    setPreferredSize(new Dimension(300, 200));
  }

  public void paintComponent(Graphics g1) {
    Graphics2D g = (Graphics2D) g1;
    g.setColor(getBackground());
    Rectangle2D vizSize = getVisibleRect();
    double sx = (vizSize.getWidth() * PX_PER_INCH) / CUTFILE_MAX_WIDTH_INCHES;
    double sy = (vizSize.getHeight() * PX_PER_INCH) / CUTFILE_MAX_HEIGHT_INCHES;
    double scale = Math.min(sx, sy);
    bug("Setting scale: " + num(scale));
    g.fill(getVisibleRect());
    if (dropBorder) {
      float t = 1.5f;
      float t2 = t / 2.0f;
      g.setColor(Color.BLACK);
      g.setStroke(Strokes.get(t));
      Rectangle2D recDst = new Rectangle2D.Double(vizSize.getX() + t2, vizSize.getY() + t2,
          vizSize.getWidth() - t, vizSize.getHeight() - t);
      g.draw(recDst);
    }
    g.setColor(Color.BLUE);
    AffineTransform before = g.getTransform();
    AffineTransform scaleXform = AffineTransform.getScaleInstance(scale, scale);
    g.transform(scaleXform);
    g.setStroke(Strokes.get(2.0f));
    for (Stencil stencil : stencils) {
      Pt origin = stencil.getOrigin();
      bug("Drawing shape with bounding rect: " + stencil.getShape().getBounds2D() + ", origin= "
          + num(origin));
      Point2D scaledOrigin = scaleXform.transform(origin, null);
      g.translate(scaledOrigin.getX(), scaledOrigin.getY());
      g.draw(stencil.getShape());
      g.setTransform(scaleXform);
    }
    g.setTransform(before);
  }

  public void gestureComplete(GestureEvent ev) {
    dropBorder = false;
    repaint();
    Gesture g = ev.getGesture();
    if (g instanceof MoveGesture && g.getComponentStart() instanceof ScrapGrid
        && g.getComponentEnd() == this) {
      bug("You just dropped a scrap on the cutfile pane.");
      MoveGesture mg = (MoveGesture) g;
      List<Ink> selected = editor.getModel().search(mg.getWhere());
      Set<StructuredInk> stencilParts = new HashSet<StructuredInk>();
      for (Ink stroke : selected) {
        if (stroke instanceof UnstructuredInk) {
          UnstructuredInk unstruc = (UnstructuredInk) stroke;
          Sequence seq = unstruc.getSequence();
          stencilParts.addAll(editor.getCornerFinder().findCorners(seq));
        }
      }
      Stencil part = new Stencil(stencilParts);
      stencils.add(part);
      doCutfileLayout();
    }
  }

  private void doCutfileLayout() {
    double xCursor = 0;
    cutfileBB = new BoundingBox();
    for (Stencil stencil : stencils) {
      stencil.setOrigin(xCursor, 0.0);
      cutfileBB.add(stencil.getCutfileBoundingBox());
      xCursor += stencil.getWidth();
    }
    print(new File("cutfile.pdf"));
  }

  private BoundingBox getBoundingBox() {
    return cutfileBB;
  }

  public void gestureStart(GestureEvent ev) {
    // TODO Auto-generated method stub

  }

  public void gestureProgress(GestureEvent ev) {
    dropBorder = (ev.getTargetComponent() == this);
    repaint();
  }

  public void handlePenEvent(PenEvent ev) {

  }

  /**
   * Prints the collection of Stencil objects in their current positions. Before printing, ensure
   * doCutfileLayout() has been called.
   * 
   * @param file
   */
  public void print(File file) {
    // 2. Draw the layers to the pdf graphics context.
    BoundingBox bb = getBoundingBox();
    int w = bb.getWidthInt();
    int h = bb.getHeightInt();
    Rectangle size = new Rectangle(w, h);
    Document document = new Document(size, 0, 0, 0, 0);
    try {
      FileOutputStream out = new FileOutputStream(file);
      PdfWriter writer = PdfWriter.getInstance(document, out);
      document.open();
      DefaultFontMapper mapper = new DefaultFontMapper();
      PdfContentByte cb = writer.getDirectContent();
      PdfTemplate tp = cb.createTemplate(w, h);
      Graphics2D g2 = tp.createGraphics(w, h, mapper);
      g2.setColor(Color.BLUE);
      tp.setWidth(w);
      tp.setHeight(h);
      g2.translate(-bb.getX(), -bb.getY());
      printStencils(g2);
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

  private void printStencils(Graphics2D g) {
    g.setColor(Color.BLUE);
    g.setStroke(new BasicStroke(0.001f));
    for (Stencil stencil : stencils) {
      Pt tx = stencil.origin;
      g.translate(tx.x, tx.y);
      Shape s = stencil.getShape();
      g.draw(stencil.getShape());
      g.translate(-tx.x, -tx.y);
    }
  }

}
