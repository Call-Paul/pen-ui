package org.six11.skrui;

import java.awt.Color;
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
import org.six11.util.gui.Colors;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.SequenceEvent;
import org.six11.util.pen.SequenceListener;

/**
 * A Plug-in to the Skrui Hacks. This analyzes finished Sequences and identifies possible corners.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class CornerFinder extends DrawingScript implements SequenceListener {

  public static final String K_SPEED_MULTIPLIER = "speed-multiplier";
  public static final String K_CURVE_MEDIAN_MULTIPLIER = "curve-median-multiplier";
  public static final String K_CORNER_SPACE = "corner-space-thresh";
  public static final String K_LINE_RATIO = "line-ratio-thresh";

  public static Arguments getArgumentSpec() {
    Arguments args = new Arguments();
    args.setProgramName("Corner Finder");
    args.setDocumentationProgram("Examines finished Sequences and identifies corners.");

    Map<String, BoundedParameter> defs = getDefaultParameters();
    BoundedParameter p = null;
    p = defs.get(K_SPEED_MULTIPLIER);
    args.addFlag(p.getKeyName(), ArgType.ARG_OPTIONAL, ValueType.VALUE_REQUIRED, p
        .getDocumentation()
        + " Defaults to " + Debug.num(p.getValue()) + ". ");
    p = defs.get(K_CURVE_MEDIAN_MULTIPLIER);
    args.addFlag(p.getKeyName(), ArgType.ARG_OPTIONAL, ValueType.VALUE_REQUIRED, p
        .getDocumentation()
        + " Defaults to " + Debug.num(p.getValue()) + ". ");
    p = defs.get(K_CORNER_SPACE);
    args.addFlag(p.getKeyName(), ArgType.ARG_OPTIONAL, ValueType.VALUE_REQUIRED, p
        .getDocumentation()
        + " Defaults to " + Debug.num(p.getValue()) + ". ");
    p = defs.get(K_LINE_RATIO);
    args.addFlag(p.getKeyName(), ArgType.ARG_OPTIONAL, ValueType.VALUE_REQUIRED, p
        .getDocumentation()
        + " Defaults to " + Debug.num(p.getValue()) + ". ");
    return args;
  }

  public static Map<String, BoundedParameter> getDefaultParameters() {
    Map<String, BoundedParameter> defs = new HashMap<String, BoundedParameter>();
    defs.put(K_SPEED_MULTIPLIER, new BoundedParameter(K_SPEED_MULTIPLIER, "Speed Multiplier",
        "the double value that serves as the threshold for determining what is slow. "
            + "Points with a speed below (stroke-average-speed * speed-thresh-multiplier) are "
            + "considered slow.", 0, 1, 0.75));
    defs.put(K_CURVE_MEDIAN_MULTIPLIER, new BoundedParameter(K_CURVE_MEDIAN_MULTIPLIER,
        "Curve Median Multiplier",
        "how many multiples of the median a point's curvature must be in order to be "
            + "considered curvy. Larger numbers are more restrictive.", 1.5, 5, 2));
    defs.put(K_CORNER_SPACE, new BoundedParameter(K_CORNER_SPACE, "Minimum Corner Space",
        "the minimum Euclidean distance surrounding a corner in which a "
            + "curvilinearly-adjacent corner may not exist.", 1, 60, 15));
    defs.put(K_LINE_RATIO, new BoundedParameter(K_LINE_RATIO, "Line Ratio",
        "the ratio between a segment's ideal length over its curvilinear path "
            + "length, above which it is considered a line. Lower values will yield "
            + "more arcs.", 0, 1, 0.95));
    return defs;
  }

  Arguments args;
  // static int windowSize = 2;

  // static double threshSpeedMultiplier = 0.75;
  // static double threshCurveMedianMultiplier = 2.0;
  // static double minCornerSpaceThresh = 15.0;
  // static double lineRatioThresh = 0.95;
  private Map<String, BoundedParameter> params;

  @SuppressWarnings("unchecked")
  public void handleSequenceEvent(SequenceEvent seqEvent) {
    if (seqEvent.getType() == SequenceEvent.Type.END) {

      List<Pt> corners = mergeCFModified(seqEvent.getSeq());

      if (seqEvent.getSeq().getAttribute("segmentation") != null) {
        SortedSet<Segment> segs = (SortedSet<Segment>) seqEvent.getSeq().getAttribute(
            "segmentation");
        DrawingBuffer db = new DrawingBuffer();
        for (Segment seg : segs) {
          DrawingBufferRoutines.seg(db, seg, Color.BLACK, params.get(K_LINE_RATIO).getValue());
        }
        main.getDrawingSurface().getSoup().addBuffer(db);
      }

      drawCorners(corners, seqEvent);
      disableOriginalInput(seqEvent);
    }
  }

  private void disableOriginalInput(SequenceEvent seqEvent) {
    main.getDrawingSurface().getSoup().getDrawingBufferForSequence(seqEvent.getSeq()).setVisible(
        false);
  }

  private void drawCorners(List<Pt> corners, SequenceEvent seqEvent) {
    if (corners != null && corners.size() > 0) {
      DrawingBuffer db = new DrawingBuffer();
      Color cornerColor = new Color(255, 0, 0, 127);
      Color mergedColor = new Color(0, 0, 255, 127);
      Color normalColor = new Color(255, 255, 255, 127);
      Color curvyColor = Colors.makeAlpha(Color.GREEN, 0.6f);
      Color slowColor = Colors.makeAlpha(Color.YELLOW, 0.6f);
      Color slowAndCurvyColor = Colors.makeAlpha(Color.MAGENTA, 0.6f);
      boolean addedSomething = false;
      for (Pt pt : seqEvent.getSeq()) {
        boolean specialPoint = false;
        Color c = cornerColor;
        if (pt.getBoolean("corner")) {
          specialPoint = true;
        } else if (pt.getBoolean("removed")) {
          c = mergedColor;
          specialPoint = true;
        } else if (pt.getBoolean("slow") && pt.getBoolean("curvy")) {
          c = slowAndCurvyColor;
        } else if (pt.getBoolean("curvy")) {
          c = curvyColor;
        } else if (pt.getBoolean("slow")) {
          c = slowColor;
        } else {
          c = normalColor;
        }

        if (specialPoint) {
          DrawingBufferRoutines.dot(db, pt, 4.0, 0.4, Color.BLACK, c);
          addedSomething = true;
        } else {
          // DrawingBufferRoutines.rect(db, pt, 3.0, 3.0, Color.BLACK, c, 0.3);
        }
      }
      if (addedSomething) {
        main.getDrawingSurface().getSoup().addBuffer(db);
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
    double threshSpeed = params.get(K_SPEED_MULTIPLIER).getValue() * aveSpeed;
    double threshCurve = params.get(K_CURVE_MEDIAN_MULTIPLIER).getValue() * medianCurve;

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
      if (dist < params.get(K_CORNER_SPACE).getValue()) {
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
    Pt prev = null;
    int segCounter = 1;
    for (Pt pt : candidates) {
      if (prev != null) {
        Segment s = new Segment(prev, pt, seq);
        s.label = "" + segCounter;
        segs.add(s);
        segCounter++;
      }
      prev = pt;
    }

    // threshold is average segment length multiplied by the iteration number. We will consider
    // segments that are shorter than this threshold. This means it is
    // easier for the threshold to be surpassed with each iteration.
    double threshold = (seq.length() / (double) segs.size()) * (double) iterationNumber;
    if (threshold > segs.last().length()) {
      ret = false;
    } else {
      for (Segment thisSeg : segs) {
        if (thisSeg.length() < threshold) {
          Segment prevSeg = findPreviousSegment(thisSeg, segs);
          Segment nextSeg = findNextSegment(thisSeg, segs);
          Segment leftSeg = prevSeg == null ? null : new Segment(prevSeg.start, thisSeg.end, seq);
          Segment rightSeg = nextSeg == null ? null : new Segment(thisSeg.start, nextSeg.end, seq);
          double errorThis = thisSeg.getBestError(params.get(K_LINE_RATIO).getValue());
          double errorPrev = prevSeg == null ? 0.0 : prevSeg.getBestError(params.get(K_LINE_RATIO)
              .getValue());
          double errorNext = nextSeg == null ? 0.0 : nextSeg.getBestError(params.get(K_LINE_RATIO)
              .getValue());
          double errorLeft = prevSeg == null ? Double.POSITIVE_INFINITY : leftSeg
              .getBestError(params.get(K_LINE_RATIO).getValue());
          double errorRight = nextSeg == null ? Double.POSITIVE_INFINITY : rightSeg
              .getBestError(params.get(K_LINE_RATIO).getValue());
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
    params = copyParameters(getDefaultParameters());
    for (String k : params.keySet()) {
      if (args.hasValue(k)) {
        params.get(k).setValue(Double.parseDouble(args.getValue(k)));
        bug("Set " + params.get(k).getHumanReadableName() + " to "
            + Debug.num(params.get(K_SPEED_MULTIPLIER).getValue()));
      }
    }
    main.getDrawingSurface().getSoup().addSequenceListener(this);
  }

  @Override
  public Map<String, BoundedParameter> getParameters() {
    return null;
  }
}
