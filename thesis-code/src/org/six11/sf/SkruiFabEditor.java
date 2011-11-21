package org.six11.sf;

import static org.six11.util.Debug.bug;
import static org.six11.util.layout.FrontEnd.E;
import static org.six11.util.layout.FrontEnd.N;
import static org.six11.util.layout.FrontEnd.ROOT;
import static org.six11.util.layout.FrontEnd.S;
import static org.six11.util.layout.FrontEnd.W;

import java.awt.Color;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.six11.sf.rec.Arrow;
import org.six11.sf.rec.RecognizedItem;
import org.six11.sf.rec.RecognizerPrimitive;
import org.six11.sf.rec.RightAngleBrace;
import org.six11.util.gui.ApplicationFrame;
import org.six11.util.layout.FrontEnd;
import org.six11.util.lev.NamedAction;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.solve.Constraint;
import org.six11.util.solve.ConstraintSolver;
import org.six11.util.solve.NumericValue;
import org.six11.util.solve.OrientationConstraint;

import com.sun.java.swing.plaf.gtk.GTKConstants.ArrowType;

/**
 * A self-contained editor instance.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class SkruiFabEditor {

  Main main;
  DrawingBufferLayers layers;
  SketchBook model;

  GraphicDebug guibug;
  Map<String, Action> actions;
  private GlassPane glass;
  private static String ACTION_GO = "Go";
  private static String ACTION_CLEAR = "Clear";
  ApplicationFrame af;

  public SkruiFabEditor(Main m) {
    this.main = m;
    af = new ApplicationFrame("SkruiFab (started " + m.varStr("dateString") + " at "
        + m.varStr("timeString") + ")");
    af.setSize(600, 400);
    createActions(af.getRootPane());
    glass = new GlassPane(this);
    af.getRootPane().setGlassPane(glass);
    glass.setVisible(true);
    model = new SketchBook(glass);
    model.getConstraints().addListener(new ConstraintSolver.Listener() {
      public void constraintStepDone() {
        Runnable r = new Runnable() {
          public void run() {
            if (layers != null) {
              drawStructured();
//              drawConstraints();
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

    ScrapGrid grid = new ScrapGrid(this);
    CutfilePane cutfile = new CutfilePane(this);
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

  public static void copyImage(Image sourceImage, BufferedImage destImage, double scaleFactor) {
    Graphics2D g = destImage.createGraphics();
    AffineTransform xform = AffineTransform.getScaleInstance(scaleFactor, scaleFactor);
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.drawImage(sourceImage, xform, null);
    g.dispose();
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
    for (int num = 0; num < 10; num++) {
      final String numStr = "" + num;
      KeyStroke numKey = KeyStroke.getKeyStroke(numStr.charAt(0));
      actions.put("DEBUG " + num, new NamedAction("Toggle Debug Layer " + num, numKey) {
        public void activate() {
          guibug.whackLayerVisibility(numStr);
        }
      });
    }
    //
    // 2b. Now give actions for other commands like printing, saving,
    // launching ICBMs, etc

    // actions.put(ACTION_PRINT, new NamedAction("Print",
    // KeyStroke.getKeyStroke(KeyEvent.VK_P, 0)) {
    // public void activate() {
    // print();
    // }
    // });
    actions.put(ACTION_GO, new NamedAction("Go", KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)) {
      public void activate() {
        go();
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

  @SuppressWarnings("unchecked")
  public void go() {
    bug("+----------------------------------------------------------------------------------------");
    bug("|                                          ");
    bug("|                                          go");
    bug("|                                          ");
    bug("+----------------------------------------------------------------------------------------");
    List<Ink> unstruc = model.getUnanalyzedInk();
    Collection<Segment> segs = new HashSet<Segment>();
    for (Ink stroke : unstruc) {
      Sequence seq = stroke.getSequence();
      segs.addAll((List<Segment>) seq.getAttribute(CornerFinder.SEGMENTS));
      stroke.setAnalyzed(true);
    }
    for (Segment seg : segs) {
      model.getConstraints().addPoint(model.nextPointName(), seg.getP1());
      model.getConstraints().addPoint(model.nextPointName(), seg.getP2());
      model.addGeometry(seg);
    }
    model.getConstraintAnalyzer().analyze(segs);
    Collection<RecognizedItem> items = model.getRecognizer().analyzeRecent();
    items = filterRecognizedItems(items);
    for (RecognizedItem item : items) {
      item.getTemplate().create(item, model);
    }
    model.getConstraints().wakeUp();

    model.clearInk();
    layers.getLayer(GraphicDebug.DB_UNSTRUCTURED_INK).clear();
    drawStructured();
    drawRecognized(items);
    layers.repaint();
  }

  /**
   * Given a bunch of recognized items, filter out the ones that don't make sense. For example,
   * there might be two recognized items: a right-angle bracket, and an arrow. If the ink for the
   * right angle bracket is the same as the arrow, it is clear that both are not right. Only include
   * the ones that the user was more likely to have meant.
   */
  private Collection<RecognizedItem> filterRecognizedItems(Collection<RecognizedItem> items) {
    Collection<RecognizedItem> ret = new HashSet<RecognizedItem>();
    ret.addAll(items); // TODO: you're doing it wrong.
    return ret;
  }

  private void drawRecognized(Collection<RecognizedItem> items) {
    DrawingBuffer buf = layers.getLayer(GraphicDebug.DB_SEGMENT_LAYER);
    for (RecognizedItem item : items) {
      if (item.getTemplate() instanceof Arrow) {
        DrawingBufferRoutines.arrow(buf, item.getFeaturePoint(Arrow.START),
            item.getFeaturePoint(Arrow.TIP), 2.0f, Color.BLUE);
      } else if (item.getTemplate() instanceof RightAngleBrace) {
        //        List<Pt> corners = new ArrayList<Pt>();
        //        corners.add(item.getFeaturePoint(RightAngleBrace.CORNER_A));
        //        corners.add(item.getFeaturePoint(RightAngleBrace.CORNER_B));
        //        corners.add(item.getFeaturePoint(RightAngleBrace.CORNER_D));
        //        corners.add(item.getFeaturePoint(RightAngleBrace.CORNER_C));
        //        corners.add(item.getFeaturePoint(RightAngleBrace.CORNER_A));
        //        DrawingBufferRoutines.lines(buf, corners, Color.CYAN, 2.0);
        //        DrawingBufferRoutines.dot(buf, corners.get(0), 4.0, 1.0, Color.BLACK, Color.GREEN);
        //        DrawingBufferRoutines.dot(buf, corners.get(2), 4.0, 1.0, Color.BLACK, Color.RED);
      }
    }
  }

  private void drawStructured() {
    DrawingBuffer buf = layers.getLayer(GraphicDebug.DB_STRUCTURED_INK);
    buf.clear();
    for (Segment seg : model.getGeometry()) {
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
        case Unknown:
          break;
      }
    }
    layers.repaint();

  }



}
