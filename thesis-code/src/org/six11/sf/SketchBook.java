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

public class SketchBook {

  List<Sequence> scribbles; // raw ink, as the user provided it.
  List<Ink> ink;

  private GestureController gestures;
  private DrawingBufferLayers layers;
  private List<Ink> selection;
  private List<Ink> selectionCopy;
  private GraphicDebug guibug;

  public SketchBook(GlassPane glass) {
    this.scribbles = new ArrayList<Sequence>();
    this.selection = new ArrayList<Ink>();
    this.selectionCopy = new ArrayList<Ink>();
    this.gestures = new GestureController(this, glass);
    this.ink = new ArrayList<Ink>();
  }

  public List<Ink> getSelectionCopy() {
    return selectionCopy;
  }

  public List<Ink> getSelection() {
    return selection;
  }

  public DrawingBufferLayers getLayers() {
    return layers;
  }

  public GraphicDebug getGuiBug() {
    return guibug;
  }

  public GestureController getGestures() {
    return gestures;
  }

  public void setGuibug(GraphicDebug gb) {
    this.guibug = gb;
  }

  public void addInk(Ink newInk) {
    ink.add(newInk);
    gestures.clearGestureTimer();
    if (newInk.getType() == Type.Unstructured) {
      DrawingBuffer buf = layers.getLayer(GraphicDebug.DB_UNSTRUCTURED_INK);
      UnstructuredInk unstruc = (UnstructuredInk) newInk;
      Sequence scrib = unstruc.getSequence();
      DrawingBufferRoutines.drawShape(buf, scrib.getPoints(), DrawingBufferLayers.DEFAULT_COLOR,
          DrawingBufferLayers.DEFAULT_THICKNESS);
      //    } else if (newInk.getType() == Type.Structured) {
      //      DrawingBuffer buf = layers.getLayer(GraphicDebug.DB_STRUCTURED_INK);
      //      StructuredInk struc = (StructuredInk) newInk;
      //      bug("Adding structured ink of type: " + struc.getSegment().getType());
      //      switch (struc.getSegment().getType()) {
      //        case Curve:
      //          DrawingBufferRoutines.drawShape(buf, struc.getSegment().asSpline(), Color.CYAN, 1.8);
      //          break;
      //        case EllipticalArc:
      //          DrawingBufferRoutines.drawShape(buf, struc.getSegment().asSpline(), Color.MAGENTA, 1.8);
      //          break;
      //        case Line:
      //          DrawingBufferRoutines.line(buf, struc.getSegment().asLine(), Color.GREEN, 1.8);
      //          break;
      //        case Unknown:
      //          break;
      //      }
    }
    layers.repaint();
  }

  public void removeInk(Ink oldInk) {
    ink.remove(oldInk);
    // TODO: remove from drawing buffer and redraw
    bug("Not implemented");
  }

  /**
   * The 'scribble' is ink that is currently being drawn, or is the most recently completed stroke.
   */
  public Sequence startScribble(Pt pt) {
    Sequence scrib = new Sequence();
    scrib.add(pt);
    scribbles.add(scrib);
    return scrib;
  }

  public Sequence addScribble(Pt pt) {
    Sequence scrib = (Sequence) Lists.getLast(scribbles);
    if (!scrib.getLast().isSameLocation(pt)) { // Avoid duplicate point in
      // scribble
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

  public void clearSelection() {
    setSelected(null);
  }

  public void setSelected(Collection<Ink> selectUs) {
    selection.clear();
    if (selectUs != null) {
      selection.addAll(selectUs);
    }
    DrawingBuffer db = layers.getLayer(GraphicDebug.DB_SELECTION);
    db.clear();
    for (Ink eenk : selection) {
      UnstructuredInk uns = (UnstructuredInk) eenk;
      guibug.ghostlyOutlineShape(db, uns.getSequence().getPoints(), Color.CYAN.darker());
    }
  }

}
