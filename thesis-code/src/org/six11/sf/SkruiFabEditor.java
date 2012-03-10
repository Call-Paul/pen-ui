package org.six11.sf;

import static org.six11.util.Debug.bug;
import static org.six11.util.layout.FrontEnd.E;
import static org.six11.util.layout.FrontEnd.N;
import static org.six11.util.layout.FrontEnd.ROOT;
import static org.six11.util.layout.FrontEnd.S;
import static org.six11.util.layout.FrontEnd.W;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Toolkit;
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
import javax.swing.JFrame;
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
  private static final String ACTION_TOGGLE_VECTORS = "Toggle Vectors";
  private static final Color LATCH_SPOT_COLOR = new Color(112, 164, 225); // 0.4375, 0.640625, 0.87890625
  private static String ACTION_PRINT = "Print";
  private static String ACTION_DEBUG_STATE = "DebugState";
  private static String ACTION_CLEAR = "Clear";

  private Color selectionColor = new Color(255, 128, 128, 200);
  private Color inertColor = new Color(128, 128, 128, 100);
  private Color derivedGuideColor = new Color(220, 220, 220, 0);

  //  private Main main;
  private boolean useDebuggingColor = false;
  private boolean useDebuggingPoints = false;
  private DrawingSurface surface;
  private SketchBook model;
  private Map<String, Action> actions;
  //  private GlassPane glass;
  private FastGlassPane fastGlass;
  private ApplicationFrame af;
  private Colors colors;
  private ScrapGrid grid;
  private CutfilePane cutfile;
  private ActionListener drawLaterRunnable;
  private Timer drawLaterTimer;
  private boolean debugSolver = true;
  protected boolean fixedFrameRate = false;

  public SkruiFabEditor(Main m) {
    this.colors = new Colors();
    colors.set("stencil", new Color(0.8f, 0.8f, 0.8f, 0.5f));
    colors.set("selected stencil", new Color(0.8f, 0.5f, 0.5f, 0.5f));
    af = new ApplicationFrame("Sketch It, Make It (started " + m.varStr("dateString") + " at "
        + m.varStr("timeString") + ")");
    Dimension screenDim = Toolkit.getDefaultToolkit().getScreenSize();
    if (screenDim.width > 1600 && screenDim.height > 1000) {
      af.setSize(1600, 1000);
    } else if (screenDim.width > 1400 && screenDim.height > 800) {
      af.setSize(1400, 800);
    } else {
      af.setSize(800, 600);
    }
    createActions(af.getRootPane());
    //    glass = new GlassPane(this);
    fastGlass = new FastGlassPane(this);
    af.getRootPane().setGlassPane(fastGlass);
    fastGlass.setVisible(true);
    model = new SketchBook(fastGlass, this);
    model.getConstraints().addListener(new ConstraintSolver.Listener() {
      public void constraintStepDone(final ConstraintSolver.State state, int numIterations,
          double err, int numPoints, int numConstraints) {
        if (!fixedFrameRate) {
          if (numIterations > 30 || err < (numPoints * 2)) {
            model.getConstraints().setFrameRate(0);
          } else {
            model.getConstraints().setFrameRate(FRAME_RATE);
          }
          if (state == State.Solved) {
            model.getSnapshotMachine().requestSnapshot("Solver simmered down");
          }
        }
        //        drawStuffLater();
        //        layers.repaint();
        //        surface.display();
        //        requestRedrawGL();
        surface.repaint();
      }
    });
    surface = new DrawingSurface(model);
    model.setSurface(surface);
    grid = new ScrapGrid(this);
    cutfile = new CutfilePane(this);
    FrontEnd fe = new FrontEnd();
    //    fe.add(layers, "layers");
    fe.add(surface, "layers");
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
    
    model.getSnapshotMachine().requestSnapshot("Initial blank state"); // initial blank state
  }

  public JFrame getApplicationFrame() {
    return af;
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

    // 2. Now give actions for other commands like printing, saving, launching ICBMs, etc

    actions.put(ACTION_PRINT, new NamedAction("Print", KeyStroke.getKeyStroke(KeyEvent.VK_P, 0)) {
      public void activate() {
        print();
      }
    });

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

    actions.put(ACTION_TOGGLE_VECTORS,
        new NamedAction("Toggle Vectors", KeyStroke.getKeyStroke(KeyEvent.VK_V, 0)) {
          public void activate() {
            toggleVectors();
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

  protected void toggleVectors() {
    debugSolver = !debugSolver;
    surface.repaint();
  }

  protected void loadSnapshot() {

  }

  protected void debugState() {
    String debugFileName = "skrui-debug-" + System.currentTimeMillis() + ".txt";
    bug("Debugging state of everything. Look in the file " + debugFileName);
    String str = model.getMondoDebugString();
    System.out.println(str);
    File bugFile = new File(debugFileName);
    try {
      boolean made = bugFile.createNewFile();
      if (made) {
        BufferedWriter bugFileOut = new BufferedWriter(new FileWriter(bugFile));
        bugFileOut.write(str);
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
    bug("Warning: print() does not work anymore. Re-implement");
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
      List<Segment> seqSegs = (List<Segment>) seq.getAttribute(CornerFinder.SEGMENTS);
      if (seqSegs != null) {
        segs.addAll(seqSegs);
      }
      stroke.setAnalyzed(true);
    }
    model.addSegments(segs);
    model.getConstraintAnalyzer().analyze(segs, true);
    Collection<RecognizedItem> items = model.getRecognizer().analyzeRecent();
    items = filterRecognizedItems(items);
    for (RecognizedItem item : items) {
      item.getTemplate().create(item, model);
    }
    findStencils(segs);
    model.getConstraints().wakeUp();
    model.clearInk();
    surface.repaint();
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

  public FastGlassPane getGlass() {
    return fastGlass;
  }

  public CutfilePane getCutfilePane() {
    return cutfile;
  }

  public DrawingSurface getDrawingSurface() {
    return surface;
  }

}
