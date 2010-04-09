package org.six11.skrui.script;

import java.awt.Color;
import java.util.*;

import org.six11.skrui.BoundedParameter;
import org.six11.skrui.DrawingBufferRoutines;
import org.six11.skrui.FlowSelection;
import org.six11.skrui.GestureRecognizer;
import org.six11.skrui.Scribbler;
import org.six11.skrui.SkruiScript;
import org.six11.skrui.data.AngleGraph;
import org.six11.skrui.data.LengthGraph;
import org.six11.skrui.data.PointGraph;
import org.six11.skrui.data.TimeGraph;
import org.six11.skrui.domain.Domain;
import org.six11.skrui.domain.Shape;
import org.six11.skrui.domain.ShapeRenderer;
import org.six11.skrui.domain.ShapeTemplate;
import org.six11.skrui.domain.SimpleDomain;
import org.six11.skrui.mesh.Mesh;
import org.six11.skrui.shape.ArcSegment;
import org.six11.skrui.shape.Dot;
import org.six11.skrui.shape.Ellipse;
import org.six11.skrui.shape.LineSegment;
import org.six11.skrui.shape.Polyline;
import org.six11.skrui.shape.Primitive;
import org.six11.skrui.shape.Segment;
import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.pen.*;

/**
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Neanderthal extends SkruiScript implements SequenceListener, HoverListener {

  private static final String K_DO_RECOGNITION = "do-recognition";
  private static final String K_COHORT_TIMEOUT = "cohort-timeout"; // 1300;
  private static final String K_COHORT_LENGTH_MULT = "cohort-length-mult"; // 0.3

  public static final String SCRAP = "Sequence already dealt with";
  public static final String MAIN_SEQUENCE = "main sequence";
  public static final String PRIMITIVES = "primitives";

  public enum Certainty {
    Yes, No, Maybe, Unknown
  }

  public static Arguments getArgumentSpec() {
    Arguments args = new Arguments();
    args.setProgramName("Neanderthal: a primitive ink processor.");
    args.setDocumentationProgram("This performs primitive (and not so primitive) analysis of "
        + "sketch input.");

    Map<String, BoundedParameter> defs = getDefaultParameters();
    for (String k : defs.keySet()) {
      BoundedParameter p = defs.get(k);
      args.addFlag(p.getKeyName(), ArgType.ARG_OPTIONAL, ValueType.VALUE_REQUIRED, p
          .getDocumentation()
          + " Defaults to " + p.getValueStr() + ". ");
    }
    return args;
  }

  public static Map<String, BoundedParameter> getDefaultParameters() {
    Map<String, BoundedParameter> defs = new HashMap<String, BoundedParameter>();
    defs.put(K_DO_RECOGNITION, new BoundedParameter.Boolean(K_DO_RECOGNITION, "Do recognition",
        "Should the Neanderthal do advanced sketch recognition?", false));
    defs.put(K_COHORT_TIMEOUT, new BoundedParameter.Integer(K_COHORT_TIMEOUT, "Cohort timeout",
        "Maximum time (milliseconds) that elapses between strokes that are "
            + "considered together for recognition", 1300));
    defs.put(K_COHORT_LENGTH_MULT, new BoundedParameter.Double(K_COHORT_LENGTH_MULT,
        "Cohort distance multiplier", "Multiplier of distance between strokes that are "
            + "considered together for recognition. This is multiplied with "
            + "the length of a pen stroke.", 0.01, 1.0, 0.3));
    return defs;
  }

  private static void bug(String what) {
    Debug.out("Neanderthal", what);
  }

  // ///
  //
  // * Put member variable declarations here. *
  //
  // //
  boolean animationInitialized = false;
  TimeGraph tg;
  AngleGraph ag;
  PointGraph allPoints;
  PointGraph endPoints;
  LengthGraph lenG;
  Domain domain;
  GestureRecognizer gr;
  List<Shape> recognizedShapes;
  FlowSelection fs;
  Scribbler scribble;
  PointGraph structurePoints;
  List<LineSegment> structureLines;
  HoverEvent lastHoverEvent;
  List<Mesh> meshes;

  @Override
  public void initialize() {
    bug("Neanderthal is alive!");
    domain = new SimpleDomain("Simple domain", this);
    allPoints = new PointGraph();
    endPoints = new PointGraph();
    structurePoints = new PointGraph();
    structureLines = new ArrayList<LineSegment>();
    tg = new TimeGraph();
    ag = new AngleGraph();
    lenG = new LengthGraph();
    gr = new GestureRecognizer(this);
    recognizedShapes = new ArrayList<Shape>();
    meshes = new ArrayList<Mesh>();
    main.getDrawingSurface().getSoup().addSequenceListener(this);
    main.getDrawingSurface().getSoup().addHoverListener(this);
    fs = new FlowSelection(this);
    scribble = new Scribbler(this);
  }

  public PointGraph getStructurePoints() {
    return structurePoints;
  }

  public TimeGraph getTimeGraph() {
    return tg;
  }

  public AngleGraph getAngleGraph() {
    return ag;
  }

  public PointGraph getAllPoints() {
    return allPoints;
  }

  public PointGraph getEndPoints() {
    return endPoints;
  }

  public LengthGraph getLenthGraph() {
    return lenG;
  }

  public void addMesh(Mesh mesh) {
    meshes.add(mesh);
  }

  @SuppressWarnings("unused")
  private void output(String... stuff) {
    boolean first = true;
    for (String v : stuff) {
      if (!first) {
        System.out.print(", ");
      }
      first = false;
      System.out.print(v);
    }
    System.out.print("\n");
  }

  @SuppressWarnings("unused")
  private void output(double... stuff) {
    boolean first = true;
    for (double v : stuff) {
      if (!first) {
        System.out.print(", ");
      }
      first = false;
      System.out.print(Debug.num(v));
    }
    System.out.print("\n");
  }

  private double updatePathLength(Sequence seq) {
    int cursor = seq.size() - 1;
    while (cursor > 0 && !seq.get(cursor).hasAttribute("path-length")) {
      cursor--;
    }
    if (cursor == 0) {
      seq.get(cursor).setDouble("path-length", 0);
    }
    for (int i = cursor; i < seq.size() - 1; i++) {
      double dist = seq.get(i).distance(seq.get(i + 1));
      double v = seq.get(i).getDouble("path-length") + dist;
      seq.get(i + 1).setDouble("path-length", v);
    }
    return seq.get(seq.size() - 1).getDouble("path-length");
  }

  @SuppressWarnings("unused")
  private int sumCertainty(Certainty... certainties) {
    int sum = 0;
    for (Certainty c : certainties) {
      switch (c) {
        case Maybe:
        case Yes:
          sum += 1;
      }
    }
    return sum;
  }

  public void handleHoverEvent(HoverEvent hoverEvent) {
    lastHoverEvent = hoverEvent;
    whackStructuredInk();
  }

  /**
   * When the pen moves around inside the drawing area, we want structural ink to be redrawn as a
   * function of what the user is doing, and where the pen is. Consult 'lastHoverEvent' to know
   * what's going on.
   */
  private void whackStructuredInk() {
    if (structurePoints.size() > 0) {
      if (lastHoverEvent != null) {
        switch (lastHoverEvent.getType()) {
          case In:
          case Hover:
            drawStructuredInk();
            break;
          case Out:
            hideStructuredInk();
            break;
        }
      }
    }
  }

  private void hideStructuredInk() {
    if (getSoup().getBuffer("structured") != null) {
      getSoup().getBuffer("structured").setVisible(false);
      main.getDrawingSurface().repaint();
    }
  }

  private void drawStructuredInk() {
    DrawingBuffer db = new DrawingBuffer();
    Pt pen = lastHoverEvent.getPt();
    Color debugColor = new Color(1f, 0.3f, 0.3f, 1f);
    for (Pt st : structurePoints.getPoints()) {
      // double dist = st.distance(pen);
      // double alpha = calcAlpha(dist);
      // Color c = new Color(0.6f, 0.6f, 0.6f, (float) alpha);
      Color c = debugColor;
      DrawingBufferRoutines.cross(db, st, 3.5, c);
    }
    for (LineSegment line : structureLines) {
      // double dist = Functions.getDistanceBetweenPointAndLine(pen, line.getGeometryLine());
      // double alpha = calcAlpha(dist);
      // Color c = new Color(0.6f, 0.6f, 0.6f, (float) alpha);
      Color c = debugColor;
      DrawingBufferRoutines.screenLine(db, main.getDrawingSurface().getBounds(), line
          .getGeometryLine(), c, 0.7);
    }
    List<Pt> rec = structurePoints.getRecent(30000, pen.getTime());
    // Color veryLightGray = new Color(0.6f, 0.6f, 0.6f, (float) 0.3);
    Color veryLightGray = debugColor;
    if (rec.size() > 0) {
      // draw a line from pen to the most recent point
      Pt st = rec.get(0);
      DrawingBufferRoutines.line(db, pen, st, veryLightGray, 0.5);
    }
    for (int i = 0; i < rec.size() - 1; i++) {
      // draw lines connecting each dot to the previous
      Pt here = rec.get(i);
      Pt there = rec.get(i + 1);
      Line ghostLine = new Line(here, there);
      Pt mid = ghostLine.getMidpoint();
      DrawingBufferRoutines.line(db, ghostLine, veryLightGray, 0.5);
      DrawingBufferRoutines.cross(db, mid, 2.3, veryLightGray);
    }
    if (rec.size() > 2) {
      // when there are three points, make a circle and show its center.
      Pt center = Functions.getCircleCenter(rec.get(0), rec.get(1), rec.get(2));
      if (center != null) { // if 0-1-2 are colinear, center is null. avoid NPEs
        double radius = rec.get(0).distance(center);
        DrawingBufferRoutines.dot(db, center, radius, 0.5, veryLightGray, null);
        DrawingBufferRoutines.dot(db, center, 3.0, 0.3, veryLightGray, veryLightGray);
      }
    }
    getSoup().addBuffer("structured", db);
  }

  public static double calcAlpha(double dist) {
    double minAlpha = 0.05;
    double maxAlpha = 0.6;
    double minDist = 10;
    double maxDist = 150;
    double ret = 0;
    if (dist < minDist) {
      ret = maxAlpha;
    } else if (dist > maxDist) {
      ret = minAlpha;
    } else {
      ret = maxAlpha - ((dist - minDist) / (maxDist - minDist)) * (maxAlpha - minAlpha);
    }
    return ret;
  }

  public void handleSequenceEvent(SequenceEvent seqEvent) {
    if (!animationInitialized && main.getScript("Animation") != null) {
      Animation ani = (Animation) main.getScript("Animation");
      ani.startAnimation(main.getDrawingSurface().getBounds(), "png");
      animationInitialized = true;
    }
    SequenceEvent.Type type = seqEvent.getType();
    Sequence seq = seqEvent.getSeq();
    switch (type) {
      case BEGIN:
        seq.getLast().setSequence(MAIN_SEQUENCE, seq);
        scribble.sendDown(seq);
        fs.sendDown(seq);
        break;
      case PROGRESS:
        if (lastHoverEvent != null) {
          lastHoverEvent.setPt(seq.getLast());
          whackStructuredInk();
        }
        seq.getLast().setSequence(MAIN_SEQUENCE, seq);
        updatePathLength(seq);
        scribble.sendDrag();
        fs.sendDrag(seq.getLast());
        break;
      case END:
        fs.sendUp();
        scribble.sendUp();
        if (seq.getAttribute(SCRAP) == null/* && seq.size() > 1 */) {
          processFinishedSequence(seq);
        }
        break;
    }
  }

  private void processFinishedSequence(Sequence seq) {
    bug("processing...");
    tg.add(seq);
    // explicity map points back to their original sequence so we can find it later.
    allPoints.addAll(seq);
    // Create a 'primitives' set where dots/ellipses/polyline elements will go.
    seq.setAttribute(PRIMITIVES, new TreeSet<Primitive>(Primitive.sortByIndex));
    // All the 'detectXYZ' methods will insert a Primitive into seq's primitives set.
    detectDot(seq);
    detectVeryShortLines(seq);
    detectEllipse(seq);
    detectPolyline(seq);

    for (Primitive prim : getPrimitiveSet(seq)) {
      endPoints.add(prim.getSeq().get(prim.getStartIdx()));
      endPoints.add(prim.getSeq().get(prim.getEndIdx()));
      if (prim instanceof LineSegment) {
        ag.add((LineSegment) prim);
      }
      lenG.add(prim);
      addPrimitiveToPoints(prim);
    }

    gr.add(seq);

    for (Primitive prim : getPrimitiveSet(seq)) {
      if (prim instanceof Dot) {
        fs.sendTap((Dot) prim);
      }
    }

    if (getParam(K_DO_RECOGNITION).getBoolean()) {
      Set<Primitive> recent = getPrimitiveSet(seq);
      Set<Primitive> cohorts = getCohorts(recent);
      long start = System.currentTimeMillis();
      List<Shape> results;
      Set<Shape> newShapes = new HashSet<Shape>();
      for (ShapeTemplate st : domain.getTemplates()) {
        results = st.apply(cohorts);
        newShapes.addAll(merge(results));
      }
      long end = System.currentTimeMillis();
      bug("Applied " + domain.getTemplates().size() + " templates to " + cohorts.size()
          + " primitive shapes in " + (end - start) + " ms.");
      drawNewShapes(newShapes);
    }
    // DEBUGGING stuff below here. comment out if you want.

    // drawPrims(recent, Color.RED, "recent");
    // cohorts.removeAll(recent);
    // drawPrims(cohorts, Color.GREEN, "3");
    // drawParallelPerpendicular(seq);
    // drawAdjacent(seq);
    // drawSimilarLength(seq);
    drawDots(scribble.getPossibleCorners(), "7");
    // drawDots(seq, true, true, true, false, false, "4");
    // drawDots(seq, false, false, false, true, false, "5");
    // drawDots(seq, false, false, false, false, true, "6");
    // drawDots(seq, true, false, false, false, false, "7");
  }

  private void drawDots(List<Pt> spots, String bufferName) {
    if (spots.size() > 1) {
      DrawingBuffer db = main.getDrawingSurface().getSoup().getBuffer(bufferName);
      if (db == null) {
        db = new DrawingBuffer();
        main.getDrawingSurface().getSoup().addBuffer(bufferName, db);
      }
      DrawingBufferRoutines.dots(db, spots, 2.5, 0.3, Color.BLACK, Color.PINK);
    }
  }

  @SuppressWarnings("unused")
  private void drawDots(Sequence seq, boolean plain, boolean curvy, boolean slow, boolean both,
      boolean corner, String bufferName) {
    if (seq.size() > 1) {
      DrawingBuffer db = main.getDrawingSurface().getSoup().getBuffer(bufferName);
      if (db == null) {
        db = new DrawingBuffer();
        main.getDrawingSurface().getSoup().addBuffer(bufferName, db);
        db.setVisible(false);
      }
      List<Pt> plainDots = new ArrayList<Pt>();
      List<Pt> curvyDots = new ArrayList<Pt>();
      List<Pt> slowDots = new ArrayList<Pt>();
      List<Pt> bothDots = new ArrayList<Pt>();
      List<Pt> cornerDots = new ArrayList<Pt>();
      for (Pt pt : seq) {
        if (plain) {
          plainDots.add(pt);
        }
        if (curvy && pt.getBoolean("curvy")) {
          curvyDots.add(pt);
        }
        if (slow && pt.getBoolean("slow")) {
          slowDots.add(pt);
        }
        if (both && pt.getBoolean("both")) {
          bothDots.add(pt);
        }
        if (corner && pt.getBoolean("corner")) {
          cornerDots.add(pt);
        }
      }
      if (plainDots.size() > 0) {
        DrawingBufferRoutines.dots(db, plainDots, 2.5, 0.3, Color.BLACK, null);
      }
      if (curvyDots.size() > 0) {
        DrawingBufferRoutines.dots(db, curvyDots, 2.5, 0.3, Color.BLACK, Color.YELLOW);
      }
      if (slowDots.size() > 0) {
        DrawingBufferRoutines.dots(db, slowDots, 2.5, 0.3, Color.BLACK, Color.BLUE);
      }
      if (bothDots.size() > 0) {
        DrawingBufferRoutines.dots(db, bothDots, 2.5, 0.3, Color.BLACK, Color.GREEN);
      }
      if (cornerDots.size() > 0) {
        DrawingBufferRoutines.dots(db, cornerDots, 2.5, 0.3, Color.BLACK, Color.RED);
      }
    }
  }

  private Set<Shape> merge(List<Shape> results) {
    Set<Shape> unique = new HashSet<Shape>();
    for (Shape s : results) {
      if (!recognizedShapes.contains(s)) {
        unique.add(s);
        recognizedShapes.add(s);
      }
    }
    return unique;
  }

  @SuppressWarnings("unchecked")
  public static SortedSet<Primitive> getPrimitiveSet(Sequence seq) {
    return (SortedSet<Primitive>) seq.getAttribute(Neanderthal.PRIMITIVES);
  }

  private void drawNewShapes(Collection<Shape> newShapes) {
    if (newShapes.size() > 0) {
      DrawingBuffer db = main.getDrawingSurface().getSoup().getBuffer("2");
      if (db == null) {
        db = new DrawingBuffer();
        main.getDrawingSurface().getSoup().addBuffer("2", db);
      }
      for (Shape s : newShapes) {
        ShapeRenderer ren = domain.getRenderer(s.getName());
        if (ren != null) {
          ren.draw(db, s);
        }
      }
    }
  }

  @SuppressWarnings("unused")
  private void drawPrims(Set<Primitive> inSet, Color color, String name) {
    DrawingBuffer db = new DrawingBuffer();
    boolean dirty = false;
    for (Primitive prim : inSet) {
      if (prim instanceof LineSegment) {
        dirty = true;
        DrawingBufferRoutines.patch(db, prim.getSeq(), prim.getStartIdx(), prim.getEndIdx(), 2.0,
            color);
        DrawingBufferRoutines.text(db, Functions.getMean(prim.getP1(), prim.getP2()).getTranslated(
            5, 10), prim.getShortStr(), Color.BLACK);
      }
    }
    if (dirty) {
      main.getDrawingSurface().getSoup().addBuffer(name, db);
    } else {
      main.getDrawingSurface().getSoup().removeBuffer(name);
    }

  }

  /**
   * For each point in the primitive, add the primitive to the point's primitive list.
   */
  @SuppressWarnings("unchecked")
  private void addPrimitiveToPoints(Primitive prim) {
    for (int i = prim.getStartIdx(); i <= prim.getEndIdx(); i++) {
      Pt pt = prim.getSeq().get(i);
      if (!pt.hasAttribute(PRIMITIVES)) {
        pt.setAttribute(PRIMITIVES, new TreeSet<Primitive>(Primitive.sortByIndex));
      }
      ((TreeSet<Primitive>) pt.getAttribute(PRIMITIVES)).add(prim);
    }
  }

  /**
   * After a strokes primitives have been found, we can try to do recognition. In order to do that
   * we need a reasonably inclusive set of related primitives. This includes all primitives that
   * were just made (in the input 'recent' set), as well as previously drawn elements that are
   * spatially or temporally close.
   */
  private Set<Primitive> getCohorts(Set<Primitive> recent) {
    Set<Primitive> ret = new HashSet<Primitive>();
    ret.addAll(recent);
    for (Primitive source : recent) {
      ret.addAll(getNear(source, source.getLength()
          * main.getParam(K_COHORT_LENGTH_MULT).getDouble()));
    }
    List<Sequence> timeRecent = tg.getRecent(main.getParam(K_COHORT_TIMEOUT).getInt());
    for (Sequence s : timeRecent) {
      ret.addAll(getPrimitiveSet(s));
    }
    return ret;
  }

  @SuppressWarnings("unchecked")
  private Set<Primitive> getNear(Primitive source, double dist) {
    Set<Primitive> ret = new HashSet<Primitive>();
    Set<Pt> out = new HashSet<Pt>();
    for (int i = source.getStartIdx(); i <= source.getEndIdx(); i++) {
      out.addAll(allPoints.getNear(source.getSeq().get(i), dist));
    }
    for (Pt pt : out) {
      SortedSet<Primitive> pointPrims = (SortedSet<Primitive>) pt
          .getAttribute(Neanderthal.PRIMITIVES);
      if (pointPrims != null) {
        ret.addAll(pointPrims);
      }
    }
    return ret;
  }

  @SuppressWarnings("unused")
  private void drawSimilarLength(Sequence seq) {
    DrawingBuffer db = new DrawingBuffer();
    boolean dirty = false;
    for (Primitive prim : getPrimitiveSet(seq)) {
      dirty = true;
      DrawingBufferRoutines.patch(db, seq, prim.getStartIdx(), prim.getEndIdx(), 2.0, Color.RED);
      double pathLength = prim.getSeq().getPathLength(prim.getStartIdx(), prim.getEndIdx());
      Set<Primitive> similar = lenG.getSimilar(pathLength, pathLength * 0.25);
      for (Primitive adj : similar) {
        if ((adj.getSeq() == prim.getSeq() && adj.getStartIdx() == prim.getStartIdx() && adj
            .getEndIdx() == prim.getEndIdx()) == false) {
          DrawingBufferRoutines.patch(db, adj.getSeq(), adj.getStartIdx(), adj.getEndIdx(), 2.0,
              Color.GREEN);
        }
      }
    }
    if (dirty) {
      main.getDrawingSurface().getSoup().addBuffer("similength", db);
    } else {
      main.getDrawingSurface().getSoup().removeBuffer("similength");
    }

  }

  @SuppressWarnings("unused")
  private void drawAdjacent(Sequence seq) {
    DrawingBuffer db = new DrawingBuffer();
    boolean dirty = false;
    for (Primitive prim : getPrimitiveSet(seq)) {
      dirty = true;
      // DrawingBufferRoutines.patch(db, seq, prim.getStartIdx(), prim.getEndIdx(), 2.0, Color.RED);
      double pathLength = prim.getSeq().getPathLength(prim.getStartIdx(), prim.getEndIdx());
      double radius = pathLength * 0.20;
      Set<Pt> ends = endPoints.getNear(prim.getSeq().get(prim.getStartIdx()), radius);
      ends.addAll(endPoints.getNear(prim.getSeq().get(prim.getEndIdx()), radius));
      Set<Primitive> adjacentPrims = getPrimitives(ends);
      adjacentPrims.remove(prim);
      for (Primitive adj : adjacentPrims) {
        DrawingBufferRoutines.patch(db, adj.getSeq(), adj.getStartIdx(), adj.getEndIdx(), 2.0,
            Color.GREEN);
      }
    }
    if (dirty) {
      main.getDrawingSurface().getSoup().addBuffer("adjacent", db);
    } else {
      main.getDrawingSurface().getSoup().removeBuffer("adjacent");
    }
  }

  @SuppressWarnings( {
      "unchecked", "unused"
  })
  private void drawParallelPerpendicular(Sequence seq) {
    DrawingBuffer db = new DrawingBuffer();
    boolean dirty = false;
    for (Primitive prim : (SortedSet<Primitive>) seq.getAttribute(Neanderthal.PRIMITIVES)) {
      if (prim instanceof LineSegment) {
        dirty = true;
        DrawingBufferRoutines.patch(db, seq, prim.getStartIdx(), prim.getEndIdx(), 2.0, Color.RED);
        Set<Primitive> set = ag.getNear((LineSegment) prim, 0, 0.2);
        // bug(set.size() + " are parallel to " + ((LineSegment) prim).getFixedAngle() + ": ");
        for (Primitive p : set) {
          // bug("  " + p.getFixedAngle());
          DrawingBufferRoutines.patch(db, p.getSeq(), p.getStartIdx(), p.getEndIdx(), 2.0,
              Color.BLUE);
        }
        set = ag.getNear((LineSegment) prim, Math.PI / 2, 0.2);
        // bug(set.size() + " are perpendicular to " + ((LineSegment) prim).getFixedAngle() + ": ");
        for (Primitive p : set) {
          // bug("  " + p.getFixedAngle());
          DrawingBufferRoutines.patch(db, p.getSeq(), p.getStartIdx(), p.getEndIdx(), 2.0,
              Color.GREEN);
        }
      }
    }
    if (dirty) {
      main.getDrawingSurface().getSoup().addBuffer("paraperp", db);
    } else {
      main.getDrawingSurface().getSoup().removeBuffer("paraperp");
    }
  }

  @SuppressWarnings("unchecked")
  public static Set<Primitive> getPrimitives(Set<Pt> points) {
    // each point has a main sequence. that sequence has a list of primitives. a point may be in
    // zero, one, or more of them. Return all primitives related to the input points.
    SortedSet<Primitive> ret = new TreeSet<Primitive>(Primitive.sortByIndex);
    for (Pt pt : points) {
      Sequence seq = pt.getSequence(MAIN_SEQUENCE);
      int where = seq.indexOf(pt);
      for (Primitive prim : (SortedSet<Primitive>) seq.getAttribute(Neanderthal.PRIMITIVES)) {
        if (where <= prim.getEndIdx() && where >= prim.getStartIdx()) {
          ret.add(prim);
        }
      }
    }
    return ret;
  }

  public static Set<Sequence> getSequences(Set<Primitive> prims) {
    Set<Sequence> ret = new HashSet<Sequence>();
    for (Primitive prim : prims) {
      ret.add(prim.getSeq());
    }
    return ret;
  }

  private Polyline detectPolyline(Sequence seq) {
    Polyline ret = new Polyline(seq, (Animation) main.getScript("Animation"));
    Set<Segment> segs = ret.getSegments();
    // Establish line and arc certainties for each segment.
    for (Segment seg : segs) {
      double lineLenDivCurviLen = seg.getPathLength() / seg.getLineLength();
      // bug("pathLength / lineLength = " + Debug.num(seg.getPathLength()) + " / "
      // + Debug.num(seg.getLineLength()) + " = " + Debug.num(lineLenDivCurviLen));
      if (lineLenDivCurviLen < 1.07) {
        seg.lineCertainty = Certainty.Yes;
        seg.arcCertainty = Certainty.Maybe;
      } else if (lineLenDivCurviLen < 1.27) {
        seg.lineCertainty = Certainty.Maybe;
        seg.arcCertainty = Certainty.Maybe;
      } else {
        seg.lineCertainty = Certainty.No;
        seg.arcCertainty = Certainty.Yes;
      }
    }

    for (Segment seg : segs) {
      if (seg.lineCertainty == Certainty.Yes || seg.lineCertainty == Certainty.Maybe) {
        new LineSegment(seg.seq, seg.start, seg.end, seg.lineCertainty);
      }
      if (seg.arcCertainty == Certainty.Yes || seg.arcCertainty == Certainty.Maybe) {
        new ArcSegment(seg.seq, seg.start, seg.end, seg.arcCertainty);
      }
    }
    return ret;
  }

  private Certainty detectEllipse(Sequence seq) {
    Certainty ret = Certainty.Unknown;

    // First determine if this is a closed shape.
    Pt start = seq.getFirst();
    updatePathLength(seq);
    double totalLength = seq.getLast().getDouble("path-length");
    if (totalLength < 50) {
      ret = Certainty.No;
    } else {
      double lengthThreshold = totalLength * 0.5;

      double endpointDistThreshold = totalLength * 0.30;
      double closestDist = Double.MAX_VALUE;
      Pt closestPoint = null;
      for (Pt pt : seq) {
        double toThisPoint = pt.getDouble("path-length");
        if (toThisPoint > lengthThreshold) {
          double thisDist = start.distance(pt);
          if (thisDist < endpointDistThreshold && thisDist < closestDist) {
            closestPoint = pt;
            closestDist = thisDist;
          }
        }
      }

      if (closestPoint != null) {
        ConvexHull hull = new ConvexHull(seq.getPoints());
        Antipodal antipodes = new Antipodal(hull.getHull());

        Pt centroid = antipodes.getCentroid();
        double angle = antipodes.getAngle();
        double a = antipodes.getFirstDimension() / 2;
        double b = antipodes.getSecondDimension() / 2;
        RotatedEllipse re = new RotatedEllipse(centroid, a, b, angle);
        double errorSum = 0;
        for (Pt pt : seq) {
          Pt intersect = re.getCentroidIntersect(pt);
          double intersectDist = intersect.distance(pt);
          errorSum += intersectDist * intersectDist;
        }
        double normalizedError = errorSum / totalLength;
        double area = re.getArea();
        double areaError = errorSum / area;
        boolean punishLazyPen = closestDist > (endpointDistThreshold / 2);
        if (areaError < 0.4) {
          if (punishLazyPen) {
            ret = Certainty.Maybe;
          } else {
            ret = Certainty.Yes;
          }
        } else if (normalizedError < 9) {
          if (punishLazyPen) {
            ret = Certainty.Maybe;
          } else {
            ret = Certainty.Yes;
          }
        } else if (areaError < 0.9) {
          ret = Certainty.Maybe;
        } else if (normalizedError < 20) {
          ret = Certainty.Maybe;
        } else {
          ret = Certainty.No;
        }
      } else {
        ret = Certainty.No;
      }
    }

    if (ret == Certainty.Yes || ret == Certainty.Maybe) {
      new Ellipse(seq, ret);
    }
    return ret;
  }

  private Certainty detectVeryShortLines(Sequence seq) {
    Certainty cert = Certainty.Unknown;
    if (seq.getPathLength(0, seq.size() - 1) <= Polyline.DUPLICATE_THRESHOLD) {
      double pathLength = seq.getPathLength(0, seq.size() - 1);
      double lineLength = new Line(seq.getFirst(), seq.getLast()).getLength();
      double lineLenDivCurviLen = pathLength / lineLength;
      if (lineLenDivCurviLen < 1.10) {
        cert = Certainty.Yes;
        new LineSegment(seq, 0, seq.size() - 1, cert);
      } else if (lineLenDivCurviLen < 2.2) {
        cert = Certainty.Maybe;
        new LineSegment(seq, 0, seq.size() - 1, cert);
      } else {
        cert = Certainty.No;
      }
    }
    return cert;
  }

  private Certainty detectDot(Sequence seq) {

    ConvexHull hull = new ConvexHull(seq.getPoints());
    Antipodal antipodes = new Antipodal(hull.getHull());
    double density = (double) seq.size() / antipodes.getArea();
    double areaPerAspect = antipodes.getArea() / antipodes.getAspectRatio();
    Certainty cert = Certainty.Unknown;

    if (areaPerAspect < 58) {
      cert = Certainty.Yes;
    } else if (areaPerAspect < 120) {
      cert = Certainty.Maybe;
    } else if (areaPerAspect / (0.3 + density) < 120) {
      cert = Certainty.Maybe;
    } else {
      cert = Certainty.No;
    }
    if (cert == Certainty.Yes || cert == Certainty.Maybe) {
      new Dot(seq, cert); // will insert the dot into seq's primitives list.
    }

    return cert;
  }

  public Map<String, BoundedParameter> initializeParameters(Arguments args) {
    Map<String, BoundedParameter> params = copyParameters(getDefaultParameters());
    for (String k : params.keySet()) {
      if (args.hasFlag(k)) {
        if (args.hasValue(k)) {
          params.get(k).setValue(args.getValue(k));
          bug("Set " + params.get(k).getHumanReadableName() + " to " + params.get(k).getValueStr());
        } else {
          params.get(k).setValue("true");
          bug("Set " + params.get(k).getHumanReadableName() + " to " + params.get(k).getValueStr());
        }
      }
    }
    return params;
  }

  public OliveSoup getSoup() {
    return main.getDrawingSurface().getSoup();
  }

  public void addStructurePoint(Pt pt) {
    structurePoints.add(pt);
  }

  public void addStructureLine(LineSegment line) {
    structureLines.add(line);
  }

  public void forget(Shape s) {
    for (Primitive p : s.getSubshapes()) {
      // each primitive has siblings that must also be forgotten.
      // for example, a line has a corresponding arc for the same points.
      for (Primitive forgetMe : getPrimitiveSet(p.seq)) {
        forget(forgetMe);
      }
    }
  }

  public void forget(Primitive p) {
    ag.remove(p);
    lenG.remove(p);
    bug("Forgetting primitive " + p + ". Before, endPoints has " + endPoints.size());
    endPoints.remove(p.getStartPt());
    endPoints.remove(p.getEndPt());
    bug("Afterwards, endPoints has: " + endPoints.size());
    forget(p.seq); // removes drawing buffer, sets SCRAP, removes points from tg and allPoints.
  }

  public void forget(Sequence seq) {
    seq.setAttribute(SCRAP, true);
    if (getSoup().getDrawingBufferForSequence(seq) != null) {
      getSoup().getDrawingBufferForSequence(seq).setVisible(false);
      seq.setAttribute(SCRAP, true);
    }
    getSoup().removeFinishedSequence(seq);
    tg.remove(seq);
    for (Pt die : seq) {
      allPoints.remove(die);
    }
  }

}

// Did they ever figure out who the man in the jar was?