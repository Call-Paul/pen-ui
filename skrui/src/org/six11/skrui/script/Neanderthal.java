package org.six11.skrui.script;

import java.awt.Color;
import java.util.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.six11.skrui.BoundedParameter;
import org.six11.skrui.SkruiScript;
import org.six11.skrui.data.AngleGraph;
import org.six11.skrui.data.Journal;
import org.six11.skrui.data.LengthGraph;
import org.six11.skrui.data.PointGraph;
import org.six11.skrui.data.TimeGraph;
import org.six11.skrui.domain.Shape;
import org.six11.skrui.shape.*;
import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.pen.*;

/**
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Neanderthal extends SkruiScript implements SequenceListener {

  public static final String SCRAP = "Sequence already dealt with";
  public static final String MAIN_SEQUENCE = "main sequence";
  public static final String PRIMITIVES = "primitives";
  public static final String CORNER = "corner";

  //
  // ---------------------------------------------------------------------- Member declarations.
  //

  //
  // Accounting information for all ink related to drawing.
  //

  /**
   * tg is a TimeGraph that stores Sequences orderd by time.
   */
  TimeGraph tg;

  /**
   * ag is an AngleGraph that stores Primitives (in practice, LineSegment instances but it works for
   * Primitives in general). The primitives are stored in order of angle so it is easy to find
   * primitives (lines) that are close to some angle (like the angle of another primitive).
   */
  AngleGraph ag;

  /**
   * allPoints stores all visible points associated with lines or filled regions. This makes it more
   * efficient to answer questions like 'which points are near X,Y'.
   */
  PointGraph allPoints;

  /**
   * endPoints stores the start and end points of all primitives.
   */
  PointGraph endPoints;

  /**
   * lenG stores Primitive objects in order based on their curvilinear stroke lengths.
   */
  LengthGraph lenG;

  /**
   * structureLines stores all the sometimes-visible structured lines.
   */
  List<LineSegment> structureLines;

  /**
   * structurePoints stores all the structured points.
   */
  PointGraph structurePoints;

  /**
   * forgottenSequences stores ?some sequences that have been removed from the main list of past
   * sequences.
   */
  List<Stroke> forgottenSequences;

  DebugUtil debugUtil;

  private List<SequenceListener> relaySeqEventListeners;
  private List<PrimitiveListener> primitiveListeners;

  @Override
  public void initialize() {
    bug("Neanderthal is alive!");
    debugUtil = new DebugUtil(main);
    relaySeqEventListeners = new ArrayList<SequenceListener>();
    primitiveListeners = new ArrayList<PrimitiveListener>();
    allPoints = new PointGraph();
    endPoints = new PointGraph();
    structurePoints = new PointGraph();
    structureLines = new ArrayList<LineSegment>();
    forgottenSequences = new ArrayList<Stroke>();
    tg = new TimeGraph();
    ag = new AngleGraph();
    lenG = new LengthGraph();
    main.addSequenceListener(this);
  }

  public JSONObject getSaveData(Journal jnl) throws JSONException {
    JSONObject ret = new JSONObject();

    // ---------------------------------------------- forgotten sequences
    JSONArray jsonForgottenSequences = new JSONArray();
    for (Stroke seq : forgottenSequences) {
      JSONObject jsonSeq = jnl.makeJsonSequence(seq);
      if (jsonSeq != null) {
        jsonForgottenSequences.put(jsonSeq);
      }
    }
    ret.put("forgottenSequences", jsonForgottenSequences);

    // ---------------------------------------------- primitives
    List<Stroke> allSeqs = main.getSequences();
    JSONArray jsonPrimArray = new JSONArray();
    for (Stroke seq : allSeqs) {
      if (seq.getAttribute(SCRAP) == null) {
        Collection<Primitive> prims = (Collection<Primitive>) seq.getAttribute(PRIMITIVES);
        for (Primitive p : prims) {
          JSONArray jsonPrim = (JSONArray) jnl.makeJsonObj(p);
          jsonPrimArray.put(jsonPrim);
        }
      }
    }
    ret.put("primitives", jsonPrimArray);

    // ---------------------------------------------- structurePoints
    if (structurePoints.size() > 0) {
      JSONArray jsonStructurePoints = new JSONArray();
      for (Pt pt : structurePoints.getPoints()) {
        jsonStructurePoints.put(jnl.makeFullJsonPt(pt));
      }
      ret.put("structurePoints", jsonStructurePoints);
    }
    // ---------------------------------------------- structureLines
    if (getStructureLines().size() > 0) {
      // TODO: implement structureLine saving.
      bug("Note: the " + getStructureLines().size() + " structure line(s) will not be saved.");
    }

    return ret;
  }

  public void openSaveData(Journal jnl, JSONObject job) throws JSONException {
    // ---------------------------------------------- forgotten sequences
    JSONArray jsonForgottenSequences = job.getJSONArray("forgottenSequences");
    for (int i = 0; i < jsonForgottenSequences.length(); i++) {
      JSONObject jsonSeq = (JSONObject) jsonForgottenSequences.get(i);
      Stroke seq = jnl.makeSequence(jsonSeq);
      jnl.mapSequence(seq.getId(), seq);
    }

    // ---------------------------------------------- primitives
    List<Stroke> allSeqs = main.getSequences();
    for (Stroke seq : allSeqs) {
      seq.setAttribute(PRIMITIVES, new TreeSet<Primitive>(Primitive.sortByIndex));
      getAllPoints().addAll(seq);
    }
    JSONArray jsonPrimArray = job.getJSONArray("primitives");
    for (int i = 0; i < jsonPrimArray.length(); i++) {
      Primitive p = jnl.makePrimitive((JSONArray) jsonPrimArray.get(i));
      recordPrimitive(p);
    }

    // ---------------------------------------------- structurePoints
    JSONArray jsonStructurePoints = job.optJSONArray("structurePoints");
    if (jsonStructurePoints != null) {
      Map<Integer, Pt> strPtMap = new HashMap<Integer, Pt>();
      for (int i = 0; i < jsonStructurePoints.length(); i++) {
        Pt pt = jnl.makeFullLonelyPoint(jsonStructurePoints.getJSONObject(i));
        structurePoints.add(pt);
        strPtMap.put(pt.getID(), pt);
      }
    }

  }

  public PointGraph getStructurePoints() {
    return structurePoints;
  }

  public List<LineSegment> getStructureLines() {
    return structureLines;
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

  public LengthGraph getLengthGraph() {
    return lenG;
  }

  private double updatePathLength(Stroke seq) {
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

  public void handleSequenceEvent(SequenceEvent seqEvent) {

    SequenceEvent.Type type = seqEvent.getType();
    Stroke seq = (Stroke) seqEvent.getSeq();
    switch (type) {
      case BEGIN:
        seq.getLast().setSequence(MAIN_SEQUENCE, seq);
        break;
      case PROGRESS:
        seq.getLast().setSequence(MAIN_SEQUENCE, seq);
        updatePathLength(seq);
        break;
      case END:
        if (seq.getAttribute(SCRAP) == null/* && seq.size() > 1 */) {
          processFinishedSequence(seq);
        }
        break;
    }
    fireRelaySequenceEvent(seqEvent);
  }

  private void fireRelaySequenceEvent(SequenceEvent seqEvent) {
    for (SequenceListener sl : relaySeqEventListeners) {
      sl.handleSequenceEvent(seqEvent);
    }
  }

  public void reprocessStroke(Stroke modified, boolean processAgain) {
    // first, completely remove traces of this stroke and related derived objects (e.g. primitives)
    Set<Primitive> prims = (Set<Primitive>) modified.getAttribute(PRIMITIVES);
    for (Primitive p : prims) {
      getAngleGraph().remove(p);
      getLengthGraph().remove(p);
      getEndPoints().remove(p.getStartPt());
      getEndPoints().remove(p.getEndPt());
    }
    getTimeGraph().remove(modified);
    for (Pt die : modified) {
      getAllPoints().remove(die);
    }
    // next, if asked, process this stroke like it just happened
    if (processAgain) {
      processFinishedSequence(modified);
    }
  }

  private void processFinishedSequence(Stroke seq) {
    bug("processing... " + (seq.getAttribute(SCRAP) != null ? " (this is a scrap sequence!)" : ""));
    getTimeGraph().add(seq);
    // explicity map points back to their original sequence so we can find it later.
    getAllPoints().addAll(seq);
    // Create a 'primitives' set where dots/ellipses/polyline elements will go.
    seq.setAttribute(PRIMITIVES, new TreeSet<Primitive>(Primitive.sortByIndex));
    // All the 'detectXYZ' methods will insert a Primitive into seq's primitives set.
    detectDot(seq);
    detectVeryShortLines(seq);
    detectEllipse(seq);
    detectPolyline(seq);

    for (Primitive prim : getPrimitiveSet(seq)) {
      recordPrimitive(prim);
    }

    // complain when points are not part of a primitive
    for (Pt pt : seq) {
      TreeSet<Primitive> pointPrims = (TreeSet<Primitive>) pt.getAttribute(PRIMITIVES);
      if (pointPrims == null || pointPrims.size() == 0) {
        bug("Found lonely point: " + pt.getID() + ": " + Debug.num(pt));
        pt.setBoolean("buggy", true);
      }
    }

    firePrimitiveEvent(seq);

    // DEBUGGING stuff below here. comment out if you want.

    // drawPrims(recent, Color.RED, "recent");
    // cohorts.removeAll(recent);
    // drawPrims(cohorts, Color.GREEN, "3");
    // drawParallelPerpendicular(seq);
    // drawAdjacent(seq);
    // drawSimilarLength(seq);
    // if (main.getScript("Scribbler") != null) {
    // debugUtil.drawDots(((Scribbler) main.getScript("Scribbler")).getPossibleCorners(), "7");
    // }
    debugUtil.drawDots(seq, "buggy", "1", Color.BLUE, 2.5);
    debugUtil.drawDots(seq, "corner", "2", Color.RED, 3.5);
    // drawDots(seq, true, true, true, false, false, "4");
    // drawDots(seq, false, false, false, true, false, "5");
    // drawDots(seq, false, false, false, false, true, "6");
    // drawDots(seq, true, false, false, false, false, "7");
  }

  private void recordPrimitive(Primitive prim) {
    getEndPoints().add(prim.getSeq().get(prim.getStartIdx()));
    getEndPoints().add(prim.getSeq().get(prim.getEndIdx()));
    if (prim instanceof LineSegment) {
      getAngleGraph().add((LineSegment) prim);
    }
    getLengthGraph().add(prim);
    addPrimitiveToPoints(prim);
  }

  private void firePrimitiveEvent(Stroke seq) {
    SortedSet<Primitive> prims = getPrimitiveSet(seq);
    PrimitiveEvent pe = new PrimitiveEvent(this, prims);
    for (PrimitiveListener pl : primitiveListeners) {
      pl.handlePrimitiveEvent(pe);
    }
  }

  public static SortedSet<Primitive> getPrimitiveSet(Stroke seq) {
    return (SortedSet<Primitive>) seq.getAttribute(Neanderthal.PRIMITIVES);
  }

  /**
   * For each point in the primitive, add the primitive to the point's primitive list.
   */
  private void addPrimitiveToPoints(Primitive prim) {
    for (int i = prim.getStartIdx(); i <= prim.getEndIdx(); i++) {
      Pt pt = prim.getSeq().get(i);
      if (!pt.hasAttribute(PRIMITIVES)) {
        pt.setAttribute(PRIMITIVES, new TreeSet<Primitive>(Primitive.sortByIndex));
      }
      ((TreeSet<Primitive>) pt.getAttribute(PRIMITIVES)).add(prim);
    }
  }

  public static Set<Primitive> getPrimitives(Set<Pt> points) {
    // each point has a main sequence. that sequence has a list of primitives. a point may be in
    // zero, one, or more of them. Return all primitives related to the input points.
    SortedSet<Primitive> ret = new TreeSet<Primitive>(Primitive.sortByIndex);
    for (Pt pt : points) {
      Stroke seq = (Stroke) pt.getSequence(MAIN_SEQUENCE);
      int where = seq.indexOf(pt);
      for (Primitive prim : (SortedSet<Primitive>) seq.getAttribute(Neanderthal.PRIMITIVES)) {
        if (where <= prim.getEndIdx() && where >= prim.getStartIdx()) {
          ret.add(prim);
        }
      }
    }
    return ret;
  }

  public static Set<Stroke> getSequences(Set<Primitive> prims) {
    Set<Stroke> ret = new HashSet<Stroke>();
    for (Primitive prim : prims) {
      ret.add((Stroke) prim.getSeq());
    }
    return ret;
  }

  private Polyline detectPolyline(Stroke seq) {
    Polyline ret = new Polyline(seq, (Animation) main.getScript("Animation"));
    Set<Segment> segs = ret.getSegments();
    // Establish line and arc certainties for each segment.
    for (Segment seg : segs) {
      double lineLenDivCurviLen = seg.getPathLength() / seg.getLineLength();
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

  private Certainty detectEllipse(Stroke seq) {
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

  private Certainty detectVeryShortLines(Stroke seq) {
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

  private Certainty detectDot(Stroke seq) {

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
    // TODO: why is this not just a part of SkruiScript?
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

  public void addStructurePoint(Pt pt) {
    structurePoints.add(pt);
  }

  public void addStructureLine(LineSegment line) {
    bug("Adding Structure Line: " + line);
    structureLines.add(line);
  }

  public void forget(Shape s, boolean rememberForgottenSequences) {
    for (Primitive p : s.getSubshapes()) {
      // each primitive has siblings that must also be forgotten.
      // for example, a line has a corresponding arc for the same points.
      for (Primitive forgetMe : getPrimitiveSet(p.seq)) {
        forget(forgetMe, rememberForgottenSequences);
      }
    }
  }

  @SuppressWarnings("unused")
  private static String getIDList(Collection<Pt> points) {
    StringBuilder buf = new StringBuilder();
    for (Pt pt : points) {
      buf.append(pt.getID() + " ");
    }
    return "[" + buf.toString().trim() + "]";
  }

  public void forget(Primitive p, boolean rememberForgottenSequence) {
    getAngleGraph().remove(p);
    getLengthGraph().remove(p);
    getEndPoints().remove(p.getStartPt());
    getEndPoints().remove(p.getEndPt());
    forget(p.seq, rememberForgottenSequence); // removes drawing buffer, sets SCRAP, removes points
    // from tg and allPoints.
  }

  public void forget(Stroke seq, boolean rememberForgottenSequence) {
    if (rememberForgottenSequence) {
      forgottenSequences.add(seq);
    }
    // bug("Forgetting sequence " + seq.getId() + " with " + seq.size() + " points. There are now "
    // + forgottenSequences.size() + " forgotten sequences.");
    makeScrap(seq);
    if (seq.getDrawingBuffer() != null) {
      seq.getDrawingBuffer().setVisible(false);
    }
    getMain().removeFinishedSequence(seq);
    getTimeGraph().remove(seq);
    for (Pt die : seq) {
      getAllPoints().remove(die);
    }
  }

  public void addSequenceListener(SequenceListener sel) {
    relaySeqEventListeners.add(sel);
  }

  public void addPrimitiveListener(PrimitiveListener pl) {
    primitiveListeners.add(pl);
  }

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

    return defs;
  }

  private static void bug(String what) {
    Debug.out("Neanderthal", what);
  }

  public void makeScrap(Stroke scrapMe) {
    // bug("Turning sequence " + scrapMe.getId() + " into scrap. Reason: " + reason);
    scrapMe.setAttribute(Neanderthal.SCRAP, "true");
  }

}

// Did they ever figure out who the man in the jar was?