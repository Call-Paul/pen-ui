package org.six11.sf;

import static org.six11.util.Debug.bug;
import static org.six11.util.layout.FrontEnd.E;
import static org.six11.util.layout.FrontEnd.N;
import static org.six11.util.layout.FrontEnd.ROOT;
import static org.six11.util.layout.FrontEnd.S;
import static org.six11.util.layout.FrontEnd.W;

import java.awt.Color;
import java.awt.Container;
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

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.six11.sf.rec.Arrow;
import org.six11.sf.rec.RecognizedItem;
import org.six11.sf.rec.RecognizedItemTemplate;
import org.six11.sf.rec.RecognizerPrimitive;
import org.six11.sf.rec.RightAngleBrace;
import org.six11.util.Debug;
import org.six11.util.data.Lists;
import org.six11.util.gui.ApplicationFrame;
import org.six11.util.gui.Colors;
import org.six11.util.layout.FrontEnd;
import org.six11.util.lev.NamedAction;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
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

  private static String ACTION_GO = "Go";
  private static String ACTION_PRINT = "Print";
  private static String ACTION_DEBUG_STATE = "DebugState";
  private static String ACTION_CLEAR = "Clear";

  private Color selectionColor = new Color(255, 128, 128, 200);
  private Color inertColor = new Color(128, 128, 128, 100);
  private Color derivedGuideColor = new Color(220, 220, 220, 0);

  //  private Main main;
  private DrawingBufferLayers layers;
  private SketchBook model;
  private GraphicDebug guibug;
  private Map<String, Action> actions;
  private GlassPane glass;
  private ApplicationFrame af;
  private Colors colors;
  private ScrapGrid grid;
  private CutfilePane cutfile;

  public SkruiFabEditor(Main m) {
    //    this.main = m;
    this.colors = new Colors();
    colors.set("stencil", new Color(0.8f, 0.8f, 0.8f, 0.5f));
    colors.set("selected stencil", new Color(0.8f, 0.5f, 0.5f, 0.5f));

    af = new ApplicationFrame("SkruiFab (started " + m.varStr("dateString") + " at "
        + m.varStr("timeString") + ")");
    af.setSize(802, 399);
    createActions(af.getRootPane());
    glass = new GlassPane(this);
    af.getRootPane().setGlassPane(glass);
    glass.setVisible(true);
    model = new SketchBook(glass, this);
    model.getConstraints().addListener(new ConstraintSolver.Listener() {
      public void constraintStepDone(final ConstraintSolver.State state) {
        Runnable r = new Runnable() {
          public void run() {
            if (state == State.Solved) {
              model.fixDerivedGuides();
            }
            if (layers != null) {
              drawStuff();
            }
          }
        };
        SwingUtilities.invokeLater(r);
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

    // 3. For those actions with keyboard accelerators, register them to the
    // root pane.
    for (Action action : actions.values()) {
      KeyStroke s = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
      if (s != null) {
        rp.registerKeyboardAction(action, s, JComponent.WHEN_IN_FOCUSED_WINDOW);
      }
    }

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
      }
    } catch (IOException ex) {
      ex.printStackTrace();
    }
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
      outFile = new File(System.getProperty("user.dir"), "skruifab-" + now + (which == 0 ? "" : "-" + which) + ".pdf");
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
    for (Segment seg : segs) {
      model.getConstraints().addPoint(model.nextPointName(), seg.getP1());
      model.getConstraints().addPoint(model.nextPointName(), seg.getP2());
    }
    SafeAction a = model.getActionFactory().addSegments(segs);
    model.addAction(a);
    model.getConstraintAnalyzer().analyze(segs);
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
  }

  public void findStencils(Collection<Segment> segs) {
    StencilFinder sf = new StencilFinder();
    model.mergeStencils(sf.findStencils(segs, model.getGeometry()));
  }

  /**
   * Given a bunch of recognized items, filter out the ones that don't make sense. For example,
   * there might be two recognized items: a right-angle bracket, and an arrow. If the ink for the
   * right angle bracket is the same as the arrow, it is clear that both are not right. Only include
   * the ones that the user was more likely to have meant.
   */
  private Collection<RecognizedItem> filterRecognizedItems(Collection<RecognizedItem> items) {
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

  public void drawStuff() {
    drawStencils();
    drawStructured();
    drawGuides();
    drawFS();
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
        DrawingBufferRoutines.fillShape(buf, s.getShape(), colors.get("stencil"), 0);
      }
    }
    if (later.size() > 0) {
      for (Stencil s : later) {
        DrawingBufferRoutines.fillShape(selBuf, s.getShape(),
            colors.get("selected stencil"), 0);
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
      if (!model.getConstraints().getPoints().contains(seg.getP1())) {
        bug("Segment P1 is unknown to constraint system.");
      }
      if (!model.getConstraints().getPoints().contains(seg.getP2())) {
        bug("Segment P2 is unknown to constraint system.");
      }
      switch (seg.getType()) {
        case Curve:
          DrawingBufferRoutines.drawShape(buf, seg.asSpline(), Color.CYAN, 1.8);
          break;
        case EllipticalArc:
          DrawingBufferRoutines.drawShape(buf, seg.asSpline(), Color.MAGENTA, 1.8);
          break;
        case Line:
          DrawingBufferRoutines.line(buf, seg.asLine(), Color.GREEN, 1.8);
          break;
        case CircularArc:
          DrawingBufferRoutines.drawShape(buf, seg.asArc(), Color.BLUE, 1.8);
          break;
        case Unknown:
          break;
      }
    }
    layers.repaint();

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
      if (def != null) {
        for (int i=0; i < def.size()-1; i++) {
          Pt a = def.get(i);
          Pt b = def.get(i+1);
          double str = Math.max(a.getDouble("fsStrength"), b.getDouble("fsStrength"));
          Color color = new Color(1f, 0f, 0f, (float) str);
          DrawingBufferRoutines.line(fsBuf, a, b, color, 5.0);
        }
      }
    }
    layers.repaint();
  }

}
