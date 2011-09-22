package org.six11.sf;

import java.awt.Color;
import java.awt.dnd.DragSource;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;

import org.six11.sf.Ink.Type;
import org.six11.util.data.Lists;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;
import org.six11.util.Debug;

public class SketchBook {

  public static final double GESTURE_AOE_DISTANCE = 20.0;

  List<Sequence> scribbles; // raw ink, as the user provided it.
  List<Ink> ink;

  /*
   * A potential gesture is ink the user just made, and looks suspiciously like some gesture. The
   * only way to determine if it is a gesture is to wait for the user's next action. If the next
   * action is consistent with the gesture (e.g. moving a selection) then we use the gesture. If the
   * next action is NOT consistent with the gesture it means that ink was simply unstructured ink.
   */
  Gesture potentialGesture;
  Pt gestureProgressStart;
  Pt gestureProgressPrev;

  private DrawingBufferLayers layers;
  private List<Ink> selected;
  private List<Ink> selectedCopy;
  private GraphicDebug guibug;
  private Color encircleColor = new Color(255, 255, 0, 128);
  private ActionListener potentialGestureTimeout;
  private Timer potentialGestureTimer;

  public SketchBook() {
    this.scribbles = new ArrayList<Sequence>();
    this.selected = new ArrayList<Ink>();
    this.selectedCopy = new ArrayList<Ink>();
    ink = new ArrayList<Ink>();
  }

  public void setGuibug(GraphicDebug gb) {
    this.guibug = gb;
  }

  public void addInk(Ink newInk) {
    ink.add(newInk);
    clearGestureTimer();
    if (newInk.getType() == Type.Unstructured) {
      DrawingBuffer buf = layers.getLayer(GraphicDebug.DB_UNSTRUCTURED_INK);
      UnstructuredInk unstruc = (UnstructuredInk) newInk;
      Sequence scrib = unstruc.getSequence();
      DrawingBufferRoutines.drawShape(buf, scrib.getPoints(), DrawingBufferLayers.DEFAULT_COLOR,
          DrawingBufferLayers.DEFAULT_THICKNESS);
      layers.repaint();
    }
  }

  public void removeInk(Ink oldInk) {
    ink.remove(oldInk);
    // TODO: remove from drawing buffer and redraw
    bug("Not implemented");
  }

  public Sequence startScribble(Pt pt) {
    Sequence scrib = new Sequence();
    scrib.add(pt);
    scribbles.add(scrib);
    return scrib;
  }

  public Sequence addScribble(Pt pt) {
    Sequence scrib = (Sequence) Lists.getLast(scribbles);
    if (!scrib.getLast().isSameLocation(pt)) { // Avoid duplicate point in scribble
      scrib.add(pt);
    }
    return scrib;
  }

  public Sequence endScribble(Pt pt) {
    Sequence scrib = (Sequence) Lists.getLast(scribbles);
    return scrib;
  }

  public void setLayers(DrawingBufferLayers layers) {
    this.layers = layers;
  }

  public List<UnstructuredInk> getUnanalyzedInk() {
    List<UnstructuredInk> ret = new ArrayList<UnstructuredInk>();
    for (Ink stroke : ink) {
      if (stroke.getType() == Type.Unstructured && !stroke.isAnalyzed()) {
        ret.add((UnstructuredInk) stroke);
      }
    }
    return ret;
  }

  /**
   * Returns a list of Ink that is contained (partly or wholly) in the target area.
   */
  public List<Ink> search(Area target) {
    List<Ink> ret = new ArrayList<Ink>();
    for (Ink eenk : ink) {
      if (eenk.getOverlap(target) > 0.5) {
        ret.add(eenk);
      }
    }
    return ret;
  }

  public void addPotentialGesture(Gesture best) {
    // right now the only gesture is encircle, so obviously this will change...
    EncircleGesture circ = (EncircleGesture) best;
    List<Pt> points = circ.getPoints();
    DrawingBuffer db = layers.getLayer(GraphicDebug.DB_HIGHLIGHTS);
    db.clear();
    guibug.ghostlyOutlineShape(db, points, encircleColor);
    setSelected(search(circ.getArea()));
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

  public void clearSelection() {
    setSelected(null);
  }

  public void setSelected(Collection<Ink> selectUs) {
    selected.clear();
    if (selectUs != null) {
      selected.addAll(selectUs);
    }
    DrawingBuffer db = layers.getLayer(GraphicDebug.DB_SELECTION);
    db.clear();
    for (Ink eenk : selected) {
      UnstructuredInk uns = (UnstructuredInk) eenk;
      guibug.ghostlyOutlineShape(db, uns.getSequence().getPoints(), Color.CYAN.darker());
    }
  }

  public void revertPotentialGesture() {
    if (potentialGesture != null) {
      bug("... currently in a gesture (" + potentialGesture.hashCode()
          + "). Reverting. was actual gesture? " + potentialGesture.wasActualGesture());
      clearGestureTimer();
      clearSelection();
      if (!potentialGesture.wasActualGesture()) {
        // turn the gesture's pen input into unstructured ink
        Ink originalInk = new UnstructuredInk(potentialGesture.getOriginalSequence());
        addInk(originalInk);
      }
      clearSelection(); // deselet things if there were any
      // remove the graphics from any highlight currently shown
      DrawingBuffer db = layers.getLayer(GraphicDebug.DB_HIGHLIGHTS);
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
        selectedCopy.clear();
        for (Ink sel : selected) {
          selectedCopy.add(sel.copy());
        }
        DrawingBuffer copyLayer = layers.getLayer(GraphicDebug.DB_COPY_LAYER);
        copyLayer.clear();
        Color color = DrawingBufferLayers.DEFAULT_COLOR.brighter().brighter();
        for (Ink eenk : selectedCopy) {
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
          DrawingBuffer copyLayer = layers.getLayer(GraphicDebug.DB_COPY_LAYER);
          copyLayer.setGraphicsReset();
          copyLayer.setGraphicsTranslate(dx, dy);
        }
        gestureProgressPrev = pt;
      }
    }
    layers.repaint();
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
            for (Ink k : selectedCopy) {
              k.move(dx, dy);
              addInk(k);
            }
          }
        }
        circ.setActualGesture(true);
        DrawingBuffer copyLayer = layers.getLayer(GraphicDebug.DB_COPY_LAYER);
        copyLayer.clear();
        revertPotentialGesture();
        clearSelection();
        layers.repaint();
      }
    }
    bug("Cleaing gesture " + potentialGesture.hashCode());
    potentialGesture = null;
    gestureProgressPrev = null;
    gestureProgressStart = null;
  }

}
