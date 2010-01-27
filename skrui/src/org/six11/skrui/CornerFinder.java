package org.six11.skrui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.data.Statistics;
import org.six11.util.gui.Colors;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.SequenceEvent;
import org.six11.util.pen.SequenceListener;
import org.six11.util.pen.Vec;

/**
 * A Plug-in to the Skrui Hacks. This analyzes finished Sequences and identifies possible corners.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class CornerFinder implements SequenceListener {

  public static Arguments getArgumentSpec() {
    Arguments args = new Arguments();
    args.setProgramName("Corner Finder");
    args.setDocumentationProgram("Examines finished Sequences and identifies corners.");

    args.addFlag("corner-finder", ArgType.ARG_OPTIONAL, ValueType.VALUE_OPTIONAL,
        "Loads a corner finder. Possible values are 'mergeCFModified' (default), and "
            + "'your-mom' (not actually used).");
    args.addFlag("curve-windowsize", ArgType.ARG_OPTIONAL, ValueType.VALUE_REQUIRED,
        "Specify the window size for calculating curvature. Default is 2.");
    args.addFlag("curve-thresh-multiplier", ArgType.ARG_OPTIONAL, ValueType.VALUE_REQUIRED,
        "Specify the double value that serves as the threshold for determining "
            + "what is curvy. Defaults to 1.0. Points with a curvature above "
            + "(stroke-average-curviness * curve-thresh-multiplier) are considered curvy.");
    args.addFlag("speed-thresh-multiplier", ArgType.ARG_OPTIONAL, ValueType.VALUE_REQUIRED,
        "Specify the double value that serves as the threshold for determining "
            + "what is slow. Defaults to 0.75. Points with a speed below "
            + "(stroke-average-speed * speed-thresh-multiplier) are considered slow.");

    return args;
  }

  private Main main;
  private Arguments args;
  static int windowSize = 2;
  static double threshCurveMultiplier = 1.00;
  static double threshSpeedMultiplier = 0.75;
  static double threshCurveMedianMultiplier = 2.0;
  static double minCornerSpaceThresh = 15.0;
  static double lineRatioThresh = 0.95;
  static double splineControlPointDistThresh = 16.0;

  public CornerFinder(Main m, String[] in) {
    main = m;
    args = getArgumentSpec();
    args.parseArguments(in);
    args.validate();
    if (args.hasValue("curve-windowsize")) {
      windowSize = Integer.parseInt(args.getValue("curve-windowsize"));
      bug("Using custom window size: " + windowSize);
    }
    if (args.hasValue("curve-thresh-multiplier")) {
      threshCurveMultiplier = Double.parseDouble(args.getValue("curve-thresh-multiplier"));
      bug("Using custom curve multiplier: " + threshCurveMultiplier);
    }
    if (args.hasValue("speed-thresh-multiplier")) {
      threshSpeedMultiplier = Double.parseDouble(args.getValue("speed-thresh-multiplier"));
      bug("Using custom speed multiplier: " + threshSpeedMultiplier);
    }

    main.getDrawingSurface().getSoup().addSequenceListener(this);
  }

  @SuppressWarnings("unchecked")
  public void handleSequenceEvent(SequenceEvent seqEvent) {
    if (seqEvent.getType() == SequenceEvent.Type.END) {

      List<Pt> corners = null;

      String finder = "mergeCFModified";
      if (args.hasValue("corner-finder")) {
        finder = args.getValue("corner-finder");
      }

      if (finder.equals("mergeCFModified")) {
        corners = mergeCFModified(seqEvent.getSeq());
      }
      
//      if (seqEvent.getSeq().getAttribute("segmentation") != null) {
//        SortedSet<Segment> segs = (SortedSet<Segment>) seqEvent.getSeq().getAttribute(
//            "segmentation");
//        DrawingBuffer db = new DrawingBuffer();
//        for (Segment seg : segs) {
//          DrawingBufferRoutines.seg(db, seg, Color.BLACK);
//        }
//        main.getDrawingSurface().getSoup().addBuffer(db);
//      }

      if (corners != null && corners.size() > 0) {
        DrawingBuffer db = new DrawingBuffer();
        Color cornerColor = new Color(255, 0, 0, 127);
        Color mergedColor = new Color(0, 0, 255, 127);
        Color normalColor = new Color(255, 255, 255, 127);
        Color curvyColor = Colors.makeAlpha(Color.GREEN, 0.6f);
        Color slowColor = Colors.makeAlpha(Color.YELLOW, 0.6f);
        Color slowAndCurvyColor = Colors.makeAlpha(Color.MAGENTA, 0.6f);
        
        for (Pt pt : seqEvent.getSeq()) {
          boolean specialPoint = false;
          Color c = cornerColor;
          Color b = Color.BLACK;
          if (pt.getBoolean("corner")) {
            specialPoint = true;
          } else if (pt.getBoolean("removed")) {
            c = mergedColor;
            specialPoint = true;
          } else if (pt.getBoolean("slow") && pt.getBoolean("curvy")) {
            c = slowAndCurvyColor;
            b = c;
          } else if (pt.getBoolean("curvy")) {
            c = curvyColor;
            b = c;
          } else if (pt.getBoolean("slow")) {
            c = slowColor;
            b = c;
          } else {
            c = normalColor;
          }

          if (specialPoint) {
            DrawingBufferRoutines.rect(db, pt, 9.0, 9.0, b, c, 1.0);
          } else {
            DrawingBufferRoutines.rect(db, pt, 3.0, 3.0, Color.BLACK, c, 0.3);
          }
        }
        main.getDrawingSurface().getSoup().getDrawingBufferForSequence(seqEvent.getSeq())
            .setVisible(false);
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
    double threshSpeed = threshSpeedMultiplier * aveSpeed;
    double threshCurve = threshCurveMedianMultiplier * medianCurve;

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

    merge(candidates, seq, 1);

    { // debug
      for (Pt pt : candidates) {
        pt.setBoolean("corner", true);
      }
    }

    ret.addAll(candidates);
    return ret;
  }

  private SortedSet<Pt> removeDupes(Collection<Pt> in, Sequence origin) {
    List<Pt> working = new ArrayList<Pt>();
    working.addAll(in);
    int initialCount = working.size();
    for (int i = 0; i < (working.size() - 1);) {
      Pt a = working.get(i);
      Pt b = working.get(i + 1);
      int idxA = origin.indexOf(a);
      int idxB = origin.indexOf(b);
      double dist = origin.getPathLength(idxA, idxB);
      if (dist < minCornerSpaceThresh) {
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
    bug("Removed " + (initialCount - working.size()) + " duplicates.");
    return new TreeSet<Pt>(working);
  }

  private boolean merge(SortedSet<Pt> candidates, Sequence seq, final int iterationNumber) {
    bug("merging " + candidates.size() + " candidate points with iteration number: "
        + iterationNumber);
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
        bug(s.toString());
        segCounter++;
      }
      prev = pt;
    }
    
    // threshold is average segment length multiplied by the iteration number. We will consider
    // segments that are shorter than this threshold. This means it is
    // easier for the threshold to be surpassed with each iteration.
    double threshold = (seq.length() / (double) segs.size()) * (double) iterationNumber;
    if (threshold > segs.last().length()) {
      bug("Threshold surpassed in iteration: " + iterationNumber);
      ret = false;
    } else {
      for (Segment thisSeg : segs) {
        bug("Attempting to merge " + thisSeg);
        if (thisSeg.length() < threshold) {
          bug("  It is short enough...");
          Segment prevSeg = findPreviousSegment(thisSeg, segs);
          Segment nextSeg = findNextSegment(thisSeg, segs);
          Segment leftSeg = prevSeg == null ? null : new Segment(prevSeg.start, thisSeg.end, seq);
          Segment rightSeg = nextSeg == null ? null : new Segment(thisSeg.start, nextSeg.end, seq);
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
            bug("  Removing 'left' corner of: " + thisSeg);
            bug(bugInfo.toString());
            thisSeg.start.setBoolean("removed", true);
            ret = merge(candidates, seq, iterationNumber);
            break;
          } else if (errorRight < errorLeft && errorRight < (1.5 * errorNext) + errorThis) {
            candidates.remove(thisSeg.end);
            bug("  Removing 'right' corner of: " + thisSeg);
            bug(bugInfo.toString());
            thisSeg.end.setBoolean("removed", true);
            ret = merge(candidates, seq, iterationNumber);
            break;
          } else {
            bug("  Not removing corners.");
          }
        } else {
          bug("  " + thisSeg + " is long enough for iteration " + iterationNumber + " (threshold: "
              + Debug.num(threshold) + ")");
        }
      }
    }
    if (ret) {
      merge(candidates, seq, iterationNumber+1);
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
}
