package org.six11.sf;

import java.awt.Color;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.six11.sf.Ink.Type;
import org.six11.util.data.Lists;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;
import org.six11.util.Debug;

public class SketchBook {

  List<Sequence> scribbles; // raw ink, as the user provided it.
  List<Ink> ink;

  /*
   * A potential gesture is ink the user just made, and looks suspiciously like some gesture. The
   * only way to determine if it is a gesture is to wait for the user's next action. If the next
   * action is consistent with the gesture (e.g. moving a selection) then we use the gesture. If the
   * next action is NOT consistent with the gesture it means that ink was simply unstructured ink.
   */
  Gesture potentialGesture;

  private DrawingBufferLayers layers;
  private List<Ink> selected;
  private GraphicDebug guibug;
  private Color encircleColor = new Color(255, 255, 0, 128);

  public SketchBook() {
    this.scribbles = new ArrayList<Sequence>();
    this.selected = new ArrayList<Ink>();
    ink = new ArrayList<Ink>();
  }

  public void setGuibug(GraphicDebug gb) {
    this.guibug = gb;
  }

  public void addInk(Ink newInk) {
    ink.add(newInk);
    if (newInk.getType() == Type.Unstructured) {
      DrawingBuffer buf = layers.getLayer(GraphicDebug.DB_UNSTRUCTURED_INK);
      UnstructuredInk unstruc = (UnstructuredInk) newInk;
      Sequence scrib = unstruc.getSequence();
      bug("Finished stroke with " + scrib.size() + " points. It has a bounding box of: "
          + num(newInk.getBounds()));
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
    bug("Finished scribble " + scribbles.size() + " with " + scrib.size() + " points");
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
    bug("Found " + selected.size() + " ink items in that area.");
    potentialGesture = circ;
  }

  public void clearSelection() {
    bug("Clearing selection.");
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
    bug("There are now " + selected.size() + " objects.");
  }

  public void revertPotentialGesture() {
    bug("Reverting potentialGesture...");
    if (potentialGesture != null) {
      bug("***********");
      // turn the gesture's pen input into unstructured ink
      Ink originalInk = new UnstructuredInk(potentialGesture.getOriginalSequence());
      bug("Adding potential gesture's ink to ink list...");
      addInk(originalInk);
      clearSelection(); // deselet things if there were any
      // remove the graphics from any highlight currently shown
      DrawingBuffer db = layers.getLayer(GraphicDebug.DB_HIGHLIGHTS);
      db.clear();
    } else {
      bug("**** There was no gesture.");
    }
  }

}
