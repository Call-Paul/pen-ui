package org.six11.skrui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.data.Statistics;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.SequenceEvent;
import org.six11.util.pen.SequenceListener;

/**
 * A Plug-in to the Skrui Hacks. This analyzes finished Sequences and identifies possible corners.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class CornerFinder extends SkruiScript implements SequenceListener {

  public static final String K_SPEED_MULTIPLIER = "speed-multiplier";
  public static final String K_CURVE_MEDIAN_MULTIPLIER = "curve-median-multiplier";
  public static final String K_CORNER_SPACE = "corner-space-thresh";
  public static final String K_LINE_RATIO = "line-ratio-thresh";
  public static final String K_SPLINE_THRESH = "spline-thresh";
  public static final String K_LINE_OK = "line";
  public static final String K_ARC_OK = "arc";
  public static final String K_SPLINE_OK = "spline";
  public static final String K_LINE_ERROR_MULT = "line-error-mult";
  public static final String K_ARC_ERROR_MULT = "arc-error-mult";
  public static final String K_SPLINE_ERROR_MULT = "spline-error-mult";

  public static Arguments getArgumentSpec() {
    Arguments args = new Arguments();
    args.setProgramName("Corner Finder");
    args.setDocumentationProgram("Examines finished Sequences and identifies corners.");

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
    defs.put(K_SPEED_MULTIPLIER, new BoundedParameter.Double(K_SPEED_MULTIPLIER,
        "Speed Multiplier",
        "the double value that serves as the threshold for determining what is slow. "
            + "Points with a speed below (stroke-average-speed * speed-thresh-multiplier) are "
            + "considered slow.", 0, 1, 0.75));
    defs.put(K_CURVE_MEDIAN_MULTIPLIER, new BoundedParameter.Double(K_CURVE_MEDIAN_MULTIPLIER,
        "Curve Median Multiplier",
        "how many multiples of the median a point's curvature must be in order to be "
            + "considered curvy. Larger numbers are more restrictive.", 1.5, 5, 2));
    defs.put(K_CORNER_SPACE, new BoundedParameter.Double(K_CORNER_SPACE, "Minimum Corner Space",
        "the minimum Euclidean distance surrounding a corner in which a "
            + "curvilinearly-adjacent corner may not exist.", 1, 60, 15));
    defs.put(K_LINE_RATIO, new BoundedParameter.Double(K_LINE_RATIO, "Line Ratio",
        "the ratio between a segment's ideal length over its curvilinear path "
            + "length, above which it is considered a line. Lower values will yield "
            + "more arcs.", 0, 1, 0.95));
    defs.put(K_LINE_OK, new BoundedParameter.Boolean(K_LINE_OK, "Enable Line Finder",
        "Enables line finding", true));
    defs.put(K_ARC_OK, new BoundedParameter.Boolean(K_ARC_OK, "Enable Arc Finder",
        "Enables arc finding (portions of circles)", true));
    defs.put(K_SPLINE_OK, new BoundedParameter.Boolean(K_SPLINE_OK, "Enable Spline Finder",
        "Enables cardinal spline fitting.", true));
    defs.put(K_SPLINE_THRESH, new BoundedParameter.Double(K_SPLINE_THRESH,
        "Spline Error Threshold", "The error threshold when fitting a spline.", 1, 100, 20));
    defs.put(K_LINE_ERROR_MULT, new BoundedParameter.Double(K_LINE_ERROR_MULT,
        "Line Error Multiplier",
        "A multiplier applied to line error when deciding if a segment is a line, arc, "
            + "or spline. Lines will generally have more error than arcs, which "
            + "certainly have more error than splines. So to promote line finding, "
            + "set the line error multiplier to be substantially lower than circles and splines.",
        1, 100, 1));
    defs.put(K_ARC_ERROR_MULT, new BoundedParameter.Double(K_ARC_ERROR_MULT,
        "Arc Error Multiplier",
        "A multiplier applied to arc error when deciding if a segment is a line, arc, "
            + "or spline. See docs for line error for more info.", 1.5, 100, 1));
    defs.put(K_SPLINE_ERROR_MULT, new BoundedParameter.Double(K_SPLINE_ERROR_MULT,
        "Spline Error Multiplier",
        "A multiplier applied to spline error when deciding if a segment is a line, arc, "
            + "or spline. See docs for line error for more info.", 3, 100, 1));
    return defs;
  }

  Arguments args;

  @SuppressWarnings("unchecked")
  public void handleSequenceEvent(SequenceEvent seqEvent) {
    if (seqEvent.getType() == SequenceEvent.Type.END) {

      mergeCFModified(seqEvent.getSeq());

      if (seqEvent.getSeq().getAttribute("segmentation") != null) {
        SortedSet<Segment> segs = (SortedSet<Segment>) seqEvent.getSeq().getAttribute(
            "segmentation");
        getSoup().addSoupData("segmentation", segs);
      }
    }
  }

  public List<Pt> mergeCFModified(Sequence seq) {
    List<Pt> ret = new ArrayList<Pt>();
    seq.calculateCurvatureEuclideanWindowSize(24.0);
    Statistics stats = new Statistics();
    for (Pt pt : seq) {
      stats.addData(Math.abs(pt.getDouble("curvature")));
    }
    double medianCurve = stats.getMedian();
    double aveSpeed = seq.calculateSpeed() / (double) seq.size();
    double threshSpeed = getParam(K_SPEED_MULTIPLIER).getDouble() * aveSpeed;
    double threshCurve = getParam(K_CURVE_MEDIAN_MULTIPLIER).getDouble() * medianCurve;

    SortedSet<Pt> candidates = new TreeSet<Pt>();
    candidates.add(seq.getFirst());
    candidates.add(seq.getLast());

    for (Pt pt : seq) {
      if (Math.abs(pt.getDouble("curvature")) > threshCurve) {
        pt.setBoolean("curvy", true);
      }
      if (pt.getDouble("speed") < threshSpeed) {
        pt.setBoolean("slow", true);
      }
      if ((Math.abs(pt.getDouble("curvature")) > threshCurve)
          && (pt.getDouble("speed") < threshSpeed)) {
        candidates.add(pt);
      }
    }

    // remove points that are too close
    candidates = removeDupes(candidates, seq);

    // set curvilinear-distance for all points (including candidates). This value is reused
    // throughout. Segment length from corners i, j is curvilinear distance of j minus that of i.
    seq.calculateCurvilinearDistances();

    // perform the CFMerge
    merge(candidates, seq, 1);

    // Explain to the world which points are the corners.
    for (Pt pt : candidates) {
      pt.setBoolean("corner", true);
    }

    ret.addAll(candidates);
    return ret;
  }

  private SortedSet<Pt> removeDupes(Collection<Pt> in, Sequence origin) {
    List<Pt> working = new ArrayList<Pt>();
    working.addAll(in);
    for (int i = 0; i < (working.size() - 1);) {
      Pt a = working.get(i);
      Pt b = working.get(i + 1);
      int idxA = origin.indexOf(a);
      int idxB = origin.indexOf(b);
      double dist = origin.getPathLength(idxA, idxB);
      if (dist < getParam(K_CORNER_SPACE).getDouble()) {
        if (i == 0) {
          working.remove(i + 1);
        } else if (i == (working.size() - 1)) {
          working.remove(i);
        } else {
          if (Math.abs(a.getDouble("curvature")) > Math.abs(b.getDouble("curvature"))) {
            working.remove(i + 1);
          } else {
            working.remove(i);
          }
        }
      } else {
        i++;
      }
    }
    return new TreeSet<Pt>(working);
  }

  private boolean merge(SortedSet<Pt> candidates, Sequence seq, final int iterationNumber) {
    boolean ret = true; // true means do it again. change to false when threshold is long enough
    SortedSet<Segment> segs = new TreeSet<Segment>();
    SortedSet<Segment> inTimeOrder = new TreeSet<Segment>(Segment.orderByPoints);
    boolean doLine = getParam(K_LINE_OK).getBoolean();
    boolean doArc = getParam(K_ARC_OK).getBoolean();
    boolean doSpline = getParam(K_SPLINE_OK).getBoolean();
    double splineThresh = getParam(K_SPLINE_THRESH).getDouble();
    double lineMult = getParam(K_LINE_ERROR_MULT).getDouble();
    double arcMult = getParam(K_ARC_ERROR_MULT).getDouble();
    double splineMult = getParam(K_SPLINE_ERROR_MULT).getDouble();
    Pt prev = null;
    int segCounter = 1;
    for (Pt pt : candidates) {
      if (prev != null) {
        Segment s = new Segment(prev, pt, seq, doLine, doArc, doSpline, splineThresh, lineMult,
            arcMult, splineMult);
        s.label = "" + segCounter;
        segs.add(s);
        segCounter++;
      }
      prev = pt;
    }

    // threshold is average segment length multiplied by the iteration number. We will consider
    // segments that are shorter than this threshold. This means it is easier for the threshold to
    // be surpassed with each iteration.
    double threshold = (seq.length() / (double) segs.size()) * (double) iterationNumber;
    if (threshold > segs.last().length()) {
      ret = false;
    } else {
      for (Segment thisSeg : segs) {
        if (thisSeg.length() < threshold) {
          Segment prevSeg = findPreviousSegment(thisSeg, segs);
          Segment nextSeg = findNextSegment(thisSeg, segs);
          Segment leftSeg = prevSeg == null ? null : new Segment(prevSeg.start, thisSeg.end, seq,
              doLine, doArc, doSpline, splineThresh, lineMult, arcMult, splineMult);
          Segment rightSeg = nextSeg == null ? null : new Segment(thisSeg.start, nextSeg.end, seq,
              doLine, doArc, doSpline, splineThresh, lineMult, arcMult, splineMult);
          double errorThis = thisSeg.getBestError();
          double errorPrev = prevSeg == null ? 0.0 : prevSeg.getBestError();
          double errorNext = nextSeg == null ? 0.0 : nextSeg.getBestError();
          double errorLeft = prevSeg == null ? Double.POSITIVE_INFINITY : leftSeg.getBestError();
          double errorRight = nextSeg == null ? Double.POSITIVE_INFINITY : rightSeg.getBestError();
          StringBuilder bugInfo = new StringBuilder();
          bugInfo.append("  errorThis: " + Debug.num(errorThis) + "\n");
          bugInfo.append("  errorPrev: " + Debug.num(errorPrev) + "\n");
          bugInfo.append("  errorNext: " + Debug.num(errorNext) + "\n");
          bugInfo.append("  errorLeft: " + Debug.num(errorLeft) + "\n");
          bugInfo.append("  errorRight: " + Debug.num(errorRight) + "\n");
          if (errorLeft < errorRight && errorLeft < (1.5 * errorPrev) + errorThis) {
            candidates.remove(thisSeg.start);
            thisSeg.start.setBoolean("removed", true);
            ret = merge(candidates, seq, iterationNumber);
            break;
          } else if (errorRight < errorLeft && errorRight < (1.5 * errorNext) + errorThis) {
            candidates.remove(thisSeg.end);
            thisSeg.end.setBoolean("removed", true);
            ret = merge(candidates, seq, iterationNumber);
            break;
          }
        }
      }
    }
    if (ret) {
      merge(candidates, seq, iterationNumber + 1);
    } else {
      inTimeOrder.addAll(segs);
      seq.setAttribute("segmentation", inTimeOrder);
    }
    return ret;
  }

  private Segment findPreviousSegment(Segment seg, SortedSet<Segment> segs) {
    Segment ret = null;
    for (Segment s : segs) {
      if (s.end == seg.start) {
        ret = s;
        break;
      }
    }
    return ret;
  }

  private Segment findNextSegment(Segment seg, SortedSet<Segment> segs) {
    Segment ret = null;
    for (Segment s : segs) {
      if (s.start == seg.end) {
        ret = s;
        break;
      }
    }
    return ret;
  }

  private static void bug(String what) {
    Debug.out("CornerFinder", what);
  }

  public void initialize() {
    args = getArgumentSpec();
    args.parseArguments(main.getArguments());
    args.validate();
    getSoup().addSequenceListener(this);
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
}
