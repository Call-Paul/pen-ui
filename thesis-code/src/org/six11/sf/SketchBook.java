package org.six11.sf;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

import java.awt.Color;
import java.awt.Image;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.six11.sf.rec.Arrow;
import org.six11.sf.rec.EncircleRecognizer;
import org.six11.sf.rec.RecognizedItem;
import org.six11.sf.rec.RecognizedRawItem;
import org.six11.sf.rec.RecognizerPrimitive;
import org.six11.sf.rec.RightAngleBrace;
import org.six11.sf.rec.SameLengthGesture;
import org.six11.util.Debug;
import org.six11.util.data.Lists;
import org.six11.util.gui.Colors;
import org.six11.util.gui.shape.ShapeFactory;
import org.six11.util.pen.ConvexHull;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.solve.Constraint;
import org.six11.util.solve.ConstraintSolver;

import org.imgscalr.Scalr;

public class SketchBook {

  List<Sequence> scribbles; // raw ink, as the user provided it.
  List<Ink> ink;

  private DrawingBufferLayers layers;
  private Set<Stencil> selection;
  //  private List<Ink> selectionCopy;
  private GraphicDebug guibug;
  private Set<Segment> geometry;
  private ConstraintAnalyzer constraintAnalyzer;
  private ConstraintSolver solver;
  private CornerFinder cornerFinder;
  private int pointCounter = 1;
  private SketchRecognizerController recognizer;
  private Map<RecognizedItem, Set<Constraint>> userConstraints;
  private Set<Set<RecognizedItem>> friends;
  private Set<Stencil> stencils;
  private SkruiFabEditor editor;
  private boolean draggingSelection;
  private BufferedImage draggingThumb;
  private GlassPane glass;

  public SketchBook(GlassPane glass, SkruiFabEditor editor) {
    this.glass = glass;
    this.editor = editor;
    this.scribbles = new ArrayList<Sequence>();
    this.selection = new HashSet<Stencil>();
    this.cornerFinder = new CornerFinder();
    //    this.selectionCopy = new ArrayList<Ink>();
    this.geometry = new HashSet<Segment>();
    this.stencils = new HashSet<Stencil>();
    //    this.userConstraints = new HashMap<Constraint, RecognizedItem>();
    this.userConstraints = new HashMap<RecognizedItem, Set<Constraint>>();
    this.friends = new HashSet<Set<RecognizedItem>>();
    this.ink = new ArrayList<Ink>();
    this.constraintAnalyzer = new ConstraintAnalyzer(this);
    this.solver = new ConstraintSolver();
    solver.runInBackground();
    solver.createUI();
    this.recognizer = new SketchRecognizerController(this);
    addRecognizer(new EncircleRecognizer(this));
    addRecognizer(new Arrow(this));
    addRecognizer(new RightAngleBrace(this));
    addRecognizer(new SameLengthGesture(this));
  }

  private void addRecognizer(SketchRecognizer rec) {
    recognizer.add(rec);
  }

  //  public List<Ink> getSelectionCopy() {
  //    return selectionCopy;
  //  }

  public Set<Stencil> getSelection() {
    return selection;
  }

  public Set<Stencil> getStencils() {
    return stencils;
  }

  public DrawingBufferLayers getLayers() {
    return layers;
  }

  public GraphicDebug getGuiBug() {
    return guibug;
  }

  public void setGuibug(GraphicDebug gb) {
    this.guibug = gb;
    //    this.cornerFinder.setGuibug(gb);
  }

  public void addInk(Ink newInk) {
    // this is the part where encircle gestures should be found since they have precedence
    Collection<RecognizedRawItem> rawResults = recognizer.analyzeSingleRaw(newInk);
    boolean didSomething = false;
    for (RecognizedRawItem item : rawResults) {
      if (item.isOk()) {
        bug("This one is ok! Activating it.");
        item.activate(this);
        didSomething = true;
      }
    }
    if (!didSomething) {
      cornerFinder.findCorners(newInk);
      ink.add(newInk);
      DrawingBuffer buf = layers.getLayer(GraphicDebug.DB_UNSTRUCTURED_INK);
      Sequence scrib = newInk.getSequence();
      DrawingBufferRoutines.drawShape(buf, scrib.getPoints(), DrawingBufferLayers.DEFAULT_COLOR,
          DrawingBufferLayers.DEFAULT_THICKNESS);
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

  //  /**
  //   * Returns a list of Ink that is contained (partly or wholly) in the target area.
  //   */
  //  public List<Ink> search(Area target) {
  //    List<Ink> ret = new ArrayList<Ink>();
  //    for (Ink eenk : ink) {
  //      if (eenk.getOverlap(target) > 0.5) {
  //        ret.add(eenk);
  //      }
  //    }
  //    return ret;
  //  }

  public void clearSelection() {
    setSelected(null);
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
    bug("Replacing " + StencilFinder.n(capPt) + " with " + StencilFinder.n(spot)
        + " in all stencils. If you see this later, there's something amiss.");
    for (Stencil s : stencils) {
      s.replacePoint(capPt, spot);
    }
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
    ink.clear();
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
    editor.getGrid().clear();
  }

  private void clearStructured() {
    geometry.clear();
    stencils.clear();
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

  public String getMondoDebugString() {
    StringBuilder buf = new StringBuilder();
    int indent = 0;
    addBug(indent, buf, "All debug info\n\n");
    addBug(indent, buf, geometry.size() + " segments in 'geometry':\n");
    addBug(indent, buf, "seg type\tp1 name\tp1 hash\tp2 name\tp2 hash\n");
    for (Segment seg : geometry) {
      String p1 = (seg.getP1().hasAttribute("name")) ? seg.getP1().getString("name") : "<no name>";
      String p2 = (seg.getP2().hasAttribute("name")) ? seg.getP2().getString("name") : "<no name>";
      addBug(indent, buf, seg.getType() + " " + p1 + "\t" + seg.getP1().hashCode() + "\t" + p2
          + "\t" + seg.getP2().hashCode() + "\n");
    }
    buf.append("\n");
    addBug(indent, buf, stencils.size() + " stencils\n");
    indent++;
    for (Stencil s : stencils) {
      addBug(indent, buf, s.getPath().size() + " points: " + StencilFinder.n(s.getPath()));
      if (selection.contains(s)) {
        buf.append(" (selected)");
      }
      buf.append("\n");
    }
    indent--;
    return buf.toString();
  }

  private void addBug(int indent, StringBuilder buf, String what) {
    buf.append(Debug.spaces(4 * indent) + what);
  }

  public void mergeStencils(Set<Stencil> newStencils) {
    // add non-duplicate stencils first
    for (Stencil nub : newStencils) {
      boolean ok = true;
      for (Stencil old : stencils) {
        if (old.isSame(nub)) {
          ok = false;
          break;
        }
      }
      if (ok) {
        stencils.add(nub);
      }
    }

    // then remove sub-stencils. boot those that are in a superset
    Set<Stencil> doomed = new HashSet<Stencil>();
    for (Stencil s1 : stencils) {
      for (Stencil s2 : stencils) {
        if (s1.isSuperset(s2)) {
          doomed.add(s2);
        }
        if (s2.isSuperset(s1)) {
          doomed.add(s1);
        }
      }
    }
    stencils.removeAll(doomed);
  }

  public Collection<Stencil> findStencil(Area area, double d) {
    Collection<Stencil> ret = new HashSet<Stencil>();
    for (Stencil s : stencils) {
      double ratio = 0;
      Area ix = s.intersect(area);
      if (!ix.isEmpty()) {
        ConvexHull stencilHull = new ConvexHull(s.getPath());
        double stencilArea = stencilHull.getConvexArea();
        ConvexHull ixHull = new ConvexHull(ShapeFactory.makePointList(ix.getPathIterator(null)));
        double ixArea = ixHull.getConvexArea();
        ratio = ixArea / stencilArea;
      }
      if (ratio >= d) {
        ret.add(s);
      }
    }
    return ret;
  }

  //  public void setSelected(Collection<Ink> selectUs) {
  //    selection.clear();
  //    if (selectUs != null) {
  //      selection.addAll(selectUs);
  //    }
  //    DrawingBuffer db = layers.getLayer(GraphicDebug.DB_SELECTION);
  //    db.clear();
  //    for (Ink eenk : selection) {
  //      guibug.ghostlyOutlineShape(db, eenk.getSequence().getPoints(), Color.CYAN.darker());
  //    }
  //  }

  public void setSelected(Collection<Stencil> selectUs) {
    selection.clear();
    if (selectUs != null) {
      selection.addAll(selectUs);
    }
    editor.drawStencils();
  }

  public boolean isPointOverSelection(Pt where) {
    boolean ret = false;
    for (Stencil s : selection) {
      Area shapeArea = new Area(s.getShape());
      if (shapeArea.contains(where)) {
        ret = true;
        break;
      }
    }
    return ret;
  }

  public void setDraggingSelection(boolean b) {
    draggingSelection = b;
    if (draggingSelection) {
      DrawingBuffer sel = layers.getLayer(GraphicDebug.DB_SELECTION);
      BufferedImage bigImage = sel.getImage();
      draggingThumb = Scalr.resize(bigImage, 48);
      glass.setActivity(GlassPane.ActivityMode.DragSelection);
    } else {
      draggingThumb = null;
    }
  }

  public boolean isDraggingSelection() {
    return draggingSelection;
  }
  
  public BufferedImage getDraggingThumb() {
    return draggingThumb;
  }
}
