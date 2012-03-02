package org.six11.sf;

// J|mmyJ0hn$
import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;
import static java.lang.Math.abs;

import java.awt.Color;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import javax.swing.Timer;

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
import org.six11.sf.rec.SameAngleGesture;
import org.six11.sf.rec.SameLengthGesture;
import org.six11.sf.rec.SelectGestureRecognizer;
import org.six11.util.Debug;
import org.six11.util.data.Lists;
import org.six11.util.data.RankedList;
import org.six11.util.data.Statistics;
import org.six11.util.gui.shape.Areas;
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
import org.six11.util.solve.NumericValue;
import org.six11.util.solve.VariableBank;
import org.six11.util.solve.VariableBank.ConstraintFilter;

public class SketchBook {

  private static final String POINT_NAME = "name";
  private static final double ERASE_SAMPLE_DIST_THRESHOLD = 20;
  private static final long ERASE_SAMPLE_TIME_LIMIT = 100;
  private static final double ERASE_ANGLE_CHANGE_THRESH = Math.PI / 2;
  private static final double ERASE_ELIGIBILITY_DIST = 15;
  private static final int ERASE_PSEUDOCORNER_THRESH = 4;
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
  private Timer inactivityTimer;
  private SnapshotMachine snapshotMachine;
  boolean erasing;
  private boolean loadingSnapshot;

  public SketchBook(GlassPane glass, SkruiFabEditor editor) {
    this.glass = glass;
    this.editor = editor;
    this.snapshotMachine = new SnapshotMachine(this);
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
    this.solver.setFrameRate(SkruiFabEditor.FRAME_RATE);
    solver.setDebugOut(true);
    try {
      solver.setDebugOutWriter(new BufferedWriter(new FileWriter("solver.txt")));
    } catch (IOException e) {
      e.printStackTrace();
    }
    solver.runInBackground();
    this.recognizer = new SketchRecognizerController(this);
    addRecognizer(new EncircleRecognizer(this));
    addRecognizer(new SelectGestureRecognizer(this));
    //    addRecognizer(new EraseGestureRecognizer(this));
    addRecognizer(new DotReferenceGestureRecognizer(this));
    addRecognizer(new DotSelectGestureRecognizer(this));
    addRecognizer(new RightAngleBrace(this));
    addRecognizer(new SameLengthGesture(this));
    addRecognizer(new SameAngleGesture(this));

    inactivityTimer = new Timer(1300, new ActionListener() {
      public void actionPerformed(ActionEvent ev) {
        if (!getUnanalyzedInk().isEmpty()) {
          SketchBook.this.editor.go();
        }
      }
    });
    inactivityTimer.setRepeats(false);
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

  public void addStencil(Stencil s) {
    stencils.add(s);
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
    } else {
      getSnapshotMachine().requestSnapshot("raw ink caused a change");
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
    inactivityTimer.stop();
    Sequence scrib = new Sequence();
    scrib.add(pt);
    scribbles.add(scrib);
    return scrib;
  }

  public Sequence addScribble(Pt pt) {
    inactivityTimer.stop();
    Sequence scrib = Lists.getLast(scribbles);
    if (!scrib.getLast().isSameLocation(pt)) { // Avoid duplicate point in
      // scribble
      scrib.add(pt);
      if (!erasing) {
        analyzeForErase(scrib);
      }
    }
    return scrib;
  }

  private void analyzeForErase(Sequence scrib) {
    if (!scrib.hasAttribute("erase")) {
      int i = scrib.size() - 2; // second-to-last-point. this is the 'cursor'.
      int iNext = i + 1; // next point
      int iPrev = i - 1; // previous point
      if (i == 0) { // only one point. set curvilinear dist to zero and call it a day
        scrib.getFirst().setDouble("erase_curvidist", 0.0);
        List<Pt> samples = new ArrayList<Pt>();
        samples.add(scrib.getFirst());
        scrib.setAttribute("samples", samples);
        scrib.setAttribute("erase_pseudocorners", 0);
      } else if (i > 0) {

        // 1. set curvilinear distance along the scribble. uses i and iPrev
        Pt prev = scrib.get(iPrev);
        Pt here = scrib.get(i);
        double prevDist = prev.getDouble("erase_curvidist");
        double segDist = prev.distance(here);
        double newDist = prevDist + segDist;
        here.setDouble("erase_curvidist", newDist);
        if (i > 0) { // three or more points so we can calculate and store heading at i 
          Pt next = scrib.get(iNext);
          Vec heading = new Vec(prev, next).getUnitVector();
          here.setVec("erase_heading", heading);
          if (i == 1) {
            // when we have two points, we can give the first one
            // its heading by copying the current one.
            prev.setVec("erase_heading", heading);
          }
        }

        // 1.5: don't bother adding for psuedocorners if the pen 
        // has not moved very far from the original point. this avoids 
        // erasing when doing things like making fat dots.
        //
        // First establish if we are just now becoming eligible or not.
        boolean eligible = scrib.hasAttribute("erase_eligible");
        if (!eligible) {
          Pt first = scrib.getFirst();
          eligible = first.distance(here) > ERASE_ELIGIBILITY_DIST;
          if (eligible) {
            scrib.setAttribute("erase_eligible", true);
          }
        }

        // 2. see if the new point should be a sample. If it is, try to detect
        // a pseudo-corner.
        List<Pt> samples = (List<Pt>) scrib.getAttribute("samples");
        Pt recentSample = Lists.getLast(samples);
        double sampleDist = recentSample.getDouble("erase_curvidist");
        if (newDist - sampleDist > ERASE_SAMPLE_DIST_THRESHOLD) {
          samples.add(here);
          // 2b: Pseudo-corner detection. Get recent samples and compare their 
          // headings with the current one. Big deviations indicate a corner
          // somewhere between 'here' and the other sample.
          Vec heading = here.getVec("erase_heading");
          long sampleStopTime = here.getTime() - ERASE_SAMPLE_TIME_LIMIT;
          for (int sampleIdx = samples.size() - 2; sampleIdx >= 0; sampleIdx--) {
            Pt r = samples.get(sampleIdx);
            if (r.hasAttribute("erase_pseudocorner")) {
              break;
            }
            if (r.getTime() < sampleStopTime) {
              break;
            }
            Vec rHeading = r.getVec("erase_heading");
            double angle = Functions.getSignedAngleBetween(heading, rHeading);
            if (abs(angle) > ERASE_ANGLE_CHANGE_THRESH) {
              r.setBoolean("erase_pseudocorner", true);
              int numCorners = (Integer) scrib.getAttribute("erase_pseudocorners");
              numCorners = numCorners + 1;
              eligible = scrib.hasAttribute("erase_eligible");
              scrib.setAttribute("erase_pseudocorners", numCorners);
              if (numCorners > ERASE_PSEUDOCORNER_THRESH && eligible) {
                // this could be an erase. But we don't want to erase if the gesture is 
                // possibly just a quickly drawn circe (e.g. latching something).
                if (!detectCircle(samples)) {
                  scrib.setAttribute("erase", true);
                  Pt killSpot = Functions.getMean(samples);
                  scrib.setAttribute("erase_spot", killSpot);
                  editor.drawErase();
                } else {
                  bug("Detected circle. Not erasing.");
                }
              }
            }
          }
        }
      }
    }
  }

  public boolean detectCircle(List<Pt> points) {
    boolean ret = false;
    Pt centroid = Functions.getMean(points);
    Statistics crosses = new Statistics();
    Vec prevV = null;
    for (Pt pt : points) {
      if (prevV == null) {
        prevV = new Vec(centroid, pt);
      } else {
        Vec here = new Vec(centroid, pt);
        double cross = here.cross(prevV);
        crosses.addData(cross);
        prevV = here;
      }
    }
    if (Math.abs(crosses.getMean()) > 230) {
      ret = true;
    }
    return ret;
  }

  public boolean isErasing() {
    boolean ret = false;
    Sequence scrib = Lists.getLast(scribbles);
    if (scrib != null && scrib.hasAttribute("erase")) {
      ret = true;
    }
    return ret;
  }

  public Pt getEraseSpot() {
    Pt ret = null;
    Sequence scrib = Lists.getLast(scribbles);
    if (scrib != null && scrib.hasAttribute("erase_spot")) {
      ret = (Pt) scrib.getAttribute("erase_spot");
    }
    return ret;
  }

  public void eraseUnderPoints(List<Pt> killZone) {
    ConvexHull hull = new ConvexHull(killZone);
    final Area hullArea = new Area(hull.getHullShape());
    final Collection<Segment> doomed = pickDoomedSegments(hullArea);
    final Collection<Ink> doomedInk = pickDoomedInk(hullArea, null);

    if (doomedInk.size() > 0) {
      for (Ink ink : doomedInk) {
        removeInk(ink);
      }
    } else {
      for (Segment seg : doomed) {
        bug("Erase " + seg.typeIdPtStr());
        removeGeometry(seg);
      }
    }
    Set<Stencil> invaid = new HashSet<Stencil>();
    for (Stencil stencil : stencils) {
      if (!stencil.isValid()) {
        invaid.add(stencil);
      }
      stencil.removeInvalidChildren(new HashSet<Stencil>());
    }
    stencils.removeAll(invaid);
    getEditor().drawStuff();

  }

  public Collection<Segment> pickDoomedSegments(Area area) {
    Collection<Segment> maybeDoomed = new HashSet<Segment>();
    RankedList<Segment> ranked = new RankedList<Segment>();
    for (Segment seg : getGeometry()) {
      Area segmentArea = seg.getFuzzyArea(5.0);
      Area ix = (Area) area.clone();
      ix.intersect(segmentArea);
      if (!ix.isEmpty()) {
        double surfaceArea = Areas.approxArea(ix, 1.0);
        double segSurfaceArea = Areas.approxArea(segmentArea, 1.0);
        double ratio = surfaceArea / segSurfaceArea;
        ranked.add(ratio, seg);
      }
    }
    if (ranked.size() > 0) {
      double thresh = ranked.getHighestScore() * 0.7;
      maybeDoomed.addAll(ranked.getHigherThan(thresh));
    }
    return maybeDoomed;
  }

  public Collection<Ink> pickDoomedInk(Area area, Ink gestureInk) {
    Collection<Ink> doomed = new HashSet<Ink>();
    RankedList<Ink> ranked = new RankedList<Ink>();
    for (Ink ink : getUnanalyzedInk()) {
      if (ink == gestureInk) {
        continue;
      }
      Area inkArea = ink.getFuzzyArea(5.0);
      Area ix = (Area) area.clone();
      ix.intersect(inkArea);
      if (!ix.isEmpty()) {
        double surfaceArea = Areas.approxArea(ix, 1.0);
        double segSurfaceArea = Areas.approxArea(inkArea, 1.0);
        double ratio = surfaceArea / segSurfaceArea;
        ranked.add(ratio, ink);
      }
    }
    if (ranked.size() > 0) {
      double thresh = ranked.getHighestScore() * 0.7;
      doomed.addAll(ranked.getHigherThan(thresh));
    }
    return doomed;
  }

  public Sequence endScribble(Pt pt) {
    Sequence ret = null;
    inactivityTimer.start();
    Sequence scrib = (Sequence) Lists.getLast(scribbles);
    if (scrib.hasAttribute("erase")) {
      scrib.setAttribute("erase_spot", null);
      eraseUnderPoints((List<Pt>) scrib.getAttribute("samples"));
    } else {
      ret = scrib;
    }
    return ret;
  }

  public void setLayers(DrawingBufferLayers layers) {
    this.layers = layers;
  }

  public CornerFinder getCornerFinder() {
    return cornerFinder;
  }

  public List<Ink> getUnanalyzedInk() {
    inactivityTimer.stop();
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
    setSelectedStencils(new HashSet<Stencil>());
  }

  public void clearSelectedSegments() {
    setSelectedSegments(new HashSet<Segment>());
  }

  public void addGeometry(Segment seg) {
    geometry.add(seg);
  }

  public void removeGeometry(Segment seg) {
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
      bug("Does " + stencil + " involve segment " + seg.typeIdPtStr() + "?");
      stencil.removeGeometry(seg);
      boolean v = stencil.isValid();
      if (!v) {
        bug("Determined stencil " + stencil.getId() + " involves the killed segment.");
        doomed.add(stencil);
        childrenOfDoomed.addAll(stencil.getChildren());
      }
    }
    // at this point, the doomed stencils should be given another chance. use the 
    // segment list for each as input to the stencil finder.
    Set<Segment> segsFromDoomed = new HashSet<Segment>();
    for (Stencil sd : doomed) {
      segsFromDoomed.addAll(sd.getSegs());
    }
    editor.findStencils(segsFromDoomed);
    
    
    if (doomed.size() > 0) {
      for (Stencil ds : doomed) {
        bug("Removing stencil from list: " + ds);
        stencils.remove(ds);
      }
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

  public Segment getSegment(int id) {
    Segment ret = null;
    for (Segment s : geometry) {
      if (s.getId() == id) {
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
    try {
      clearInk();
      clearStructured();
      clearSelectedStencils();
      clearSelectedSegments();
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
    } catch (Exception ex) {
      ex.printStackTrace();
    }

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
    String rightNow = Debug.now();
    addBug(indent, buf, "All debug info\t\t\t" + rightNow + "\n\n");
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
      addBug(indent, buf, "Stencil " + s.getId() + " " + (s.isValid() ? "(ok)" : "(** INVAID **)")
          + "\n");
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
    HashSet<Stencil> parents = new HashSet<Stencil>();
    for (Stencil sten : done) {
      sten.removeInvalidChildren(parents);
    }
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
        try {
          ConvexHull stencilHull = new ConvexHull(s.getAllPoints());//s.getPath());
          double stencilArea = stencilHull.getConvexArea();
          ConvexHull ixHull = new ConvexHull(ShapeFactory.makePointList(ix.getPathIterator(null)));
          double ixArea = ixHull.getConvexArea();
          ratio = ixArea / stencilArea;
        } catch (Exception ex) {
          bug("got " + ex.getClass() + " for stencil " + s);
        }
      }
      if (ratio >= d) {
        ret.add(s);
      }
    }
    return ret;
  }

  public Collection<Stencil> findStencilOld(Area area, double d) { // TODO: erase this when findStencil works
    Collection<Stencil> ret = new HashSet<Stencil>();
    for (Stencil s : stencils) {
      double ratio = 0;
      Area ix = s.intersect(area);
      if (!ix.isEmpty()) {
        ConvexHull stencilHull = new ConvexHull(s.getAllPoints());//s.getPath());
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
    boolean same = Lists.areSetsEqual(selectUs, selectedStencils);
    selectedStencils.clear();
    if (selectUs != null) {
      selectedStencils.addAll(selectUs);
    }
    editor.drawStencils();
    if (!same) {
      getSnapshotMachine().requestSnapshot("Stencil selection changed");
    }
  }

  public void setSelectedSegments(Collection<Segment> selectUs) {
    boolean same = Lists.areSetsEqual(selectUs, selectedSegments);
    if (!lastInkWasSelection || selectUs == null) {
      selectedSegments.clear();
    }
    lastInkWasSelection = true;
    if (selectUs != null) {
      selectedSegments.addAll(selectUs);
    }
    editor.getGlass().setGatherText(selectedSegments.size() == 1);
    editor.drawStuff();
    if (!same) {
      getSnapshotMachine().requestSnapshot("Segment selection changed");
    }
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
      bug("Adding user constraint: " + uc + " with " + uc.getConstraints().size()
          + " basic constraints");
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
          if (center.distance(pts[0]) < 800) {
            derivedGuides.add(new GuidePoint(center));
            derivedGuides.add(makeDerivedCircle(center, pts[1], false));
          } else {
            bug("Guide circle would be huge. Not including it.");
          }
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
        if (gpt.getLocation().distance(pt) < DotReferenceGestureRecognizer.NEARNESS_THRESHOLD) {
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
      if (draggingGuidePoint != null) {
        getSnapshotMachine().requestSnapshot("Done dragging a guide point");
      }
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

  public void undoPreview() {
    bug("undo preview");
    Snapshot s = snapshotMachine.undo();
    if (s != null) {
      bug("Valid undo");
      layers.setPreview(s.getPreview());
    }
  }

  public void redoPreview() {
    bug("redo preview");
    Snapshot s = snapshotMachine.redo();
    if (s != null) {
      bug("Valid redo.");
      layers.setPreview(s.getPreview());
    }
  }

  public void undoRedoComplete() {
    bug("finalizing redo/undo");
    layers.clearPreview();
    Snapshot s = snapshotMachine.getCurrent();
    loadingSnapshot = true;
    snapshotMachine.load(s);
    loadingSnapshot = false;
  }

  public ActionFactory getActionFactory() {
    return actionFactory;
  }

  public void addAction(SafeAction a) {
    actions.push(a);
    redoActions.clear();
    a.forward();
    getConstraints().wakeUp();
    editor.drawStuff();
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
    return pt.getString(POINT_NAME);
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

  public Set<Segment> splitSegment(Segment seg, Pt nearPt) {
    //    bug("Split!");
    Set<Segment> ret = new HashSet<Segment>();
    List<Pt> points = seg.asPolyline();//seg.getPointList();
    //    bug("there are " + points.size() + " points in the polyline for " + seg.typeIdStr());
    Pt spot = Functions.getNearestPointOnSequence(nearPt, points);
    int splitIdx = -1;
    for (int i = 0; i < points.size() - 1; i++) {
      Pt a = points.get(i);
      Pt b = points.get(i + 1);
      boolean inside = Functions.arePointsColinear(new Pt[] {
          spot, a, b
      }) && Functions.isPointInLineSegment(spot, a, b, Functions.EQ_TOL);
      if (inside) {
        splitIdx = i;
        break;
      }
    }
    if (splitIdx >= 0) {
      Pt boundaryA = points.get(splitIdx);
      Pt boundaryB = points.get(splitIdx + 1);
      Line tinySegment = new Line(boundaryA, boundaryB);
      Pt splitPoint = Functions.getNearestPointOnLine(nearPt, tinySegment);
      nearPt.setLocation(splitPoint.x, splitPoint.y);
      List<Pt> sideA = new ArrayList<Pt>();
      for (int i = 0; i <= splitIdx; i++) {
        sideA.add(points.get(i));
      }
      sideA.add(nearPt);
      List<Pt> sideB = new ArrayList<Pt>();
      sideB.add(nearPt);
      for (int i = splitIdx + 1; i < points.size(); i++) {
        sideB.add(points.get(i));
      }
      sideA.remove(0);
      sideA.add(0, seg.getP1());
      sideB.remove(sideB.size() - 1);
      sideB.add(seg.getP2());
      //      bug("sideA[0] should be same as seg.p1: " + n(sideA.get(0)) + " == " + n(seg.getP1()));
      //      bug("sideB[n-1] should be same as seg.p2: " + n(sideB.get(sideB.size() - 1)) + " == " + n(seg.getP2()));
      Segment segA = null;
      Segment segB = null;
      switch (seg.getType()) {
        case Line:
          segA = new Segment(new LineSegment(sideA.get(0), sideA.get(sideA.size() - 1)));
          segB = new Segment(new LineSegment(sideB.get(0), sideB.get(sideB.size() - 1)));
          ret.add(segA);
          ret.add(segB);
          break;
        case EllipticalArc:
          segA = new Segment(new EllipseArcSegment(sideA));
          segB = new Segment(new EllipseArcSegment(sideB));
          ret.add(segA);
          ret.add(segB);
          break;
        case Curve:
          segA = new Segment(new CurvySegment(sideA));
          segB = new Segment(new CurvySegment(sideB));
          ret.add(segA);
          ret.add(segB);
          break;
        case CircularArc:
        case Blob:
        case Circle:
        case Dot:
        case Ellipse:
        case Unknown:
          bug("Don't know how to split segment type " + seg.getType());
          ret.add(seg);
          break;
      }
      if (ret.size() == 2) {
        SafeAction action = getActionFactory().split(seg, ret);
        addAction(action);
        editor.findStencils(ret);
      }
    }

    return ret;
  }

  public static boolean hasName(Pt p) {
    return p.hasAttribute(SketchBook.POINT_NAME);
  }

  /*
   * User has performed an original action that will cause the given segment to be split into parts.
   * This will create an action that works with undo/redo.
   */
  public Set<Segment> injectPoint(final Segment seg, final Pt nearPt, SketchBook model) {
    Set<Segment> babySegments = model.splitSegment(seg, nearPt);
    //    bug("Split Point: " + num(nearPt));
    //    bug("Old: " + seg.bugStr());
    //    StringBuilder buf = new StringBuilder();
    //    for (Segment b : babySegments) {
    //      buf.append(b.bugStr() + " ");
    //    }
    //    bug("New: " + buf.toString());
    Segment baby = Lists.getOne(babySegments);
    if (baby != null) {
      Line line = baby.asLine();
      Vec lineVec = new Vec(baby.getP1(), baby.getP2());
      final Vec param = Segment.calculateParameterForPoint(lineVec.mag(), line, nearPt);
      GuidePoint gp = new GuidePoint(baby, param);
      //    bug("Point parameter: " + num(param));
      //    bug("New Guide Point: " + Segment.bugStr(gp.getLocation()));
      model.addGuidePoint(gp);
    } else {
      bug("sweet baby segment is null while segmenting " + seg.typeIdPtStr() + " near " + n(nearPt)
          + ". model.splitSegment gave me " + babySegments.size() + " segments.");
    }
    return babySegments;
    //    bug("Added guide point attached to " + baby.bugStr());
  }

  /**
   * Find the set of user constraints related to the given segment. If you need both endpoints of
   * the segment to be in the user constraint, set both=true. Otherwise, only one endpoint has to be
   * involved.
   * 
   * @param seg
   * @param both
   * @return
   */
  public Set<UserConstraint> findUserConstraints(Segment seg, boolean both) {
    Set<UserConstraint> ucs = new HashSet<UserConstraint>();
    for (UserConstraint c : getUserConstraints()) {
      if (both) {
        if (c.involves(seg.getP1()) && c.involves(seg.getP2())) {
          bug(c
              + " is related to the split segment (both ends). I don't do anything with this yet! FIXME");
          ucs.add(c);
        }
      } else {
        if (c.involves(seg.getP1()) || c.involves(seg.getP2())) {
          bug(c
              + " is related to the split segment (one or both ends). I don't do anything with this yet! FIXME");
          ucs.add(c);
        }
      }
    }
    return ucs;
  }

  public SnapshotMachine getSnapshotMachine() {
    return snapshotMachine;
  }

  public boolean isLoadingSnapshot() {
    return loadingSnapshot;
  }

  public Stencil getStencil(int stencilID) {
    Stencil ret = null;
    for (Stencil s : stencils) {
      if (s.getId() == stencilID) {
        ret = s;
        break;
      }
    }
    return ret;
  }

  /**
   * Returns a singular segment (circle, ellipse, blob) that uses the given point. It returns null
   * if none are found.
   * 
   * @return
   */
  public Segment getSegment(Pt p) {
    Segment ret = null;
    for (Segment s : getGeometry()) {
      if (s.isSingular() && s.getP1() == p) {
        ret = s;
        break;
      }
    }
    return ret;
  }

}
