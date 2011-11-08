package org.six11.sf;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Timer;

import org.six11.util.Debug;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

/**
 * This class is responsible for maintaining gesture-related state. It will use GestureFinder
 * instances to detect gestures. One a gesture (potential or actual) is found, it can give
 * information about those. It will manipulate the SketchBook in response to gesture actions such as
 * selecting and copying.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class GestureController {

  public static final double GESTURE_AOE_DISTANCE = 20.0;

  /*
   * A potential gesture is ink the user just made, and looks suspiciously like some gesture. The
   * only way to determine if it is a gesture is to wait for the user's next action. If the next
   * action is consistent with the gesture (e.g. moving a selection) then we use the gesture. If the
   * next action is NOT consistent with the gesture it means that ink was simply unstructured ink.
   */
  private List<GestureFinder> gestureFinders;
  private Gesture potentialGesture;
  private Gesture actualGesture;
  private SketchBook model;
  private Color encircleColor = new Color(255, 255, 0, 128);
  private ActionListener potentialGestureTimeout;
  private Timer potentialGestureTimer;
  private GlassPane glass;

  /**
   * @param sketchBook
   */
  public GestureController(SketchBook sketchBook, GlassPane glass) {
    this.model = sketchBook;
    this.glass = glass;
    gestureFinders = new ArrayList<GestureFinder>();
    gestureFinders.add(new EncircleGestureFinder(model));

  }

  public void clearPotentialGesture() {
    potentialGesture = null;
  }

  public void addPotentialGesture(Gesture best) {
    // right now the only gesture is encircle, so obviously this will change...
    EncircleGesture circ = (EncircleGesture) best;
    List<Pt> points = circ.getPoints();
    DrawingBuffer db = model.getLayers().getLayer(GraphicDebug.DB_HIGHLIGHTS);
    db.clear();
    model.getGuiBug().ghostlyOutlineShape(db, points, encircleColor);
    model.setSelected(model.search(circ.getArea()));
    circ.setSelection(model.getSelection());
    potentialGesture = circ;
    bug("Set potential gesture to " + circ.hashCode());
    // after some timeout, revert the buffer if it hasn't been acted on.
    potentialGestureTimeout = new ActionListener() {
      public void actionPerformed(ActionEvent ev) {
        bug("Attempting to automatically revert the gesture.");
        revertPotentialGesture();
      }
    };
    clearGestureTimer();
    potentialGestureTimer = new Timer(5000, potentialGestureTimeout);
    potentialGestureTimer.setRepeats(false);
    potentialGestureTimer.start();
  }

  public void clearGestureTimer() {
    if (potentialGestureTimer != null) {
      potentialGestureTimer.stop();
    }
  }

  public void restartGestureTimer() {
    if (potentialGestureTimer != null) {
      potentialGestureTimer.restart();
    }
  }

  public void revertPotentialGesture() {
    if (potentialGesture != null) {
      clearGestureTimer();
      model.clearSelection();
      if (!potentialGesture.wasActualGesture()) {
        // turn the gesture's pen input into unstructured ink
        Ink originalInk = new Ink(potentialGesture.getOriginalSequence());
        model.addInk(originalInk);
      }
      clearPotentialGesture();
      // remove the graphics from any highlight currently shown
      DrawingBuffer db = model.getLayers().getLayer(GraphicDebug.DB_HIGHLIGHTS);
      db.clear();
      model.getLayers().repaint();
    }
  }

  public boolean hasPotentialGesture() {
    return (potentialGesture != null);
  }

  public boolean hasActualGesture() {
    return (actualGesture != null);
  }

  public void clearGesture() {
    actualGesture = null;
  }

  public boolean isPointNearPotentialGesture(Pt pt) {
    boolean ret = false;
    if (potentialGesture != null) {
      Sequence seq = potentialGesture.getOriginalSequence();
      Pt close = Functions.getNearestPointOnPolyline(pt, seq.getPoints());
      double d = close.distance(pt);
      ret = (d < GESTURE_AOE_DISTANCE / 2);
    }
    return ret;
  }

  public Gesture getPotentialGesture() {
    return potentialGesture;
  }

  public void setGesture(Gesture gest) {
    this.actualGesture = gest;
    GestureEvent gestStart = GestureEvent.buildStartEvent(this, gest.getComponentStart(),
        gest.getStartLocation(), gest);
    bug("Firing gesture start event.");
    glass.fire(gestStart);
  }

  public Gesture detectGesture(Component start, Sequence seq) {
    List<Gesture> gestures = new ArrayList<Gesture>(); // collect all results
    for (GestureFinder gestureFinder : gestureFinders) {
      Gesture gesture = gestureFinder.findGesture(start, seq);
      if (gesture != null && gesture.getProbability() > 0) {
        gestures.add(gesture);
        bug("Added " + gesture.getClass().getName() + " with p=" + num(gesture.getProbability()));
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
    return best;
  }

  public Gesture getGesture() {
    return actualGesture;
  }

  public void reportState(String msg) {
    bug(msg + "\tpotentialGesture: " + hasPotentialGesture() + "\tactualGesture: "
        + hasActualGesture() + "\ttimer running: "
        + (potentialGestureTimer == null ? "null" : "" + potentialGestureTimer.isRunning()));

  }

}
