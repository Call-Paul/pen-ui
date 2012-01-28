package org.six11.sf;

// J|mmyJ0hn$
import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

import java.awt.Color;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import org.imgscalr.Scalr;
import org.six11.sf.Material.Units;
import org.six11.sf.constr.SameLengthUserConstraint;
import org.six11.sf.constr.UserConstraint;
import org.six11.sf.rec.ConstraintFilters;
import org.six11.sf.rec.DotReferenceGestureRecognizer;
import org.six11.sf.rec.DotSelectGestureRecognizer;
import org.six11.sf.rec.EncircleRecognizer;
import org.six11.sf.rec.EraseGestureRecognizer;
import org.six11.sf.rec.RecognizedRawItem;
import org.six11.sf.rec.RightAngleBrace;
import org.six11.sf.rec.SameLengthGesture;
import org.six11.sf.rec.SelectGestureRecognizer;
import org.six11.util.Debug;
import org.six11.util.data.Lists;
import org.six11.util.gui.shape.ShapeFactory;
import org.six11.util.pen.ConvexHull;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.Vec;
import org.six11.util.solve.Constraint;
import org.six11.util.solve.ConstraintSolver;
import org.six11.util.solve.DistanceConstraint;
import org.six11.util.solve.NumericValue;
import org.six11.util.solve.VariableBank;
import org.six11.util.solve.VariableBank.ConstraintFilter;

public class SketchBook {

  List<Sequence> scribbles; // raw ink, as the user provided it.
  List<Ink> ink;

  private DrawingBufferLayers layers;
  private Set<Stencil> selectedStencils;
  private Set<Segment> selectedSegments;
  private GraphicDebug guibug;
  private Set<Segment> geometry;
  private List<GuidePoint> guidePoints;
  private ConstraintAnalyzer constraintAnalyzer;
  private ConstraintSolver solver;
  private CornerFinder cornerFinder;
  private int pointCounter = 1;
  private SketchRecognizerController recognizer;
  private Set<UserConstraint> userConstraints;
  private Set<Stencil> stencils;
  private SkruiFabEditor editor;
  private boolean draggingSelection;
  private BufferedImage draggingThumb;
  private GlassPane glass;
  private boolean lastInkWasSelection;
  private List<GuidePoint> activeGuidePoints;
  private Set<Guide> derivedGuides;
  private Set<Guide> retainedVisibleGuides;
  private GuidePoint draggingGuidePoint;
  private Material.Units masterUnits = Units.Centimeter;
  private ActionFactory actionFactory;
  private Stack<SafeAction> actions;
  private Stack<SafeAction> redoActions;

  public SketchBook(GlassPane glass, SkruiFabEditor editor) {
    this.glass = glass;
    this.editor = editor;
    this.scribbles = new ArrayList<Sequence>();
    this.selectedStencils = new HashSet<Stencil>();
    this.selectedSegments = new HashSet<Segment>();
    this.cornerFinder = new CornerFinder();
    this.geometry = new HashSet<Segment>();
    this.guidePoints = new ArrayList<GuidePoint>();
    this.activeGuidePoints = new ArrayList<GuidePoint>();
    this.derivedGuides = new HashSet<Guide>();
    this.retainedVisibleGuides = new HashSet<Guide>();
    this.stencils = new HashSet<Stencil>();
    this.userConstraints = new HashSet<UserConstraint>();
    this.ink = new ArrayList<Ink>();
    this.actionFactory = new ActionFactory(this);
    this.actions = new Stack<SafeAction>();
    this.redoActions = new Stack<SafeAction>();
    this.constraintAnalyzer = new ConstraintAnalyzer(this);
    this.solver = new ConstraintSolver();
    solver.runInBackground();
    this.recognizer = new SketchRecognizerController(this);
    addRecognizer(new EncircleRecognizer(this));
    addRecognizer(new SelectGestureRecognizer(this));
    addRecognizer(new EraseGestureRecognizer(this));
    addRecognizer(new DotReferenceGestureRecognizer(this));
    addRecognizer(new DotSelectGestureRecognizer(this));
    addRecognizer(new RightAngleBrace(this));
    addRecognizer(new SameLengthGesture(this));

  }

  public SkruiFabEditor getEditor() {
    return editor;
  }

  private void addRecognizer(SketchRecognizer rec) {
    recognizer.add(rec);
  }

  public Set<Stencil> getSelectedStencils() {
    return selectedStencils;
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
  }

  public void addInk(Ink newInk) {
    cornerFinder.findCorners(newInk);
    // this is the part where encircle gestures should be found since they have precedence
    Collection<RecognizedRawItem> rawResults = recognizer.analyzeSingleRaw(newInk);

    // iterate through everything and remove the trumps
    Set<RecognizedRawItem> doomed = new HashSet<RecognizedRawItem>();
    for (RecognizedRawItem a : rawResults) {
      if (!doomed.contains(a)) {
        for (RecognizedRawItem b : rawResults) {
          if (a != b && !doomed.contains(b) && a.trumps(b)) {
            bug(a + " trumps " + b);
            doomed.add(b);
          }
        }
      }
    }
    rawResults.removeAll(doomed);
    boolean didSomething = false;
    for (RecognizedRawItem item : rawResults) {
      item.activate(this);
      didSomething = true;
    }
    if (!didSomething) {
      newInk.setGuides(retainedVisibleGuides);
      ink.add(newInk);
      DrawingBuffer buf = layers.getLayer(GraphicDebug.DB_UNSTRUCTURED_INK);
      Sequence scrib = newInk.getSequence();
      DrawingBufferRoutines.drawShape(buf, scrib.getPoints(),
          DrawingBufferLayers.DEFAULT_DRY_COLOR, DrawingBufferLayers.DEFAULT_DRY_THICKNESS);
      lastInkWasSelection = false;
    }
    layers.repaint();
  }

  public void removeInk(Ink oldInk) {
    ink.remove(oldInk);
    DrawingBuffer buf = layers.getLayer(GraphicDebug.DB_UNSTRUCTURED_INK);
    buf.clear();
    for (Ink eenk : ink) {
      Sequence scrib = eenk.getSequence();
      DrawingBufferRoutines.drawShape(buf, scrib.getPoints(),
          DrawingBufferLayers.DEFAULT_DRY_COLOR, DrawingBufferLayers.DEFAULT_DRY_THICKNESS);
    }
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

  public void clearSelectedStencils() {
    setSelectedStencils(null);
  }

  public void clearSelectedSegments() {
    setSelectedSegments(null);
  }

  public void addGeometry(Segment seg) {
    geometry.add(seg);
  }

  public void removeGeometry(Segment seg) {
    bug("remove " + seg);
    // remove from the list of known geometry.
    geometry.remove(seg);

    // deselect the segment. no effect if it isn't already.
    selectedSegments.remove(seg);

    // turn on/off text gathering if there is now exactly one selected seg.
    editor.getGlass().setGatherText(selectedSegments.size() == 1);

    // remove points from the solver if they are no longer part of the model.
    boolean keep1 = false;
    boolean keep2 = false;
    for (Segment s : geometry) {
      if (s.involves(seg.getP1())) {
        keep1 = true;
      }
      if (s.involves(seg.getP2())) {
        keep2 = true;
      }
    }
    Set<Constraint> dead = new HashSet<Constraint>();
    if (!keep1) {
      dead.addAll(solver.removePoint(seg.getP1()));
    }
    if (!keep2) {
      dead.addAll(solver.removePoint(seg.getP2()));
    }

    // remove stencils if it was made with the deleted one.
    Set<Stencil> doomed = new HashSet<Stencil>();
    Set<Stencil> childrenOfDoomed = new HashSet<Stencil>(); // the new book by Frank Herbert
    for (Stencil stencil : stencils) {
      stencil.removeGeometry(seg);
      if (!stencil.isValid()) {
        doomed.add(stencil);
        childrenOfDoomed.addAll(stencil.getChildren());
      }
    }
    if (doomed.size() > 0) {
      bug("Removing stencils: " + num(doomed, " "));
      stencils.removeAll(doomed);
    }
    stencils.addAll(childrenOfDoomed);

    // remove related constraints from the UserConstraints, and remove the 
    // UserConstraints when they are no longer useful.
    Set<UserConstraint> removeUs = new HashSet<UserConstraint>();
    for (UserConstraint uc : userConstraints) {
      uc.getConstraints().removeAll(dead);
      uc.removeInvalid();
      if (!uc.isValid()) {
        removeUs.add(uc);
      }
    }
    for (UserConstraint uc : removeUs) {
      removeUserConstraint(uc);
    }
  }

  public Set<Segment> getGeometry() {
    return geometry;
  }

  public Segment getSegment(Pt blue, Pt green) {
    Segment ret = null;
    for (Segment s : geometry) {
      if (s.involves(blue) && s.involves(green)) {
        ret = s;
        break;
      }
    }
    return ret;
  }

  public boolean hasSegment(Pt blue, Pt green) {
    return getConstraints().hasPoints(blue, green) && getSegment(blue, green) != null;
  }

  public boolean hasSegment(Segment s) {
    return hasSegment(s.getP1(), s.getP2());
  }

  public ConstraintAnalyzer getConstraintAnalyzer() {
    return constraintAnalyzer;
  }

  public ConstraintSolver getConstraints() {
    return solver;
  }

  public void replace(Pt capPt, Pt spot) {
    if (!ConstraintSolver.hasName(spot)) {
      ConstraintSolver.setName(spot, nextPointName());
    }
    // points and constraints
    solver.replacePoint(capPt, spot);
    // segment geometry
    for (Segment seg : geometry) {
      seg.replace(capPt, spot);
    }
    for (Stencil s : stencils) {
      s.replacePoint(capPt, spot);
    }
  }

  public void replace(Segment oldSeg, Segment newSeg) {
    geometry.remove(oldSeg); // remove old geom
    geometry.add(newSeg); // add new geom
    if (selectedSegments.contains(oldSeg)) { // old seg is selected...
      selectedSegments.remove(oldSeg); // deselect old segment.
      selectedSegments.add(newSeg); // select new segment
    }
    // determine which end of 'oldSeg' is going to be replaced with which end of 'newSeg'.
    Pt oldPt = null;
    Pt newPt = null;
    if (oldSeg.getP1() == newSeg.getP1()) { //        keep old.p1 and new.p1
      oldPt = oldSeg.getP2();
      newPt = newSeg.getP2();
    } else if (oldSeg.getP1() == newSeg.getP2()) { // keep old.p1 and new.p2
      oldPt = oldSeg.getP2();
      newPt = newSeg.getP1();
    } else if (oldSeg.getP2() == newSeg.getP2()) { // keep old.p2 and new.p2
      oldPt = oldSeg.getP1();
      newPt = newSeg.getP1();
    } else if (oldSeg.getP2() == newSeg.getP1()) { // keep old.p2 and new.p1
      oldPt = oldSeg.getP1();
      newPt = newSeg.getP2();
    } else {
      Debug.stacktrace("Something wrong here...", 10);
    }

    if (oldPt != null && newPt != null) {
      if (!ConstraintSolver.hasName(newPt)) {
        ConstraintSolver.setName(newPt, nextPointName());
      }
      // points and constraints
      solver.replacePoint(oldPt, newPt);
    }
    getConstraints().wakeUp();
    editor.drawStuff();
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
    clearSelectedStencils();
    clearSelectedSegments();
    clearStructured();
    getConstraints().clearConstraints();
    userConstraints.clear();
    guidePoints.clear();
    activeGuidePoints.clear();
    derivedGuides.clear();
    layers.clearScribble();
    layers.clearAllBuffers();
    layers.repaint();
    editor.getGrid().clear();
    editor.getCutfilePane().clear();
    actions.clear();
    redoActions.clear();
  }

  private void clearStructured() {
    geometry.clear();
    stencils.clear();
  }

  public void removeRelated(Ink eenk) {
    Set<Segment> doomed = new HashSet<Segment>();
    for (Segment seg : geometry) {
      if (seg.getInk() == eenk) {
        doomed.add(seg);
        getConstraints().removePoint(seg.getP1());
        getConstraints().removePoint(seg.getP2());
      }
    }
    geometry.removeAll(doomed);
    getConstraints().wakeUp();
  }

  public Set<UserConstraint> getUserConstraints(Set<Constraint> manyC) {
    Set<UserConstraint> ret = new HashSet<UserConstraint>();
    for (Constraint c : manyC) {
      ret.add(getUserConstraint(c));
    }
    ret.remove(null); // just in case.
    return ret;
  }

  public UserConstraint getUserConstraint(Constraint c) {
    UserConstraint ret = null;
    for (UserConstraint item : userConstraints) {
      if (item.getConstraints().contains(c)) {
        ret = item;
        break;
      }
    }
    return ret;
  }

  public String getMondoDebugString() {
    StringBuilder buf = new StringBuilder();
    String format = "%-14s%-5s%-6s%-6s\n";
    String constrFormat = "%-20s%s\n";
    StringBuilder ptBuf = new StringBuilder();
    int indent = 0;
    addBug(indent, buf, "All debug info\n\n");
    addBug(indent, buf, "Constraint Engine Points: " + getConstraints().getPoints().size() + "\n");
    indent++;
    for (Pt pt : getConstraints().getPoints()) {
      addBug(indent, buf,
          String.format("%-6s%-4.2f %-4.2f\n", SketchBook.n(pt), pt.getX(), pt.getY()));
    }
    indent--;
    buf.append("\n");
    addBug(indent, buf, "Constraint Engine Constraints:\n");
    indent++;
    for (Constraint c : getConstraints().getConstraints()) {
      ptBuf.setLength(0);
      for (Pt cPt : c.getRelatedPoints()) {
        ptBuf.append(SketchBook.n(cPt) + " ");
      }
      addBug(indent, buf, String.format(constrFormat, c.getType(), ptBuf.toString()));
    }
    indent--;
    buf.append("\n");
    addBug(indent, buf, geometry.size() + " segments in 'geometry':\n");
    addBug(indent, buf, String.format(format, "seg-type", "id", "p1", "p2"));
    addBug(indent, buf, "--------------------------\n");
    for (Segment seg : geometry) {
      String p1 = (seg.getP1().hasAttribute("name")) ? seg.getP1().getString("name") : "<?>";
      String p2 = (seg.getP2().hasAttribute("name")) ? seg.getP2().getString("name") : "<?>";
      addBug(indent, buf, String.format(format, seg.getType() + "", seg.getId() + "", p1, p2));
    }
    buf.append("\n");
    addBug(indent, buf, stencils.size() + " stencils\n");
    indent++;
    for (Stencil s : stencils) {
      addBug(indent, buf, s.toString() + "\n");
      indent++;
      addBug(indent, buf, s.getPath().size() + " points: " + SketchBook.n(s.getPath()) + "\n");
      addBug(indent, buf, s.getSegs().size() + " segments: " + SketchBook.ns(s.getSegs()) + "\n");
      indent--;
    }
    buf.append("\n");
    indent--;
    addBug(indent, buf, userConstraints.size() + " user constraints\n");
    format = "%-20s%-4s\n";
    addBug(indent, buf, String.format(format, "Constr. Name", "#"));
    addBug(indent, buf, "-------------------------\n");
    format = "%-20s%-4d\n";
    indent++;

    for (UserConstraint uc : userConstraints) {
      addBug(indent, buf, String.format(format, uc.getName(), uc.getConstraints().size()));
      indent++;
      for (Constraint c : uc.getConstraints()) {
        ptBuf.setLength(0);
        for (Pt cPt : c.getRelatedPoints()) {
          ptBuf.append(SketchBook.n(cPt) + " ");
        }
        addBug(indent, buf, String.format(constrFormat, c.getType(), ptBuf.toString()));
      }
      indent--;
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
    if (doomed.size() > 0) {
      bug("removing stencils: " + num(doomed, " "));
      stencils.removeAll(doomed);
    }
    Set<Stencil> done = new HashSet<Stencil>();
    StencilFinder.merge(stencils, done);
    stencils = done;
  }

  /**
   * Find a set of segments whose fuzzy areas (Segment.getFuzzyArea()) intersect the given Area.
   */
  public Collection<Segment> findSegments(Area area, double fuzzyFactor) {
    Collection<Segment> ret = new HashSet<Segment>();
    for (Segment seg : geometry) {
      Area segmentArea = seg.getFuzzyArea(fuzzyFactor);
      Area ix = (Area) area.clone();
      ix.intersect(segmentArea);
      if (!ix.isEmpty()) {
        ret.add(seg);
      }
    }
    return ret;
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

  public void setSelectedStencils(Collection<Stencil> selectUs) {
    selectedStencils.clear();
    if (selectUs != null) {
      selectedStencils.addAll(selectUs);
    }
    editor.drawStencils();
  }

  public void setSelectedSegments(Collection<Segment> selectUs) {
    if (!lastInkWasSelection || selectUs == null) {
      selectedSegments.clear();
    }
    lastInkWasSelection = true;
    if (selectUs != null) {
      selectedSegments.addAll(selectUs);
    }
    editor.getGlass().setGatherText(selectedSegments.size() == 1);
    editor.drawStuff();
  }

  public boolean isPointOverSelection(Pt where) {
    boolean ret = false;
    for (Stencil s : selectedStencils) {
      Area shapeArea = new Area(s.getShape(true));
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

  public Collection<Segment> findRelatedSegments(Pt pt) {
    Collection<Segment> ret = new HashSet<Segment>();
    for (Segment seg : geometry) {
      if (seg.involves(pt)) {
        ret.add(seg);
      }
    }
    return ret;
  }

  public Collection<Pt> findPoints(Area area) {
    Collection<Pt> ret = new HashSet<Pt>();
    for (Segment seg : geometry) {
      if (area.contains(seg.getP1())) {
        ret.add(seg.getP1());
      }
      if (area.contains(seg.getP2())) {
        ret.add(seg.getP2());
      }
    }
    return ret;
  }

  public Set<Segment> getSelectedSegments() {
    return selectedSegments;
  }

  public boolean isSelected(Segment s) {
    return selectedSegments.contains(s);
  }

  public void deselectSegments(Collection<Segment> unselectUs) {
    selectedSegments.removeAll(unselectUs);
    editor.getGlass().setGatherText(selectedSegments.size() == 1);
  }

  public void addTextProgress(String string) {
    DrawingBuffer db = layers.getLayer("text");
    db.clear();
    if (selectedSegments.size() == 1) {
      Segment seg = selectedSegments.toArray(new Segment[1])[0];
      Pt mid = seg.getVisualMidpoint();
      DrawingBufferRoutines.text(db, mid, string, Color.BLACK);
      lastInkWasSelection = false;
    }
    layers.repaint();
  }

  public void addTextFinished(String string) {
    DrawingBuffer db = layers.getLayer("text");
    db.clear();
    if (selectedSegments.size() == 1) {
      try {
        Segment seg = selectedSegments.toArray(new Segment[1])[0];
        double len = Double.parseDouble(string);
        len = Material.toPixels(masterUnits, len);
        constrainSegmentLength(seg, len);
      } catch (NumberFormatException george) {
      }
    }
    lastInkWasSelection = false;
  }

  private void constrainSegmentLength(Segment seg, double len) {
    Set<ConstraintFilter> filters = new HashSet<ConstraintFilter>();
    filters.add(VariableBank.getTypeFilter(DistanceConstraint.class));
    filters.add(ConstraintFilters.getInvolvesFilter(seg.getEndpointArray()));
    Set<Constraint> results = getConstraints().getVars().searchConstraints(filters);
    Set<UserConstraint> ucs = getUserConstraints(results);
    if (ucs.size() == 0) {
      SameLengthUserConstraint uc = new SameLengthUserConstraint(this);
      uc.addDist(seg.getP1(), seg.getP2(), new NumericValue(len));
      addUserConstraint(uc);
      getConstraints().wakeUp();
    } else if (ucs.size() == 1) {
      UserConstraint uc = ucs.toArray(new UserConstraint[1])[0];
      if (uc instanceof SameLengthUserConstraint) {
        SameLengthUserConstraint sluc = (SameLengthUserConstraint) uc;
        sluc.setValue(new NumericValue(len));
        getConstraints().wakeUp();
      }
    }
  }

  public void addUserConstraint(UserConstraint uc) {
    if (uc != null) {
      userConstraints.add(uc);
      for (Constraint c : uc.getConstraints()) {
        getConstraints().addConstraint(c);
      }
    }
    getConstraints().wakeUp();
  }

  public void removeUserConstraint(UserConstraint uc) {
    if (uc != null && userConstraints.contains(uc)) {
      for (Constraint c : uc.getConstraints()) {
        getConstraints().removeConstraint(c);
      }
      userConstraints.remove(uc);
    }
    getConstraints().wakeUp();
  }

  public Collection<UserConstraint> getUserConstraints() {
    return userConstraints;
  }

  public void addGuidePoint(GuidePoint p) {
    guidePoints.add(p);
    toggleGuidePoint(p);
    editor.drawStuff();
  }

  public List<GuidePoint> getGuidePoints() {
    return guidePoints;
  }

  public Set<Guide> getDerivedGuides() {
    return derivedGuides;
  }

  public void toggleGuidePoint(GuidePoint gpt) {
    if (activeGuidePoints.contains(gpt)) {
      activeGuidePoints.remove(gpt);
    } else {
      activeGuidePoints.add(gpt);
    }
    while (activeGuidePoints.size() > 3) {
      activeGuidePoints.remove(0);
    }
    fixDerivedGuides();

    editor.drawStuff();
  }

  public void fixDerivedGuides() {
    // fix the derived guides
    derivedGuides.clear();
    Pt[] pts = new Pt[activeGuidePoints.size()];
    int i = 0;
    for (GuidePoint g : activeGuidePoints) {
      pts[i++] = g.getLocation();
    }
    switch (activeGuidePoints.size()) {
      case 1:
        derivedGuides.add(makeDerivedCircle(pts[0], null, false));
        derivedGuides.add(new GuideLine(pts[0], null));
        break;
      case 2:
        derivedGuides.add(new GuideLine(pts[0], pts[1]));
        derivedGuides.add(makeDerivedCircle(pts[0], pts[1], true));
        derivedGuides.add(makeDerivedCircle(pts[0], pts[1], false));
        derivedGuides.add(makeDerivedCircle(pts[1], pts[0], false));
        Pt mid = Functions.getMean(pts);
        derivedGuides.add(new GuidePoint(mid));
        Vec v = new Vec(pts[0], pts[1]).getNormal();
        Pt elsewhere = v.add(mid);
        derivedGuides.add(new GuideLine(mid, elsewhere));
        break;
      case 3:
        if (!Functions.arePointsColinear(pts)) {
          Pt center = Functions.getCircleCenter(pts[0], pts[1], pts[2]);
          derivedGuides.add(new GuidePoint(center));
          derivedGuides.add(makeDerivedCircle(center, pts[1], false));
        }
        break;
      default:
    }
  }

  /**
   * Makes a circle based on two points. If bothOnOutside is true, it returns a circle where a and b
   * are on opposite sides (the circle center is the midpoint of a and b). Otherwise, it uses point
   * a as the circle center and point b as a reference point on the outside.
   * 
   * @param a
   *          circle center or an outside point
   * @param b
   *          an outside point, or null if the radius is not fixed and if the current hover point
   *          should be used
   * @param bothOnOutside
   *          determines the sematics of point a.
   * @return
   */
  private Guide makeDerivedCircle(Pt a, Pt b, boolean bothOnOutside) {
    Guide ret = null;
    if (bothOnOutside) {
      Pt mid = Functions.getMean(a, b);
      ret = makeDerivedCircle(mid, b, false);
    } else {
      ret = new GuideCircle(a, b);
    }
    return ret;
  }

  public List<GuidePoint> getActiveGuidePoints() {
    return activeGuidePoints;
  }

  public void retainVisibleGuides() {
    retainedVisibleGuides.clear();
    for (Guide g : derivedGuides) {
      retainedVisibleGuides.add(g.getFixedCopy());
    }
    retainedVisibleGuides.addAll(guidePoints);
    for (Guide g : retainedVisibleGuides) {
      g.setFixedHover(layers.getHoverPoint());
    }
  }

  public Collection<GuidePoint> findGuidePoints(Area area) {
    Collection<GuidePoint> ret = new HashSet<GuidePoint>();
    for (GuidePoint gp : guidePoints) {
      if (area.contains(gp.getLocation())) {
        ret.add(gp);
      }
    }
    return ret;
  }

  public void removeGuidePoint(GuidePoint removeMe) {
    guidePoints.remove(removeMe);
    if (activeGuidePoints.contains(removeMe)) {
      toggleGuidePoint(removeMe);
    }
    editor.drawStuff();
  }

  public Collection<GuidePoint> findGuidePoints(Pt pt, boolean activeOnly) {
    Collection<GuidePoint> ret = new HashSet<GuidePoint>();
    Collection<GuidePoint> in = activeOnly ? activeGuidePoints : guidePoints;
    if (activeOnly) {
      for (GuidePoint gpt : in) {
        if (gpt.getLocation().distance(pt) < 6) {
          ret.add(gpt);
        }
      }
    }
    return ret;
  }

  public void setDraggingGuidePoint(GuidePoint dragMe) {
    if (dragMe != null) {
    } else {
      getConstraints().wakeUp();
    }
    draggingGuidePoint = dragMe;
    editor.drawStuff();
  }

  public boolean isDraggingGuide() {
    return draggingGuidePoint != null;
  }

  public GuidePoint getDraggingGuide() {
    return draggingGuidePoint;
  }

  public void dragGuidePoint(Pt pt) {
    draggingGuidePoint.setLocation(pt);
    fixDerivedGuides();
    editor.drawStuff();
  }

  public Units getMasterUnits() {
    return masterUnits;
  }

  public void undo() {
    if (!actions.isEmpty()) {
      SafeAction a = actions.pop();
      bug("Undo " + a.getName());
      redoActions.push(a);
      a.backward();
    }
  }

  public void redo() {
    if (!redoActions.isEmpty()) {
      SafeAction a = redoActions.pop();
      bug("Redo " + a.getName());
      actions.push(a);
      a.forward();
    }
  }

  public ActionFactory getActionFactory() {
    return actionFactory;
  }

  public void addAction(SafeAction a) {
    actions.push(a);
    redoActions.clear();
    a.forward();
  }

  public void removeSingularSegments() {
    Set<Segment> doomed = new HashSet<Segment>();
    for (Segment s : geometry) {
      if (s.isSingular() && !s.isClosed()) {
        doomed.add(s);
      }
    }
    if (doomed.size() > 0) {
      bug("\t*** Removing " + doomed.size() + " singular segments ***");
      for (Segment d : doomed) {
        removeGeometry(d);
      }
    }
  }

  public static String n(Pt pt) {
    return pt.getString("name");
  }

  public static String n(Collection<Pt> pts) {
    StringBuilder buf = new StringBuilder();
    if (pts == null) {
      buf.append("<null input!>");
    } else {
      for (Pt pt : pts) {
        buf.append(n(pt) + " ");
      }
    }
    return buf.toString();
  }

  public static String ns(Collection<Segment> segs) {
    StringBuilder buf = new StringBuilder();
    if (segs == null) {
      buf.append("<null input!>");
    } else {
      for (Segment seg : segs) {
        buf.append(seg.getType() + "-" + seg.getId() + " ");
      }
    }
    return buf.toString();
  }
}
