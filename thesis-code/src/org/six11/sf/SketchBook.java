package org.six11.sf;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

import java.awt.Color;
import java.awt.Component;
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

import org.imgscalr.Scalr;
import org.six11.sf.Material.Units;
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
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.Vec;
import org.six11.util.solve.Constraint;
import org.six11.util.solve.ConstraintSolver;
import org.six11.util.solve.DistanceConstraint;
import org.six11.util.solve.MultisourceNumericValue;
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
    this.constraintAnalyzer = new ConstraintAnalyzer(this);
    this.solver = new ConstraintSolver();
    solver.runInBackground();
    solver.createUI();
    this.recognizer = new SketchRecognizerController(this);
    addRecognizer(new EncircleRecognizer(this));
    addRecognizer(new SelectGestureRecognizer(this));
    addRecognizer(new EraseGestureRecognizer(this));
    addRecognizer(new DotReferenceGestureRecognizer(this));
    addRecognizer(new DotSelectGestureRecognizer(this));
    //    addRecognizer(new Arrow(this));
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
      DrawingBufferRoutines.drawShape(buf, scrib.getPoints(), DrawingBufferLayers.DEFAULT_DRY_COLOR,
          DrawingBufferLayers.DEFAULT_DRY_THICKNESS);
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
      DrawingBufferRoutines.drawShape(buf, scrib.getPoints(), DrawingBufferLayers.DEFAULT_DRY_COLOR,
          DrawingBufferLayers.DEFAULT_DRY_THICKNESS);
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
    geometry.remove(seg);
    selectedSegments.remove(seg);
    editor.getGlass().setGatherText(selectedSegments.size() == 1);
    boolean keep1 = false;
    boolean keep2 = false;
    Set<Constraint> dead = new HashSet<Constraint>();
    for (Segment s : geometry) {
      if (s.involves(seg.getP1())) {
        keep1 = true;
      }
      if (s.involves(seg.getP2())) {
        keep2 = true;
      }
    }
    if (!keep1) {
      dead.addAll(solver.removePoint(seg.getP1()));
    }
    if (!keep2) {
      dead.addAll(solver.removePoint(seg.getP2()));
    }
    Set<Stencil> doomed = new HashSet<Stencil>();
    for (Stencil stencil : stencils) {
      if (stencil.involves(seg)) {
        doomed.add(stencil);
      }
    }
    stencils.removeAll(doomed);
    Set<UserConstraint> removeUs = new HashSet<UserConstraint>();
    for (UserConstraint uc : userConstraints) {
      int before = uc.getConstraints().size();
      bug("Before, " + uc + " has " + before);
      uc.getConstraints().removeAll(dead);
      int after = uc.getConstraints().size();
      bug("After, " + uc + " has " + after);
      if (uc.getConstraints().isEmpty()) {
        bug("Will remove empty user constraint: " + uc);
        removeUs.add(uc);
      }
    }
    userConstraints.removeAll(removeUs);
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
      if (selectedStencils.contains(s)) {
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
    bug("cleared");
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
    bug("constrain " + seg + " to " + num(len));
    Set<ConstraintFilter> filters = new HashSet<ConstraintFilter>();
    filters.add(VariableBank.getTypeFilter(DistanceConstraint.class));
    filters.add(ConstraintFilters.getInvolvesFilter(seg.getEndpointArray()));
    Set<Constraint> results = getConstraints().getVars().searchConstraints(filters);
    bug("There are " + results.size() + " existing length constraints related to " + seg);
    if (results.size() == 0) {
      Constraint distConst = new DistanceConstraint(seg.getP1(), seg.getP2(), new NumericValue(len));
      UserConstraint uc = SameLengthGesture.makeUserConstraint(this, null,
          Collections.singleton(distConst));
      bug("Adding user constraint for numeric distance");
      addUserConstraint(uc);
      //      registerConstraint(distConst);
    } else if (results.size() == 1) {
      DistanceConstraint distConst = (DistanceConstraint) results.toArray(new Constraint[1])[0];
      NumericValue numVal = distConst.getValue();
      bug("Found one constraint. value: " + numVal.getClass() + " = " + numVal.getValue());
      if (numVal instanceof MultisourceNumericValue) {
        MultisourceNumericValue val = (MultisourceNumericValue) numVal;
        // RecognizedItem otherDistItem = getConstraintItem(distConst);
      }
    }
  }

  public void addUserConstraint(UserConstraint uc) {
    if (uc != null) {
      userConstraints.add(uc);
      for (Constraint c : uc.getConstraints()) {
        getConstraints().addConstraint(c);
      }
      bug(userConstraints.size() + " user constraints.");
      for (Ink itemInk : uc.getInk()) {
        removeRelated(itemInk);
      }
      getConstraints().wakeUp();
    }
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
      bug("Dragging guide point!");
    } else {
      bug("Stop dragging guide point!");
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
}
