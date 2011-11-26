package org.six11.sf;

import static org.six11.util.Debug.bug;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import javax.swing.JComponent;

import org.six11.sf.rec.RecognizedItem;
import org.six11.util.gui.BoundingBox;
import org.six11.util.gui.Components;
import org.six11.util.gui.Strokes;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.PenEvent;
import org.six11.util.pen.PenListener;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.solve.Constraint;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.DefaultFontMapper;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

@SuppressWarnings("serial")
public class DrawingBufferLayers extends JComponent implements PenListener {

  public final static Color DEFAULT_COLOR = Color.BLACK;
  public final static float DEFAULT_THICKNESS = 1.8f;
  List<PenListener> penListeners;
  private Color bgColor = Color.WHITE;
  private Color penEnabledBorderColor = Color.GREEN;
  private double borderPad = 2.0;
  private List<DrawingBuffer> layers;
  private Map<String, DrawingBuffer> layersByName;
  SketchBook model;

  Pt prev;
  GeneralPath currentScribble;
  private Pt hoverPt;

  public DrawingBufferLayers(SketchBook model) {
    this.model = model;
    setName("DrawingBufferLayers");
    layers = new ArrayList<DrawingBuffer>();
    layersByName = new HashMap<String, DrawingBuffer>();
    penListeners = new ArrayList<PenListener>();
  }

  public void clearScribble() {
    currentScribble = null;
    repaint();
  }

  public void addPenListener(PenListener pl) {
    if (!penListeners.contains(pl)) {
      penListeners.add(pl);
    }
  }

  public void paintComponent(Graphics g1) {
    Graphics2D g = (Graphics2D) g1;
    AffineTransform before = new AffineTransform(g.getTransform());
    drawBorderAndBackground(g);
    g.setTransform(before);
    paintContent(g, true);
    g.setTransform(before);
  }

  public void paintContent(Graphics2D g, boolean useCachedImages) {
    Components.antialias(g);
    drawConstraints();
    for (DrawingBuffer buffer : layers) {
      if (buffer.isVisible() && useCachedImages) {
        buffer.paste(g);
      } else if (buffer.isVisible()) {
        buffer.drawToGraphics(g);
      }
    }
    if (currentScribble != null) {
      g.setColor(DEFAULT_COLOR);
      float thick = DEFAULT_THICKNESS;
      g.setStroke(Strokes.get(thick));
      g.draw(currentScribble);
    }
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
    g.setColor(penEnabledBorderColor);
    g.draw(rec);
  }

  //  protected void fire(PenEvent pev) {
  //    for (PenListener pl : penListeners) {
  //      pl.handlePenEvent(pev);
  //    }
  //  }

  /**
   * Make a new layer with a specific key (e.g. "dots"), a human-readable name (e.g.
   * "segment junctions"), a z-depth, and a default visibility setting. Layers with lower z-values
   * are painted first. This means the 'top' layer has the largest z-value. When two layers have the
   * same z-value, it is undefined which order they will be painted in.
   */
  public DrawingBuffer createLayer(String key, String humanName, int z, boolean visible) {
    DrawingBuffer db = new DrawingBuffer();
    db.setHumanReadableName(humanName);
    db.setComplainWhenDrawingToInvisibleBuffer(false);
    layersByName.put(key, db);
    db.setLayer(z);
    layers.add(db);
    Collections.sort(layers, DrawingBuffer.sortByLayer);
    db.setVisible(visible);
    return db;
  }

  public DrawingBuffer getLayer(String name) {
    if (!layersByName.containsKey(name)) {
      int z = 0;
      try {
        z = Integer.parseInt(name);
      } catch (NumberFormatException ex) {

      }
      createLayer(name, "Layer " + name, z, true);
    }
    return layersByName.get(name);
  }

  public BoundingBox getBoundingBox() {
    BoundingBox ret = new BoundingBox();
    for (DrawingBuffer db : layers) {
      if (db.isVisible() && db.hasContent()) {
        ret.add(db.getBoundingBox());
      }
    }
    return ret;
  }

  /**
   * Non-interactively make a PDF of the whole sketch canvas. The filename is based on today's date.
   */
  public void print() {
    File file = null;
    int fileCounter = 0;
    Date now = new Date();
    SimpleDateFormat df = new SimpleDateFormat("MMMdd");
    String today = df.format(now);
    File parentDir = new File("screenshots");
    boolean made = true;
    if (!parentDir.exists()) {
      made = parentDir.mkdir();
    }
    if (made) {
      while (file == null || file.exists()) {
        file = new File(parentDir, today + "-" + fileCounter + ".pdf");
        fileCounter++;
      }
      print(file); // defer to other print function.
    }
  }

  /**
   * Print the whole sketch canvas to the given file.
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
      tp.setWidth(w);
      tp.setHeight(h);
      g2.translate(-bb.getX(), -bb.getY());
      paintContent(g2, false);
      g2.dispose();
      cb.addTemplate(tp, 0, 0);
    } catch (DocumentException ex) {
      bug(ex.getMessage());
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    document.close();
    System.out.println("Wrote " + file.getAbsolutePath());
  }

  public void handlePenEvent(PenEvent ev) {
    switch (ev.getType()) {
      case Down:
        hoverPt = null;
        prev = ev.getPt();
        currentScribble = new GeneralPath();
        currentScribble.moveTo(prev.getX(), prev.getY());
        model.startScribble(ev.getPt());
        break;
      case Drag:
        hoverPt = null;
        Pt here = ev.getPt();
        currentScribble.lineTo(here.getX(), here.getY());
        model.addScribble(ev.getPt());
        break;
      case Idle:
        Sequence seq = model.endScribble(ev.getPt());
        model.addInk(new Ink(seq));
        clearScribble();
        break;
      case Enter:
        break;
      case Exit:
        hoverPt = null;
        break;
      case Hover:
        hoverPt = ev.getPt().copyXYT();
        break;
    }
    repaint();
  }

  private void drawConstraints() {
    DrawingBuffer buf = getLayer(GraphicDebug.DB_CONSTRAINT_LAYER);
    buf.clear();
    for (Constraint c : model.getConstraints().getConstraints()) {
      RecognizedItem item = model.getConstraintItem(c);
      if (item != null) {
        item.getTemplate().draw(c, item, buf, getHoverPoint());
      }
    }
  }

  /**
   * Returns the last location the pen was hovering, or null if the pen is down or outside of the
   * component.
   */
  public Pt getHoverPoint() {
    return hoverPt;
  }

  /**
   * Tells you if the hover point is currently valid.
   */
  public boolean isHovering() {
    return (hoverPt != null);
  }

  public void clearAllBuffers() {
    for (DrawingBuffer buf : layers) {
      buf.clear();
    }
    repaint();
  }

}
