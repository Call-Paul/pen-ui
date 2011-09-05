package org.six11.sf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;

import org.six11.util.gui.ApplicationFrame;
import org.six11.util.layout.FrontEnd;
import org.six11.util.lev.NamedAction;
import org.six11.util.pen.PenEvent;
import org.six11.util.pen.PenListener;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

import static org.six11.util.Debug.bug;
import static org.six11.util.layout.FrontEnd.*;

/**
 * A self-contained editor instance.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class SkruiFabEditor implements PenListener {

  Main main;
  DrawingBufferLayers layers;
  SketchBook model;
  CornerFinder cornerFinder;
  List<GestureFinder> gestureFinders;
  GraphicDebug guibug;

  Map<String, Action> actions;

  public SkruiFabEditor(Main m) {
    this.main = m;
    ApplicationFrame af = new ApplicationFrame("SkruiFab (started " + m.varStr("dateString")
        + " at " + m.varStr("timeString") + ")");
    af.setSize(600, 400);
    createActions(af.getRootPane());

    model = new SketchBook();
    layers = new DrawingBufferLayers(model);
    layers.addPenListener(this);
    guibug = new GraphicDebug(layers);
    cornerFinder = new CornerFinder(guibug);
    gestureFinders = new ArrayList<GestureFinder>();
    gestureFinders.add(new EncircleGestureFinder());
    model.setLayers(layers);

    ScrapGrid grid = new ScrapGrid();
    CutfilePane cutfile = new CutfilePane();

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
    //    //
    //    // 2b. Now give actions for other commands like printing, saving, launching ICBMs, etc
    //    actions.put(ACTION_PRINT, new NamedAction("Print", KeyStroke.getKeyStroke(KeyEvent.VK_P, 0)) {
    //      public void activate() {
    //        print();
    //      }
    //    });
    //    actions.put(ACTION_GO, new NamedAction("Go", KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)) {
    //      public void activate() {
    //        go();
    //      }
    //    });

    // 3. For those actions with keyboard accelerators, register them to the root pane.
    for (Action action : actions.values()) {
      KeyStroke s = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
      if (s != null) {
        rp.registerKeyboardAction(action, s, JComponent.WHEN_IN_FOCUSED_WINDOW);
      }
    }
  }

  public void handlePenEvent(PenEvent ev) {
    // see docs/ink-processing-highlevel.pdf for a diagram of this
    switch (ev.getType()) {
      case Down:
        handleDown(ev);
        break;
      case Drag:
        handleDrag(ev);
        break;
      case Flow:
        handleFlow(ev);
        break;
      case Tap:
        handleTap(ev);
        break;
      case Idle:
        handleIdle(ev);
        break;
      default:
        bug("Unknown pen event type received: " + ev.getType());
    }
  }

  private void handleDown(PenEvent ev) {
    model.startScribble(ev.getPt());
  }

  private void handleDrag(PenEvent ev) {
    model.addScribble(ev.getPt());
  }

  private void handleFlow(PenEvent ev) {
    bug("Flow");
  }

  private void handleTap(PenEvent ev) {
    bug("Tap");
  }

  private void handleIdle(PenEvent ev) {
    Sequence seq = model.endScribble(ev.getPt());
    List<Gesture> gestures = new ArrayList<Gesture>(); // collect all gesture analyses here
    for (GestureFinder gestureFinder : gestureFinders) {
      Gesture gesture = gestureFinder.findGesture(seq);
      if (gesture != null) {
        bug(gesture.getHumanName() + " prob: " + gesture.getProbability());
        gestures.add(gesture);
      } else {
        bug("Warning: gesture finder " + gestureFinder.getClass() + " returned a null gesture.");
      }
    }

    // There might be zero or more gestures detected. In that case, deal with the most likely.
    Gesture best = null;
    for (Gesture g : gestures) {
      if (best != null && best.getProbability() < g.getProbability()) {
        best = g;
      }
      if (best == null) {
        best = g;
      }
    }
    if (best.getProbability() > 0) {
      // handle gesture.
      // right now the only gesture is encircle, so obviously this will change...
      EncircleGesture circ = (EncircleGesture) best;
      List<Pt> points = circ.getPoints();
      guibug.fillShape(points);
    }

    cornerFinder.findCorners(seq);
    layers.clearScribble();
  }

}
