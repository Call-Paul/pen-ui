package org.six11.sf;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.six11.util.data.FSM;
import org.six11.util.data.FSM.Transition;
import org.six11.util.gui.BoundingBox;
import org.six11.util.gui.Components;
import org.six11.util.gui.Strokes;
import org.six11.util.gui.shape.ShapeFactory;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Functions;
import org.six11.util.pen.PenEvent;
import org.six11.util.pen.PenListener;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.Vec;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.DefaultFontMapper;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

@SuppressWarnings("serial")
public class DrawingBufferLayers extends JComponent implements PenListener {

  public final static Color DEFAULT_DRY_COLOR = Color.GRAY.darker();
  public final static float DEFAULT_DRY_THICKNESS = 1.4f;
  public final static Color DEFAULT_WET_COLOR = Color.BLACK;
  public final static float DEFAULT_WET_THICKNESS = 1.8f;
  List<PenListener> penListeners;
  private Color bgColor = Color.WHITE;
  private Color penEnabledBorderColor = Color.GREEN;
  private double borderPad = 2.0;
  private List<DrawingBuffer> layers;
  private Map<String, DrawingBuffer> layersByName;
  SketchBook model;
  private FSM fsFSM;
  private Timer fsTimer;
  private Pt fsDown;
  private Segment fsNearestSeg; // segment currently flow-selected
  private Pt fsNearestPt; // point on segment currently selected
  private double fsBubble = 10;
  private int fsPauseTimeout = 900;
  private Timer fsTickTimer;
  private int fsTickTimeout = 45;
  private long fsStartTime;
  private Pt fsLastDeformPt;
  Pt prev;
  GeneralPath currentScribble;
  private Pt hoverPt;
  private GuidePoint draggingGP;

  public DrawingBufferLayers(SketchBook model) {
    this.model = model;
    setName("DrawingBufferLayers");
    layers = new ArrayList<DrawingBuffer>();
    layersByName = new HashMap<String, DrawingBuffer>();
    penListeners = new ArrayList<PenListener>();
    fsInit();
  }

  private final void fsInit() {
    fsTimer = new Timer(fsPauseTimeout, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent ev) {
        fsCheck();
      }
    });
    fsTimer.setRepeats(false);
    fsTickTimer = new Timer(fsTickTimeout, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        fsFSM.addEvent("tick");
      }
    });
    FSM f = new FSM("Flow Selection FSM");
    f.addState("idle");
    f.addState("draw");
    f.addState("flow");
    f.addState("op");
    f.setStateEntryCode("draw", new Runnable() {
      @Override
      public void run() {
        fsInitTimer();
      }
    });
    f.setStateEntryCode("idle", new Runnable() {
      public void run() {
        fsDown = null;
        fsNearestSeg = null; // segment currently flow-selected
        fsNearestPt = null; // point on segment currently selected
        fsTimer.stop();
        model.getEditor().drawStuff();
      }
    });
    f.setStateEntryCode("flow", new Runnable() {
      public void run() {
        fsTickTimer.restart();
      }
    });
    f.setStateExitCode("op", new Runnable() {
      public void run() {
        fsSaveChanges();
      }
    });
    f.addTransition(new Transition("down", "idle", "draw"));
    f.addTransition(new Transition("up", "draw", "idle"));
    f.addTransition(new Transition("pause", "draw", "flow") {
      public void doAfterTransition() {
        fsInitSelection();
      }
    });
    f.addTransition(new Transition("tick", "flow", "flow") {
      public void doAfterTransition() {
        fsGrowSelection();
      }
    }); // causes flow selection to grow
    f.addTransition(new Transition("up", "flow", "idle"));
    f.addTransition(new Transition("move", "flow", "op") {
      public boolean veto() {
        boolean ret = false;
        if (fsCheck()) {
          ret = true;
        }
        return ret;
      }
    });
    f.addTransition(new Transition("up", "op", "idle"));
    f.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent ev) {
        bug("new state: " + fsFSM.getState());
      }
    });

    this.fsFSM = f;
  }

  /**
   * Checks to see if the pen has remained relatively still since the beginning of the current
   * scribble. The 'still' region is determined by the fsBubble variable.
   * 
   * @return true if the pen has always remained relatively near the pen down point.
   */
  private boolean fsCheck() {
    boolean shouldFlow = false;
    if (currentScribble != null && fsDown != null) {
      List<Pt> points = ShapeFactory.makePointList(currentScribble.getPathIterator(null));
      double maxD = 0;
      shouldFlow = true;
      for (Pt pt : points) {
        maxD = Math.max(maxD, pt.distance(fsDown));
        if (maxD > fsBubble) {
          shouldFlow = false;
        }
      }
    }
    if (shouldFlow) {
      fsFSM.addEvent("pause");
    }
    return shouldFlow;
  }

  private void fsInitTimer() {
    fsTimer.stop();
    fsTimer.setInitialDelay(fsPauseTimeout);
    fsTimer.restart();
  }

  private void fsGrowSelection() {
    long t = System.currentTimeMillis() - fsStartTime;
    double f = fsFull(t);
    List<Pt> def = fsNearestSeg.getDeformedPoints();
    for (int i = 0; i < def.size(); i++) {
      Pt pt = def.get(i);
      double effort = pt.getDouble("fsEffort");
      double numerator = effort * Math.PI;
      double s = 0.0;
      if (effort < f) {
        s = (Math.cos(numerator / f) + 1.0) / 2.0;
      }
      pt.setDouble("fsStrength", s);
    }
    model.getEditor().drawStuff();
  }

  private void fsDeform(Pt recent) {
    if (fsLastDeformPt != null && fsNearestSeg != null) {
      Vec dir = new Vec(fsLastDeformPt, recent);
      double m = dir.mag();
      List<Pt> def = fsNearestSeg.getDeformedPoints();
      for (Pt pt : def) {
        double s = pt.getDouble("fsStrength");
        Vec amt = dir.getVectorOfMagnitude(s * m);
        if (Double.isNaN(amt.getX()) || Double.isNaN(amt.getY())) {
          // avoid scary NaNs.
        } else {
          pt.move(amt);
        }
      }
      fsNearestSeg.calculateParameters(def);
    }
    fsLastDeformPt = recent;
    model.getEditor().drawStuff();
  }

  private void fsSaveChanges() {
    if (fsNearestSeg != null) {
      List<Pt> def = fsNearestSeg.getDeformedPoints();
      fsNearestSeg.calculateParameters(def);
      fsNearestSeg.clearDeformation();
      model.getConstraints().wakeUp();
      bug("Just saved changes, I think.");
    }
  }

  private double fsFull(long elapsed) {
    // fully select P pixels per second
    double p = 70;
    return ((double) elapsed * p) / 1000.0;
  }

  public Segment getFlowSelectionSegment() {
    return fsNearestSeg;
  }

  private void fsInitSelection() {
    bug("init selection");
    fsStartTime = System.currentTimeMillis();
    if (fsDown != null) {
      Segment nearestSeg = null;
      Pt nearestPoint = null;
      double nearestDist = Double.MAX_VALUE;
      for (Segment seg : model.getGeometry()) {
        Pt thisPoint = seg.getNearestPoint(fsDown);
        double thisDist = thisPoint.distance(fsDown);
        if (thisDist < nearestDist) {
          nearestDist = thisDist;
          nearestSeg = seg;
          nearestPoint = thisPoint;
        }
      }
      if (nearestSeg != null) {
        bug("Nearest seg/point set.");
        fsLastDeformPt = null;
        fsNearestSeg = nearestSeg;
        fsNearestPt = nearestPoint;
        fsNearestSeg.storeParaPointsForDeformation();
        List<Pt> def = fsNearestSeg.getDeformedPoints();
        double[] distance = Functions.calculateCurvilinearDistance(def, fsNearestPt);
        bug("Found " + distance.length + " distances. they are " + num(distance));
        for (int i = 0; i < distance.length; i++) {
          def.get(i).setDouble("fsEffort", distance[i]);
          def.get(i).setDouble("fsStrength", 0.0); // in case it has stale data from a previous go.
        }
      }
    }
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
    model.getEditor().drawConstraints();
    model.getEditor().drawDerivedGuides();

    for (DrawingBuffer buffer : layers) {
      try {
        if (buffer.isVisible() && useCachedImages) {
          buffer.paste(g);
        } else if (buffer.isVisible()) {
          buffer.drawToGraphics(g);
        }
      } catch (Exception ex) {
        bug("Got exception while drawing, probably due to buffer size.");
      }
    }
    if (currentScribble != null) {
      g.setColor(DEFAULT_WET_COLOR);
      float thick = DEFAULT_WET_THICKNESS;
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
    if (currentScribble != null) {
      List<Pt> scribPts = ShapeFactory.makePointList(currentScribble.getPathIterator(null));
      for (Pt pt : scribPts) {
        ret.add(pt);
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
        fsFSM.setState("idle"); // sanity check in case state got messed up
        fsFSM.addEvent("down");
        fsDown = ev.getPt();
        model.retainVisibleGuides();
        hoverPt = null;
        if (model.isPointOverSelection(ev.getPt())) {
          model.setDraggingSelection(true);
        } else {
          Collection<GuidePoint> nearbyGuidePoints = model.findGuidePoints(ev.getPt(), true);
          if (nearbyGuidePoints.isEmpty()) {
            prev = ev.getPt();
            currentScribble = new GeneralPath();
            currentScribble.moveTo(prev.getX(), prev.getY());
          } else {
            GuidePoint nearestGP = null;
            double nearestDist = Double.MAX_VALUE;
            for (GuidePoint gpt : nearbyGuidePoints) {
              double d = gpt.getLocation().distance(ev.getPt());
              if (d < nearestDist) {
                nearestDist = d;
                nearestGP = gpt;
              }
            }
            model.setDraggingGuidePoint(nearestGP);
          }
          model.startScribble(ev.getPt());
          model.clearSelectedStencils();
        }
        break;
      case Drag:
        fsFSM.addEvent("move");
        hoverPt = null;
        Pt here = ev.getPt();
        if (model.isDraggingSelection()) {
          bug("You should never see this! Shouldn't send layers drag events when drag selection is true");
        } else if (model.isDraggingGuide()) {
          model.dragGuidePoint(here);
        } else if (fsFSM.getState().equals("op")) {
          fsDeform(here);
        } else {
          if (currentScribble != null) {
            currentScribble.lineTo(here.getX(), here.getY());
          }
          model.addScribble(ev.getPt());
        }
        break;
      case Idle:
        boolean wasFlowSelecting = !fsFSM.getState().equals("draw");
        fsFSM.addEvent("up");
        if (model.isDraggingGuide()) {
          model.setDraggingGuidePoint(null);
        } else {
          if (!wasFlowSelecting) {
            Sequence seq = model.endScribble(ev.getPt());
            model.addInk(new Ink(seq));
          }
          clearScribble();
        }
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
