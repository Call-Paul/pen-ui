package org.six11.sf;

import static org.six11.util.Debug.bug;
import static org.six11.util.layout.FrontEnd.E;
import static org.six11.util.layout.FrontEnd.N;
import static org.six11.util.layout.FrontEnd.ROOT;
import static org.six11.util.layout.FrontEnd.S;
import static org.six11.util.layout.FrontEnd.W;

import java.awt.Color;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.six11.sf.constr.UserConstraint;
import org.six11.sf.rec.Arrow;
import org.six11.sf.rec.RecognizedItem;
import org.six11.sf.rec.RecognizedItemTemplate;
import org.six11.sf.rec.RecognizerPrimitive;
import org.six11.sf.rec.RightAngleBrace;
import org.six11.util.Debug;
import org.six11.util.Stopwatch;
import org.six11.util.data.Lists;
import org.six11.util.gui.ApplicationFrame;
import org.six11.util.gui.Colors;
import org.six11.util.io.FileUtil;
import org.six11.util.layout.FrontEnd;
import org.six11.util.lev.NamedAction;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.Vec;
import org.six11.util.solve.ConstraintSolver;
import org.six11.util.solve.ConstraintSolver.State;
import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

/**
 * A self-contained editor instance.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class SkruiFabEditor {

  private static final Color ELLIPSE_COLOR = Color.MAGENTA.darker();
  private static final Color CIRCLE_COLOR = Color.BLUE.darker();
  private static final Color BLOB_COLOR = Color.CYAN.darker();
  private static final String ACTION_DEBUG_COLOR = "DebugColor";
  private static final String ACTION_LOAD_FILE = "Load File";
  protected static final int FRAME_RATE = 30;
  private static String ACTION_PRINT = "Print";
  private static String ACTION_DEBUG_STATE = "DebugState";
  private static String ACTION_CLEAR = "Clear";

  private Color selectionColor = new Color(255, 128, 128, 200);
  private Color inertColor = new Color(128, 128, 128, 100);
  private Color derivedGuideColor = new Color(220, 220, 220, 0);

  //  private Main main;
  private boolean useDebuggingColor = false;
  private boolean useDebuggingPoints = false;
  private DrawingBufferLayers layers;
  private SketchBook model;
  private GraphicDebug guibug;
  private Map<String, Action> actions;
  private GlassPane glass;
  private ApplicationFrame af;
  private Colors colors;
  private ScrapGrid grid;
  private CutfilePane cutfile;
  //  private Stopwatch drawingStopwatch;
  //  private Stopwatch goStopwatch;
  //  private long lastDrawLater;
  private ActionListener drawLaterRunnable;
  private Timer drawLaterTimer;

  public SkruiFabEditor(Main m) {
    //    this.main = m;
    this.colors = new Colors();
    colors.set("stencil", new Color(0.8f, 0.8f, 0.8f, 0.5f));
    colors.set("selected stencil", new Color(0.8f, 0.5f, 0.5f, 0.5f));
    //    drawingStopwatch = new Stopwatch();
    //    drawingStopwatch.setLogFile("drawingStopwatch.txt");
    //    drawingStopwatch.logHeaders(new String[] {
    //        "drawStencils", "drawStructured", "drawGuides", "drawFS", "drawStuff"
    //    });
    //    goStopwatch = new Stopwatch();
    //    goStopwatch.setLogFile("goStopwatch.txt");
    //    goStopwatch.logHeaders(new String[] {
    //        "guide", "makeSegs", "addSegs", "recognize", "stencils", "draw buffers", "go"
    //    });
    af = new ApplicationFrame("SkruiFab (started " + m.varStr("dateString") + " at "
        + m.varStr("timeString") + ")");
    af.setSize(802, 399);
    createActions(af.getRootPane());
    glass = new GlassPane(this);
    af.getRootPane().setGlassPane(glass);
    glass.setVisible(true);
    model = new SketchBook(glass, this);
    model.getConstraints().addListener(new ConstraintSolver.Listener() {
      public void constraintStepDone(final ConstraintSolver.State state, int numIterations,
          double err, int numPoints, int numConstraints) {
        if (numIterations > 30 || err < (numPoints * 2)) {
          model.getConstraints().setFrameRate(0);
        } else {
          model.getConstraints().setFrameRate(FRAME_RATE);
        }
        //        if (state == State.Solved) {
        //          bug("Came to a stop! Snapping.");
        //          model.getSnapshotMachine().requestSnapshot("Solver simmered down");
        //        }
        drawStuffLater();
      }
    });
    layers = new DrawingBufferLayers(model);
    guibug = new GraphicDebug(layers);
    model.setGuibug(guibug);
    model.setLayers(layers);
    grid = new ScrapGrid(this);
    cutfile = new CutfilePane(this);
    FrontEnd fe = new FrontEnd();
    fe.add(layers, "layers");
    fe.add(grid, "grid");
    fe.add(cutfile, "cutfile");
    fe.addRule(ROOT, N, "layers", N);
    fe.addRule(ROOT, W, "layers", W);
    fe.addRule(ROOT, S, "layers", S);
    fe.addRule(ROOT, N, "grid", N);
    fe.addRule(ROOT, E, "grid", E);
    fe.addRule(ROOT, E, "cutfile", E);
    fe.addRule(ROOT, S, "cutfile", S);
    fe.addRule("cutfile", N, "grid", S);
    fe.addRule("cutfile", W, "layers", E);
    fe.addRule("cutfile", W, "grid", W);
    af.add(fe);
    af.center();
    af.setVisible(true);
    drawLaterRunnable = new ActionListener() {
      public void actionPerformed(ActionEvent ev) {
        if (model.getConstraints().getSolutionState() == State.Solved) {
          model.fixDerivedGuides();
        }
        if (layers != null) {
          drawStuff();
          layers.repaint();
        }
      }
    };
    drawLaterTimer = new Timer(20, drawLaterRunnable);
    drawLaterTimer.setRepeats(false);
    model.getSnapshotMachine().requestSnapshot("Initial blank state"); // initial blank state
  }

  protected void drawStuffLater() {
    if (!drawLaterTimer.isRunning()) {
      drawLaterTimer.start();
    }
  }

  public ScrapGrid getGrid() {
    return grid;
  }

  Container getContentPane() {
    return af.getContentPane();
  }

  public SketchBook getModel() {
    return model;
  }

  private void createActions(JRootPane rp) {
    // 1. Make action map.
    actions = new HashMap<String, Action>();

    // 2. Fill action map with named actions.
    //
    // 2a. Start with keys for toggling layers 0--9
    //    for (int num = 0; num < 10; num++) {
    //      final String numStr = "" + num;
    //      KeyStroke numKey = KeyStroke.getKeyStroke(numStr.charAt(0));
    //      actions.put("DEBUG " + num, new NamedAction("Toggle Debug Layer " + num, numKey) {
    //        public void activate() {
    //          guibug.whackLayerVisibility(numStr);
    //        }
    //      });
    //    }

    //
    // 2b. Now give actions for other commands like printing, saving,
    // launching ICBMs, etc

    actions.put(ACTION_PRINT, new NamedAction("Print", KeyStroke.getKeyStroke(KeyEvent.VK_P, 0)) {
      public void activate() {
        print();
      }
    });
    //    actions.put(ACTION_GO, new NamedAction("Go", KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)) {
    //      public void activate() {
    //        go();
    //      }
    //    });

    actions.put(ACTION_DEBUG_STATE,
        new NamedAction("DebugState", KeyStroke.getKeyStroke(KeyEvent.VK_D, 0)) {
          public void activate() {
            debugState();
          }
        });

    actions.put(ACTION_CLEAR,
        new NamedAction("Clear", KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)) {
          public void activate() {
            model.clearAll();
          }
        });

    actions.put(ACTION_DEBUG_COLOR,
        new NamedAction("DebugColor", KeyStroke.getKeyStroke(KeyEvent.VK_C, 0)) {
          public void activate() {
            debugColorToggle();
          }
        });

    actions.put(ACTION_LOAD_FILE,
        new NamedAction("Load Snapshot", KeyStroke.getKeyStroke(KeyEvent.VK_L, 0)) {
          public void activate() {
            loadSnapshot();
          }
        });

    // 3. For those actions with keyboard accelerators, register them to the
    // root pane.
    for (Action action : actions.values()) {
      KeyStroke s = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
      if (s != null) {
        rp.registerKeyboardAction(action, s, JComponent.WHEN_IN_FOCUSED_WINDOW);
      }
    }
  }

  protected void loadSnapshot() {

  }

  protected void debugState() {
    String debugFileName = "skrui-debug-" + System.currentTimeMillis() + ".txt";
    bug("Debugging state of everything. Look in the file " + debugFileName);
    File bugFile = new File(debugFileName);
    try {
      boolean made = bugFile.createNewFile();
      if (made) {
        BufferedWriter bugFileOut = new BufferedWriter(new FileWriter(bugFile));
        bugFileOut.write(model.getMondoDebugString());
        bugFileOut.flush();
        bugFileOut.close();
        File latest = new File("skrui-debug-latest.txt");
        FileUtil.copy(bugFile, latest);
      }
    } catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  protected void debugColorToggle() {
    this.useDebuggingColor = !useDebuggingColor;
    this.useDebuggingPoints = !useDebuggingPoints;
    drawStuff();
  }

  public File getPdfOutputFile() throws IOException {
    File outfile = new File("cutfile.pdf");
    if (!outfile.exists()) {
      boolean happened = outfile.createNewFile();
      if (!happened) {
        bug("Was unable to create the output file.");
      }
    }
    return outfile;
  }

  private void print() {
    String now = Debug.nowFilenameFriendly();
    File outFile;
    int which = 0;
    do {
      outFile = new File(System.getProperty("user.dir"), "skruifab-" + now
          + (which == 0 ? "" : "-" + which) + ".pdf");
      which++;
    } while (outFile.exists());
    layers.print(outFile);
  }

  @SuppressWarnings("unchecked")
  public void go() {

    bug("+---------------------------------------------------------------------------------------+");
    bug("|                                                                                       |");
    bug("|                                       ~ go ~                                          |");
    bug("|                                                                                       |");
    bug("+---------------------------------------------------------------------------------------+");
    List<Ink> unstruc = model.getUnanalyzedInk();
    Collection<Segment> segs = new HashSet<Segment>();
    //    goStopwatch.start("guide");
    if (unstruc.isEmpty()) {
      bug("No ink to work with...");
    } else {
      Set<Guide> passed = new HashSet<Guide>();
      Set<Ink> passedInk = new HashSet<Ink>();
      for (Ink stroke : unstruc) {
        passed.clear();
        Segment guidedSeg = null;
        for (Guide g : stroke.guides) {
          if (g.claims(stroke.seq, 0, stroke.seq.size() - 1)) {
            if (g instanceof GuidePoint) {
              g.adjust(stroke, 0, stroke.seq.size() - 1);
            } else {
              bug("** Guide " + g + " claims this entire stroke.");
              passed.add(g);
              passedInk.add(stroke);
            }
          }
        }
        if (passed.size() == 1) {
          guidedSeg = passed.toArray(new Guide[1])[0].adjust(stroke, 0, stroke.seq.size() - 1);
          bug("adding guided segment: " + guidedSeg);
          segs.add(guidedSeg);
        }
      }
      unstruc.removeAll(passedInk);
    }
    for (Ink stroke : unstruc) {
      Sequence seq = stroke.getSequence();
      segs.addAll((List<Segment>) seq.getAttribute(CornerFinder.SEGMENTS));
      stroke.setAnalyzed(true);
    }
    SafeAction a = model.getActionFactory().addSegments(segs);
    model.addAction(a);
    model.getConstraintAnalyzer().analyze(segs, true);
    Collection<RecognizedItem> items = model.getRecognizer().analyzeRecent();
    items = filterRecognizedItems(items);
    for (RecognizedItem item : items) {
      item.getTemplate().create(item, model);
    }
    findStencils(segs);
    model.getConstraints().wakeUp();
    model.clearInk();
    layers.getLayer(GraphicDebug.DB_UNSTRUCTURED_INK).clear();
    drawStencils();
    drawStructured();
    drawRecognized(items);
    layers.repaint();
    model.getConstraints().wakeUp();
    model.getSnapshotMachine().requestSnapshot("End of 'go'");
  }

  public void findStencils(Collection<Segment> segs) {
    StencilFinder sf = new StencilFinder(model);
    Set<Stencil> newStencils = sf.findStencils(segs);
    model.mergeStencils(newStencils);
  }

  /**
   * Given a bunch of recognized items, filter out the ones that don't make sense. For example,
   * there might be two recognized items: a right-angle bracket, and an arrow. If the ink for the
   * right angle bracket is the same as the arrow, it is clear that both are not right. Only include
   * the ones that the user was more likely to have meant.
   */
  private Collection<RecognizedItem> filterRecognizedItems(Collection<RecognizedItem> items) {
    if (items.size() > 0) {
      bug("Filtering recognized items from the following set: ");
      for (RecognizedItem item : items) {
        bug("  " + item.toString());
      }
    }
    Collection<RecognizedItem> ret = new HashSet<RecognizedItem>();
    RecognizedItem[] cull = items.toArray(new RecognizedItem[items.size()]);
    Set<RecognizedItem> doomed = new HashSet<RecognizedItem>();
    for (int i = 0; i < cull.length; i++) {
      if (!doomed.contains(cull[i])) {
        RecognizedItem candidate = cull[i];
        Collection<RecognizerPrimitive> bunchA = candidate.getSubshapes();
        for (int j = i + 1; j < cull.length; j++) {
          if (!doomed.contains(cull[j])) {
            RecognizedItem other = cull[j];
            Collection<RecognizerPrimitive> bunchB = other.getSubshapes();
            if (Lists.hasOverlap(bunchA, bunchB)) {
              Collection<RecognizedItem> removeUs = RecognizedItemTemplate.resolveConflict(
                  candidate, other);
              doomed.addAll(removeUs);
            }
          }
        }
      }
    }
    items.removeAll(doomed);
    ret.addAll(items);
    return ret;
  }

  /**
   * A debugging function for drawing segment endcaps.
   */
  void drawEndCaps() {
    DrawingBuffer buf = layers.getLayer("end caps");
    buf.clear();
    for (Segment seg : model.getGeometry()) {
      if (seg.hasEndCaps()) {
        for (EndCap ec : seg.getEndCaps()) {
          Pt ecPt = ec.getPt();
          Vec ecDir = ec.getDir();
          double halfLen = ec.getHalfLength();
          Pt a = ecPt.getTranslated(ecDir, halfLen);
          Pt b = ecPt.getTranslated(ecDir.getFlip(), halfLen);
          DrawingBufferRoutines.line(buf, a, b, GraphicDebug.COLOR_CLEAR_BLUE, 4.0);
        }
      }
    }
  }

  public void drawStuff() {
    //    drawEndCaps();
    if (!model.isLoadingSnapshot()) {
      drawStencils();
      drawStructured();
      drawGuides();
      drawFS();
      drawErase();
    }
  }

  private void drawRecognized(Collection<RecognizedItem> items) {
    DrawingBuffer buf = layers.getLayer(GraphicDebug.DB_SEGMENT_LAYER);
    for (RecognizedItem item : items) {
      if (item.getTemplate() instanceof Arrow) {
        DrawingBufferRoutines.arrow(buf, item.getFeaturePoint(Arrow.START),
            item.getFeaturePoint(Arrow.TIP), 2.0f, Color.BLUE);
      } else if (item.getTemplate() instanceof RightAngleBrace) {

      }
    }
  }

  public void drawStencils() {
    DrawingBuffer buf = layers.getLayer(GraphicDebug.DB_STENCIL_LAYER);
    DrawingBuffer selBuf = layers.getLayer(GraphicDebug.DB_SELECTION);
    buf.clear();
    selBuf.clear();
    Set<Stencil> stencils = model.getStencils();
    Set<Stencil> later = new HashSet<Stencil>();
    for (Stencil s : stencils) {
      if (model.getSelectedStencils().contains(s)) {
        later.add(s);
      } else {
        DrawingBufferRoutines.fillShape(buf, s.getShape(true), colors.get("stencil"), 0);
      }
    }
    if (later.size() > 0) {
      for (Stencil s : later) {
        DrawingBufferRoutines
            .fillShape(selBuf, s.getShape(true), colors.get("selected stencil"), 0);
      }
    }
    layers.repaint();
  }

  public void drawConstraints() {
    DrawingBuffer buf = layers.getLayer(GraphicDebug.DB_CONSTRAINT_LAYER);
    buf.clear();
    for (UserConstraint c : model.getUserConstraints()) {
      c.draw(buf, layers.getHoverPoint());
    }
  }

  private void drawGuides() {
    DrawingBuffer buf = layers.getLayer(GraphicDebug.DB_GUIDES);
    buf.clear();
    Color c;
    double r;
    for (GuidePoint gpt : model.getGuidePoints()) {
      if (model.getActiveGuidePoints().contains(gpt)) {
        r = 4.0;
        c = selectionColor;
      } else {
        r = 3.0;
        c = inertColor;
      }
      DrawingBufferRoutines.dot(buf, gpt.getLocation(), r, r * 0.1, Color.BLACK, c);
    }
    if (model.isDraggingGuide()) {
      GuidePoint gpt = model.getDraggingGuide();
      DrawingBufferRoutines.dot(buf, gpt.getLocation(), 5, 5 * 0.1, Color.BLACK, Color.ORANGE);
    }
    layers.repaint();
  }

  public void drawDerivedGuides() {
    if (layers.getHoverPoint() != null) {
      DrawingBuffer buf = layers.getLayer(GraphicDebug.DB_GUIDES_DERIVED);
      buf.clear();
      for (Guide g : model.getDerivedGuides()) {
        g.draw(buf, layers.getHoverPoint(), derivedGuideColor, layers.getBounds());
      }
    }
  }

  private void drawStructured() {
    DrawingBuffer buf = layers.getLayer(GraphicDebug.DB_STRUCTURED_INK);
    buf.clear();

    Segment fsSeg = layers.getFlowSelectionSegment();
    // ------------------------------------------------------------ DRAW SELECTED SEGMENTS
    //
    //
    for (Segment seg : model.getSelectedSegments()) {
      if (seg == fsSeg) {
        continue;
      }
      switch (seg.getType()) {
        case Curve:
          DrawingBufferRoutines.drawShape(buf, seg.asSpline(), selectionColor, 3.8);
          break;
        case EllipticalArc:
          DrawingBufferRoutines.drawShape(buf, seg.asSpline(), selectionColor, 3.8);
          break;
        case Line:
          DrawingBufferRoutines.line(buf, seg.asLine(), selectionColor, 3.8);
          break;
        case CircularArc:
          DrawingBufferRoutines.drawShape(buf, seg.asArc(), selectionColor, 3.8);
          break;
        case Unknown:
          break;
      }
    }

    // ------------------------------------------------------------ DRAW ALL SEGMENTS
    //
    //
    for (Segment seg : model.getGeometry()) {
      if (seg == fsSeg) {
        continue;
      }
      if (!seg.isSingular()) {
        if (!model.getConstraints().getPoints().contains(seg.getP1())) {
          bug("seg.P1 is unknown to constraint system. seg = " + seg);
        }
        if (!model.getConstraints().getPoints().contains(seg.getP2())) {
          bug("seg.P2 is unknown to constraint system. seg = " + seg);
        }
      }
      switch (seg.getType()) {
        case Curve:
          DrawingBufferRoutines.drawShape(buf, seg.asSpline(), getColor(seg.getType()), 1.8);
          break;
        case EllipticalArc:
          DrawingBufferRoutines.drawShape(buf, seg.asSpline(), getColor(seg.getType()), 1.8);
          break;
        case Line:
          DrawingBufferRoutines.line(buf, seg.asLine(), getColor(seg.getType()), 1.8);
          break;
        case CircularArc:
          DrawingBufferRoutines.drawShape(buf, seg.asArc(), getColor(seg.getType()), 1.8);
          break;
        case Ellipse:
          DrawingBufferRoutines.drawShape(buf, seg.asEllipse(), getColor(seg.getType()), 1.8);
          break;
        case Blob:
          DrawingBufferRoutines.drawShape(buf, seg.asSpline(), getColor(seg.getType()), 1.8);
          break;
        case Circle:
          DrawingBufferRoutines.drawShape(buf, seg.asCircle(), getColor(seg.getType()), 1.8);
          break;
        case Unknown:
        default:
          bug("Don't know how to draw segment: " + seg);
          break;
      }
      // draw latchedness
      if (!seg.isSingular()) {
        if (!(model.findRelatedSegments(seg.getP1()).size() > 1)) {
          drawUnlatched(buf, seg.getP1(), seg.getStartDir(), Color.red, 10, 6);
        }
        if (!(model.findRelatedSegments(seg.getP2()).size() > 1)) {
          drawUnlatched(buf, seg.getP2(), seg.getEndDir(), Color.red, 10, 6);
        }
      }

      // draw points for this segment
      if (useDebuggingPoints) {
        Pt mid = seg.getVisualMidpoint();
        DrawingBufferRoutines.text(buf, mid.getTranslated(-10, 10), seg.typeIdStr(), Color.BLACK);
      }
    }

    // debugging: label points
    if (useDebuggingPoints) {
      Color joined = Color.LIGHT_GRAY;
      Color separate = Color.RED;
      Color c;
      for (Pt pt : model.getConstraints().getPoints()) {
        DrawingBufferRoutines.text(buf, pt.getTranslated(10, -10), SketchBook.n(pt), Color.BLACK);
        if (model.findRelatedSegments(pt).size() > 1) {
          c = joined;
        } else {
          c = separate;
        }
        DrawingBufferRoutines.dot(buf, pt, 4, 0.4, Color.BLACK, c);
      }
    }

    layers.repaint();
  }

  private void drawUnlatched(DrawingBuffer buf, Pt pt, Vec dir, Color color, double length,
      double thick) {
    // show something different to indicate it is not attached to anything.
    Pt away = pt.getTranslated(dir, length);
    DrawingBufferRoutines.line(buf, new Line(pt, away), color, thick);
  }

  public Color getColor(Segment.Type t) {
    Color ret = Color.BLACK;
    if (useDebuggingColor) {
      switch (t) {
        case Blob:
          ret = BLOB_COLOR;
          break;
        case Circle:
          ret = CIRCLE_COLOR;
          break;
        case CircularArc:
          ret = Color.BLUE;
          break;
        case Curve:
          ret = Color.CYAN;
          break;
        case Dot:
          break;
        case Ellipse:
          ret = ELLIPSE_COLOR;
          break;
        case EllipticalArc:
          ret = Color.MAGENTA;
          break;
        case Line:
          ret = Color.GREEN;
          break;
        case Unknown:
          break;
      }
    }
    return ret;
  }

  public GlassPane getGlass() {
    return glass;
  }

  public CutfilePane getCutfilePane() {
    return cutfile;
  }

  private void drawFS() {
    DrawingBuffer fsBuf = layers.getLayer(GraphicDebug.DB_FS);
    fsBuf.clear();
    Segment fsSeg = layers.getFlowSelectionSegment();
    if (fsSeg != null) {
      DrawingBufferRoutines.drawShape(fsBuf, fsSeg.asSpline(), Color.BLACK, 1.8);
      List<Pt> def = fsSeg.getDeformedPoints();
      boolean drawNodes = true;
      String state = layers.getFlowSelectionState();
      if (state.equals(DrawingBufferLayers.OP) || state.equals(DrawingBufferLayers.SMOOTH)) {
        drawNodes = true;
      }
      if (def != null) {
        for (int i = 0; i < def.size() - 1; i++) {
          Pt a = def.get(i);
          Pt b = def.get(i + 1);
          double aStr = a.getDouble("fsStrength");
          double bStr = b.getDouble("fsStrength");
          double str = Math.max(aStr, bStr);
          Color color = new Color(1f, 0f, 0f, (float) str);
          DrawingBufferRoutines.line(fsBuf, a, b, color, 5.0);
        }
        if (drawNodes) {
          for (int i = 0; i < def.size(); i++) {
            Pt pt = def.get(i);
            double str = pt.getDouble("fsStrength");
            if (str > 0) {
              DrawingBufferRoutines.dot(fsBuf, pt, 2.5, 0.25, Color.BLACK, Color.WHITE);
            }
          }
        }
      }
    }
    layers.repaint();
  }

  public void drawErase() {
    DrawingBuffer eraseBuf = layers.getLayer(GraphicDebug.DB_ERASE);
    eraseBuf.clear();
    if (model.isErasing()) {
      Pt killSpot = model.getEraseSpot();
      if (killSpot != null) {
        DrawingBufferRoutines.cross(eraseBuf, killSpot, 30, Color.BLUE);
      }
    }
    layers.repaint();
  }

}
