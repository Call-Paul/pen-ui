package org.six11.skruifab.analysis;

import java.util.*;

import org.six11.util.Debug;
import org.six11.util.data.Statistics;
import org.six11.util.pen.Pt;

/**
 * This basically MergeCF with my own modifications. And by 'modifications' I mean the things I did
 * because I am lazy.
 * 
 * Original paper here: Wolin, A., Paulson, B., and Hammond, T. Sort, merge, repeat: An algorithm
 * for effectively finding corners in hand-sketched strokes. In Proceedings of Eurographics 6th
 * Annual Workshop on Sketch-Based Interfaces and Modeling (2009).
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class MergeCF {

  public final static String CORNERS_FOUND = "corners found";
  public final static String CORNER = "corner";
  public final static String CURVATURE = "curvature";
  public final static String CURVY = "curvy";
  public final static String SLOW = "slow";
  public final static String SPEED = "speed";
  
  public final static double DUPLICATE_THRESHOLD = 15;

  public static Set<Segment> analyze(Stroke seq) {
    Set<Segment> segments = new HashSet<Segment>();
    List<Integer> corners = getCorners(seq);
    for (int i = 0; i < corners.size() - 1; i++) {
      Segment s = new Segment(corners.get(i), corners.get(i + 1), seq);
      segments.add(s);
    }
    seq.setAttribute(CORNERS_FOUND, true);
    return segments;
  }

  private static List<Integer> getCorners(Stroke seq) {

    List<Integer> ret = new ArrayList<Integer>();
    if (seq.size() > 1) {
      seq.calculateCurvatureEuclideanWindowSize(24.0);
      Statistics stats = new Statistics();
      for (Pt pt : seq) {
        stats.addData(Math.abs(pt.getDouble(CURVATURE)));
      }
      double medianCurve = stats.getMedian();
      double aveSpeed = seq.calculateSpeed() / (double) seq.size();
      double threshSpeed = 0.75 * aveSpeed;
      double threshCurve = 2 * medianCurve;

      SortedSet<Integer> candidates = new TreeSet<Integer>();
      candidates.add(0);
      candidates.add(seq.size() - 1);

      for (int idx = 0; idx < seq.size(); idx++) {
        Pt pt = seq.get(idx);
        if (Math.abs(pt.getDouble(CURVATURE)) > threshCurve) {
          pt.setBoolean(CURVY, true);
        }
        if (pt.getDouble(SPEED) < threshSpeed) {
          pt.setBoolean(SLOW, true);
        }
        if ((Math.abs(pt.getDouble(CURVATURE)) > threshCurve)
            && (pt.getDouble(SPEED) < threshSpeed)) {
          pt.setBoolean("both", true);
          candidates.add(idx);
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
      for (int idx : candidates) {
        seq.get(idx).setBoolean(CORNER, true);
      }

      ret.addAll(candidates);
    }
    return ret;
  }

  private static SortedSet<Integer> removeDupes(Collection<Integer> in, Stroke origin) {
    List<Integer> working = new ArrayList<Integer>();
    working.addAll(in);
    for (int i = 0; i < (working.size() - 1);) {
      int idxA = working.get(i);
      int idxB = working.get(i + 1);
      double dist = origin.getPathLength(idxA, idxB);
      if (dist < DUPLICATE_THRESHOLD) {
        if (i == 0) {
          working.remove(i + 1);
        } else if (i == (working.size() - 1)) {
          working.remove(i);
        } else {
          if (Math.abs(origin.get(idxA).getDouble(CURVATURE)) > Math.abs(origin.get(idxB)
              .getDouble(CURVATURE))) {
            working.remove(i + 1);
          } else {
            working.remove(i);
          }
        }
      } else {
        i++;
      }
    }
    return new TreeSet<Integer>(working);
  }

  private static boolean merge(SortedSet<Integer> candidates, Stroke seq, final int iterationNumber) {
    boolean ret = true; // true means do it again. change to false when threshold is long enough
    SortedSet<Segment> segs = new TreeSet<Segment>();
    SortedSet<Segment> inTimeOrder = new TreeSet<Segment>(Segment.orderByPoints);
    int prev = -1;
    int segCounter = 1;
    for (int i : candidates) {
      if (prev >= 0) {
        Segment s = new Segment(prev, i, seq);
        segs.add(s);
        segCounter++;
      }
      prev = i;
    }

    // threshold is average segment length multiplied by the iteration number. We will consider
    // segments that are shorter than this threshold. This means it is easier for the threshold to
    // be surpassed with each iteration.
    double threshold = (seq.length() / (double) segs.size()) * (double) iterationNumber;
    if (segs.size() == 0 || threshold > segs.last().getLength()) {
      ret = false;
    } else {
      for (Segment thisSeg : segs) {
        if (thisSeg.getLength() < threshold) {
          Segment prevSeg = findPreviousSegment(thisSeg, segs);
          Segment nextSeg = findNextSegment(thisSeg, segs);
          Segment leftSeg = prevSeg == null ? null : new Segment(prevSeg.start, thisSeg.end, seq);
          Segment rightSeg = nextSeg == null ? null : new Segment(thisSeg.start, nextSeg.end, seq);
          double errorThis = thisSeg.getError();
          double errorPrev = prevSeg == null ? 0.0 : prevSeg.getError();
          double errorNext = nextSeg == null ? 0.0 : nextSeg.getError();
          double errorLeft = prevSeg == null ? Double.POSITIVE_INFINITY : leftSeg.getError();
          double errorRight = nextSeg == null ? Double.POSITIVE_INFINITY : rightSeg.getError();
          if (errorLeft < errorRight
              && (leftSeg.isProbablyLine() || errorLeft < (1.5 * errorPrev) + errorThis)) {
            candidates.remove(thisSeg.start);
            ret = merge(candidates, seq, iterationNumber);
            break;
          } else if (errorRight < errorLeft
              && (rightSeg.isProbablyLine() || errorRight < (1.5 * errorNext) + errorThis)) {
            candidates.remove(thisSeg.end);
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

  @SuppressWarnings("unused")
  private static void bug(String what) {
    Debug.out("Polyline", what);
  }

  private static Segment findPreviousSegment(Segment seg, SortedSet<Segment> segs) {
    Segment ret = null;
    for (Segment s : segs) {
      if (s.end == seg.start) {
        ret = s;
        break;
      }
    }
    return ret;
  }

  private static Segment findNextSegment(Segment seg, SortedSet<Segment> segs) {
    Segment ret = null;
    for (Segment s : segs) {
      if (s.start == seg.end) {
        ret = s;
        break;
      }
    }
    return ret;
  }
}
