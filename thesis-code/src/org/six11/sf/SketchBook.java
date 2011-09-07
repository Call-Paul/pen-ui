package org.six11.sf;

import java.awt.geom.Area;
import java.util.ArrayList;
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

  private DrawingBufferLayers layers;

  public SketchBook() {
    this.scribbles = new ArrayList<Sequence>();
    ink = new ArrayList<Ink>();
  }

  public void addInk(Ink newInk) {
    ink.add(newInk);
    if (newInk.getType() == Type.Unstructured) {
      DrawingBuffer buf = layers.getLayer(GraphicDebug.DB_UNSTRUCTURED_INK);
      UnstructuredInk unstruc = (UnstructuredInk) newInk;
      Sequence scrib = unstruc.getSequence();
      bug("Finished stroke with " + scrib.size() + " points. It has a bounding box of: " + num(newInk.getBounds()));
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
    if (!scrib.getLast().isSameLocation(pt)) {
      scrib.add(pt);
    } else {
      bug("Avoid duplicate point in scribble");
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

}
