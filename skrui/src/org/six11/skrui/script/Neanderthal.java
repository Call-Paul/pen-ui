package org.six11.skrui.script;

import java.util.*;

import org.six11.skrui.BoundedParameter;
import org.six11.skrui.Scribbler;
import org.six11.skrui.SkruiScript;
import org.six11.skrui.data.AngleGraph;
import org.six11.skrui.data.LengthGraph;
import org.six11.skrui.data.PointGraph;
import org.six11.skrui.data.TimeGraph;
import org.six11.skrui.domain.Shape;
import org.six11.skrui.mesh.Mesh;
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

  // ---------------------------------------------------------------------- Member declarations.
  //

  // Accounting information for all ink related to drawing.
  TimeGraph tg;
  AngleGraph ag;
  PointGraph allPoints;
  PointGraph endPoints;
  LengthGraph lenG;
  List<LineSegment> structureLines;
  List<Mesh> regions;
  PointGraph structurePoints;

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
    tg = new TimeGraph();
    ag = new AngleGraph();
    lenG = new LengthGraph();
    regions = new ArrayList<Mesh>();
    main.getDrawingSurface().getSoup().addSequenceListener(this);
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

  public void addRegion(Mesh mesh) {
    regions.add(mesh);
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

  public void handleSequenceEvent(SequenceEvent seqEvent) {

    SequenceEvent.Type type = seqEvent.getType();
    Sequence seq = seqEvent.getSeq();
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

    firePrimitiveEvent(seq);

    // DEBUGGING stuff below here. comment out if you want.

    // drawPrims(recent, Color.RED, "recent");
    // cohorts.removeAll(recent);
    // drawPrims(cohorts, Color.GREEN, "3");
    // drawParallelPerpendicular(seq);
    // drawAdjacent(seq);
    // drawSimilarLength(seq);
    if (main.getScript("Scribbler") != null) {
      debugUtil.drawDots(((Scribbler) main.getScript("Scribbler")).getPossibleCorners(), "7");
    }
    // drawDots(seq, true, true, true, false, false, "4");
    // drawDots(seq, false, false, false, true, false, "5");
    // drawDots(seq, false, false, false, false, true, "6");
    // drawDots(seq, true, false, false, false, false, "7");
  }

  private void firePrimitiveEvent(Sequence seq) {
    SortedSet<Primitive> prims = getPrimitiveSet(seq);
    PrimitiveEvent pe = new PrimitiveEvent(this, prims);
    for (PrimitiveListener pl : primitiveListeners) {
      pl.handlePrimitiveEvent(pe);
    }
  }

  @SuppressWarnings("unchecked")
  public static SortedSet<Primitive> getPrimitiveSet(Sequence seq) {
    return (SortedSet<Primitive>) seq.getAttribute(Neanderthal.PRIMITIVES);
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

}

// Did they ever figure out who the man in the jar was?