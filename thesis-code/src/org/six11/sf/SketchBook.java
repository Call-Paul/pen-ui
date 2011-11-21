package org.six11.sf;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

import java.awt.Color;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.six11.sf.rec.Arrow;
import org.six11.sf.rec.RecognizedItem;
import org.six11.sf.rec.RecognizerPrimitive;
import org.six11.sf.rec.RightAngleBrace;
import org.six11.sf.rec.SameLengthGesture;
import org.six11.util.data.Lists;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.solve.Constraint;
import org.six11.util.solve.ConstraintSolver;


public class SketchBook {

  List<Sequence> scribbles; // raw ink, as the user provided it.
  List<Ink> ink;

  private DrawingBufferLayers layers;
  private List<Ink> selection;
  private List<Ink> selectionCopy;
  private GraphicDebug guibug;
  private Set<Segment> geometry;
  private ConstraintAnalyzer constraintAnalyzer;
  private ConstraintSolver solver;
  private CornerFinder cornerFinder;
  private int pointCounter = 1;
  private SketchRecognizerController recognizer;
  //  private Map<Constraint, RecognizedItem> userConstraints;
  private Map<RecognizedItem, Set<Constraint>> userConstraints;
  private Set<Set<RecognizedItem>> friends;

  public SketchBook(GlassPane glass) {
    this.scribbles = new ArrayList<Sequence>();
    this.selection = new ArrayList<Ink>();
    this.cornerFinder = new CornerFinder();
    this.selectionCopy = new ArrayList<Ink>();
    this.geometry = new HashSet<Segment>();
    //    this.userConstraints = new HashMap<Constraint, RecognizedItem>();
    this.userConstraints = new HashMap<RecognizedItem, Set<Constraint>>();
    this.friends = new HashSet<Set<RecognizedItem>>();
    this.ink = new ArrayList<Ink>();
    this.constraintAnalyzer = new ConstraintAnalyzer(this);
    this.solver = new ConstraintSolver();
    solver.runInBackground();
    solver.createUI();
    this.recognizer = new SketchRecognizerController(this);
    addRecognizer(new Arrow(this));
    addRecognizer(new RightAngleBrace(this));
    addRecognizer(new SameLengthGesture(this));
  }

  private void addRecognizer(SketchRecognizer rec) {
    recognizer.add(rec);
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

  public void setGuibug(GraphicDebug gb) {
    this.guibug = gb;
    this.cornerFinder.setGuibug(gb);
  }

  public void addInk(Ink newInk) {
    cornerFinder.findCorners(newInk);
    ink.add(newInk);
    DrawingBuffer buf = layers.getLayer(GraphicDebug.DB_UNSTRUCTURED_INK);
    Sequence scrib = newInk.getSequence();
    DrawingBufferRoutines.drawShape(buf, scrib.getPoints(), DrawingBufferLayers.DEFAULT_COLOR,
        DrawingBufferLayers.DEFAULT_THICKNESS);
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
    //    cornerFinder.findCorners(scrib);
    return scrib;
  }

  public void setLayers(DrawingBufferLayers layers) {
    this.layers = layers;
  }

  public CornerFinder getCornerFinder() {
    return cornerFinder;
  }

  public List<Ink> getUnanalyzedInk() {
    List<Ink> ret = new ArrayList<Ink>();
    for (Ink stroke : ink) {
      if (!stroke.isAnalyzed()) {
        ret.add(stroke);
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
      guibug.ghostlyOutlineShape(db, eenk.getSequence().getPoints(), Color.CYAN.darker());
    }
  }

  public void addGeometry(Segment seg) {
    geometry.add(seg);
  }

  public void removeGeometry(Segment seg) {
    geometry.remove(seg);
  }

  public Set<Segment> getGeometry() {
    return geometry;
  }

  public ConstraintAnalyzer getConstraintAnalyzer() {
    return constraintAnalyzer;
  }

  public ConstraintSolver getConstraints() {
    return solver;
  }

  public void replace(Pt capPt, Pt spot) {
    // segment geometry
    for (Segment seg : geometry) {
      seg.replace(capPt, spot);
    }
    // points and constraints
    solver.replacePoint(capPt, nextPointName(), spot);
  }

  /**
   * Gives a incrementally-formed name like "P240" to assign points used in the constraint model.
   * 
   * @return
   */
  protected String nextPointName() {
    return "P" + pointCounter++;
  }

  public SketchRecognizerController getRecognizer() {
    return recognizer;
  }

  public void clearInk() {
    int before = ink.size();
    ink.clear();
    bug("removed " + (before - ink.size()) + " ink strokes");
  }

  public void clearAll() {
    clearInk();
    clearSelection();
    clearStructured();
    getConstraints().clearConstraints();
    friends = new HashSet<Set<RecognizedItem>>();
    layers.clearScribble();
    layers.clearAllBuffers();
    layers.repaint();
  }

  private void clearStructured() {
    geometry.clear();
  }

  public void removeRelated(Ink eenk) {
    Set<Segment> doomed = new HashSet<Segment>();
    for (Segment seg : geometry) {
      if (seg.ink == eenk) {
        doomed.add(seg);
        getConstraints().removePoint(seg.getP1());
        getConstraints().removePoint(seg.getP2());
      }
    }
    geometry.removeAll(doomed);
    getConstraints().wakeUp();
  }

  public void registerConstraint(RecognizedItem item, Constraint someConstraint) {
    getConstraints().addConstraint(someConstraint);
    //    userConstraints.put(someConstraint, item);
    if (userConstraints.get(item) == null) {
      userConstraints.put(item, new HashSet<Constraint>());
    }
    userConstraints.get(item).add(someConstraint);
    for (RecognizerPrimitive prim : item.getSubshapes()) {
      removeRelated(prim.getInk());
    }
  }

  public RecognizedItem getConstraintItem(Constraint c) {
    RecognizedItem ret = null;
    for (RecognizedItem item : userConstraints.keySet()) {
      if (userConstraints.get(item).contains(c)) {
        ret = item;
        break;
      }
    }
    return ret;
  }

  public void setFriends(RecognizedItem... items) {
    Set<RecognizedItem> f = null;
    for (RecognizedItem item : items) {
      f = findFriends(item);
      if (f != null) {
        break;
      }
    }
    if (f == null) {
      f = new HashSet<RecognizedItem>();
      friends.add(f);
    }
    for (RecognizedItem item : items) {
      f.add(item);
    }
    bug("The following are now friends: " + num(f, " "));
    
  }

  public Set<RecognizedItem> findFriends(RecognizedItem item) {
    Set<RecognizedItem> f = null;
    for (Set<RecognizedItem> click : friends) {
      if (click.contains(item)) {
        f = click;
        break;
      }
    }
    return f;
  }

}
