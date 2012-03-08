package org.six11.sf;

import static org.six11.util.Debug.bug;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JPanel;

import org.six11.sf.Drag.Event;
import org.six11.util.gui.BoundingBox;
import org.six11.util.gui.Strokes;
import org.six11.util.pen.PenEvent;
import org.six11.util.pen.PenListener;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.DefaultFontMapper;
import com.lowagie.text.pdf.PdfContentByte;
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

  private boolean dropBorder;
  private SkruiFabEditor editor;
  private Material material;

  public CutfilePane(SkruiFabEditor editor) {
    this.editor = editor;
    this.material = new Material(Material.Units.Inch, CUTFILE_MAX_HEIGHT_INCHES, CUTFILE_MAX_HEIGHT_INCHES);
    setName("CutfilePane");
    setBackground(new Color(250, 240, 200));
    setPreferredSize(new Dimension(300, 200));
  }

  public void paintComponent(Graphics g1) {
    Graphics2D g = (Graphics2D) g1;
    g.setColor(getBackground());
    Rectangle2D vizSize = getVisibleRect();

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
    g.setColor(Color.BLACK);
    g.drawString(material.countStencils() + " stencils", 10, 10);
    BufferedImage im = material.getSmallImage(getWidth(), getHeight());
    if (im != null) {
      g.setColor(Color.LIGHT_GRAY);
      g.fill(new Rectangle2D.Double(0, 0, im.getWidth(), im.getHeight()));
      g.drawImage(im, 0, 0, null);
    }
  }

  private BoundingBox getBoundingBox() {
    return material.getCutBoundingBox();
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
    // TODO Auto-generated method stub

  }

  @Override
  public void dragExit(Event ev) {
    // TODO Auto-generated method stub

  }

  @Override
  public void dragDrop(Event ev) {
    bug("Cutfile got drop");
    switch (ev.getMode()) {
      case DragScrap:
        addStencils(editor.getGrid().getSelectedStencils());
        break;
      case DragSelection:
        addStencils(editor.getModel().getSelectedStencils());
        break;
      case None:
        break;
      default:
        bug("unhandled state: " + ev.getMode());
    }
    repaint();
  }

  private void addStencils(Set<Stencil> selection) {
    for (Stencil s : selection) {
      material.addStencil(s.getShape(true));
    }
    material.layoutStencils();
    try {
      File file = editor.getPdfOutputFile();
      print(editor.getPdfOutputFile());
      bug("Printed to '" + file.getAbsolutePath() + "'");
    } catch (IOException e) {
      e.printStackTrace();
    }
    repaint();
  }

  public void clear() {
    material.clear();
    repaint();
  }

}
