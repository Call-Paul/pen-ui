package org.six11.sf;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Timer;

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
  private Pt gestureProgressStart;
  private Pt gestureProgressPrev;
  private Gesture actualGesture;
  private SketchBook model;
  private Color encircleColor = new Color(255, 255, 0, 128);
  private ActionListener potentialGestureTimeout;
  private Timer potentialGestureTimer;

  /**
   * @param sketchBook
   */
  public GestureController(SketchBook sketchBook) {
    this.model = sketchBook;
    gestureFinders = new ArrayList<GestureFinder>();
    gestureFinders.add(new EncircleGestureFinder(model));

  }

  public void addPotentialGesture(Gesture best) {
    // right now the only gesture is encircle, so obviously this will change...
    EncircleGesture circ = (EncircleGesture) best;
    List<Pt> points = circ.getPoints();
    DrawingBuffer db = model.getLayers().getLayer(GraphicDebug.DB_HIGHLIGHTS);
    db.clear();
    model.getGuiBug().ghostlyOutlineShape(db, points, encircleColor);
    model.setSelected(model.search(circ.getArea()));
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

  public void revertPotentialGesture() {
    if (potentialGesture != null) {
      bug("... currently in a gesture (" + potentialGesture.hashCode()
          + "). Reverting. was actual gesture? " + potentialGesture.wasActualGesture());
      clearGestureTimer();
      model.clearSelection();
      if (!potentialGesture.wasActualGesture()) {
        // turn the gesture's pen input into unstructured ink
        Ink originalInk = new UnstructuredInk(potentialGesture.getOriginalSequence());
        model.addInk(originalInk);
      }
      model.clearSelection(); // deselect things if there were any
      potentialGesture = null; // forget anything about the last input as a gesture.
      // remove the graphics from any highlight currently shown
      DrawingBuffer db = model.getLayers().getLayer(GraphicDebug.DB_HIGHLIGHTS);
      db.clear();
    } else {
      bug("... not in a gesture. Doing nothing.");
    }
  }

  public boolean isGesturing() {
    return (potentialGesture != null);
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

  public void gestureStart(Pt pt) {
    Gesture currentGesture = getPotentialGesture();
    if (currentGesture != null) {
      if (currentGesture instanceof EncircleGesture) {
        EncircleGesture circ = (EncircleGesture) currentGesture;
        model.getSelectionCopy().clear();
        for (Ink sel : model.getSelection()) {
          model.getSelectionCopy().add(sel.copy());
        }
        DrawingBuffer copyLayer = model.getLayers().getLayer(GraphicDebug.DB_COPY_LAYER);
        copyLayer.clear();
        Color color = DrawingBufferLayers.DEFAULT_COLOR.brighter().brighter();
        for (Ink eenk : model.getSelectionCopy()) {
          UnstructuredInk uns = (UnstructuredInk) eenk;
          Sequence scrib = uns.getSequence();
          DrawingBufferRoutines.drawShape(copyLayer, scrib.getPoints(), color,
              DrawingBufferLayers.DEFAULT_THICKNESS);
          DrawingBufferRoutines.dots(copyLayer, scrib.getPoints(), 2, 0.5, Color.BLACK, Color.RED);
        }

      }
    }
  }

  public void gestureProgress(Pt pt) {
    Gesture currentGesture = getPotentialGesture();
    if (currentGesture != null) {
      if (currentGesture instanceof EncircleGesture) {
        EncircleGesture circ = (EncircleGesture) currentGesture;
        if (gestureProgressStart == null) {
          gestureProgressStart = pt;
        } else {
          double dx = pt.getX() - gestureProgressStart.getX();
          double dy = pt.getY() - gestureProgressStart.getY();
          DrawingBuffer copyLayer = model.getLayers().getLayer(GraphicDebug.DB_COPY_LAYER);
          copyLayer.setGraphicsReset();
          copyLayer.setGraphicsTranslate(dx, dy);
        }
        gestureProgressPrev = pt;
      }
    }
    model.getLayers().repaint();
  }

  public void gestureEnd(boolean endInDrawingLayers) {
    Gesture currentGesture = getPotentialGesture();
    if (currentGesture != null) {
      if (currentGesture instanceof EncircleGesture) {
        bug("Gesture end: " + endInDrawingLayers);
        EncircleGesture circ = (EncircleGesture) currentGesture;
        if (endInDrawingLayers) {
          // do whatever is necessary to finalize the gesture.
          if (gestureProgressStart != null && gestureProgressPrev != null) {
            double dx = gestureProgressPrev.getX() - gestureProgressStart.getX();
            double dy = gestureProgressPrev.getY() - gestureProgressStart.getY();
            for (Ink k : model.getSelectionCopy()) {
              k.move(dx, dy);
              model.addInk(k);
            }
          }
        }
        circ.setActualGesture(true);
        DrawingBuffer copyLayer = model.getLayers().getLayer(GraphicDebug.DB_COPY_LAYER);
        copyLayer.clear();
        revertPotentialGesture();
        model.clearSelection();
        model.getLayers().repaint();
      }
    }
    bug("Cleaing gesture " + potentialGesture.hashCode());
    potentialGesture = null;
    gestureProgressPrev = null;
    gestureProgressStart = null;
  }

  public void addGesture(Gesture gest) {
    this.actualGesture = gest;
  }

  public Gesture detectGesture(Sequence seq) {
    List<Gesture> gestures = new ArrayList<Gesture>(); // collect all results
    for (GestureFinder gestureFinder : gestureFinders) {
      Gesture gesture = gestureFinder.findGesture(seq);
      if (gesture != null && gesture.getProbability() > 0) {
        gestures.add(gesture);
        bug("Added " + gesture.getClass().getName() + " with p=" + num(gesture.getProbability()));
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
    return best;

    // model.revertPotentialGesture();
    // if (best != null && best.getProbability() > 0) {
    // model.addPotentialGesture(best);
    // } else {
    // }
    // }
    // actOnGesture = false;

  }

}
