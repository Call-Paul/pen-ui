package org.six11.sf;

import java.awt.Color;
import java.awt.Shape;
import java.awt.dnd.DragSource;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;

import org.six11.sf.Ink.Type;
import org.six11.util.data.Lists;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Functions;
import org.six11.util.pen.IntersectionData;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;
import org.six11.util.Debug;

public class SketchBook {

  List<Sequence> scribbles; // raw ink, as the user provided it.
  List<Ink> ink;

  private GestureController gestures;
  private DrawingBufferLayers layers;
  private List<Ink> selection;
  private List<Ink> selectionCopy;
  private GraphicDebug guibug;
  private Color transluscentPink = new Color(220, 180, 180, 140);

  public SketchBook(GlassPane glass) {
    this.scribbles = new ArrayList<Sequence>();
    this.selection = new ArrayList<Ink>();
    this.selectionCopy = new ArrayList<Ink>();
    this.gestures = new GestureController(this, glass);
    ink = new ArrayList<Ink>();
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
    } else if (newInk.getType() == Type.Structured) {
      DrawingBuffer buf = layers.getLayer(GraphicDebug.DB_STRUCTURED_INK);
      StructuredInk struc = (StructuredInk) newInk;
      bug("Adding structured ink of type: " + struc.getSegment().getType());
      switch (struc.getSegment().getType()) {
        case Curve:
          DrawingBufferRoutines.drawShape(buf, struc.getSegment().asSpline(), Color.CYAN, 1.8);
          break;
        case EllipticalArc:
          DrawingBufferRoutines.drawShape(buf, struc.getSegment().asSpline(), Color.MAGENTA, 1.8);
          break;
        case Line:
          DrawingBufferRoutines.line(buf, struc.getSegment().asLine(), Color.GREEN, 1.8);
          break;
        case Unknown:
          break;
      }
    }
    layers.repaint();
  }

  public void processStructuredInk(Collection<StructuredInk> latchMeBaby) {
    // make a current map of the endcap locations for all structured ink

    DrawingBuffer bugBuf = layers.getLayer(GraphicDebug.DB_LATCH_LAYER);
    Set<EndCap> caps = getCurrentEndCaps(ink);
    Set<EndCap> newCaps = getCurrentEndCaps(latchMeBaby);
    // draw stuff. this section can be removed later
//    for (EndCap cap : caps) {
//      if (latchMeBaby.contains(cap.getInk())) {
//        DrawingBufferRoutines.fillShape(bugBuf, cap.getShape(), transluscentPink, 1);
//        DrawingBufferRoutines.drawShape(bugBuf, cap.getShape(), Color.BLACK, 1);
//      } else {
//        bug("Already drew " + cap.getInk());
//      }
//    }

    // Compare the caps of the new ink with each cap in the model
    for (EndCap c1 : newCaps) {
      for (EndCap c2 : caps) {
        // to be efficient do the low-overhead checks first: 
        if (!c1.same(c2) && // avoid self-comparison
            c1.getBounds().intersects(c2.getBounds())) { // check bounding rectangles
          Area mutableArea = new Area(c1.getArea());
          mutableArea.intersect(c2.getArea());
          if (!mutableArea.isEmpty()) {
            IntersectionData id = c1.intersect(c2);
            if (!id.isParallel()) {
              Pt x = id.getIntersection();
              if (mutableArea.contains(x)) {
                bug("Caps intersect. Marking it with a fat blue dot.");
                DrawingBufferRoutines.dot(bugBuf, x, 8, 1, Color.BLACK, Color.BLUE);
              }
            }
          }
        }
      }
    }

    layers.repaint();
  }

  private Set<EndCap> getCurrentEndCaps(Collection<? extends Ink> someInk) {
    Set<EndCap> ret = new HashSet<EndCap>();
    for (Ink k : someInk) {
      if (k instanceof StructuredInk) {
        ret.addAll(((StructuredInk) k).getEndCaps());
      }
    }
    return ret;
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
