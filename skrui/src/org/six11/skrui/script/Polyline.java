package org.six11.skrui.script;

import java.awt.Color;
import java.util.*;

import org.six11.skrui.DrawingBufferRoutines;
import org.six11.util.Debug;
import org.six11.util.data.Statistics;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

/**
 * This basically MergeCF with my own modifications.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Polyline {

  Sequence seq;
  Set<Segment> segments;
  Animation ani;

  public final static double DUPLICATE_THRESHOLD = 15;

  public Polyline(Sequence seq, Animation ani) {
    this.seq = seq;
    this.ani = ani;
    segments = new HashSet<Segment>();
    List<Integer> corners = getCorners();
    for (int i = 0; i < corners.size() - 1; i++) {
      Segment s = new Segment(corners.get(i), corners.get(i + 1), seq);
      segments.add(s);
    }

  }

  public Set<Segment> getSegments() {
    return segments;
  }

  public List<Integer> getCorners() {

    List<Integer> ret = new ArrayList<Integer>();
    if (seq.size() > 1) {
      seq.calculateCurvatureEuclideanWindowSize(24.0);
      Statistics stats = new Statistics();
      for (Pt pt : seq) {
        stats.addData(Math.abs(pt.getDouble("curvature")));
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
        if (Math.abs(pt.getDouble("curvature")) > threshCurve) {
          pt.setBoolean("curvy", true);
        }
        if (pt.getDouble("speed") < threshSpeed) {
          pt.setBoolean("slow", true);
        }
        if ((Math.abs(pt.getDouble("curvature")) > threshCurve)
            && (pt.getDouble("speed") < threshSpeed)) {
          pt.setBoolean("both", true);
          candidates.add(idx);
        }
      }
      animate("Initial points.", candidates);

      // remove points that are too close
      candidates = removeDupes(candidates, seq);

      animate("Removed duplicates.", candidates);

      // set curvilinear-distance for all points (including candidates). This value is reused
      // throughout. Segment length from corners i, j is curvilinear distance of j minus that of i.
      seq.calculateCurvilinearDistances();

      // perform the CFMerge
      merge(candidates, seq, 1);

      // Explain to the world which points are the corners.
      for (int idx : candidates) {
        seq.get(idx).setBoolean("corner", true);
      }

      ret.addAll(candidates);
    }
    return ret;
  }

  private SortedSet<Integer> removeDupes(Collection<Integer> in, Sequence origin) {
    List<Integer> working = new ArrayList<Integer>();
    working.addAll(in);
    for (int i = 0; i < (working.size() - 1);) {
      int idxA = working.get(i);
      int idxB = working.get(i + 1);
      double dist = origin.getPathLength(idxA, idxB);
      if (dist < DUPLICATE_THRESHOLD) { // TODO: this should be a parameter
        if (i == 0) {
          working.remove(i + 1);
        } else if (i == (working.size() - 1)) {
          working.remove(i);
        } else {
          if (Math.abs(origin.get(idxA).getDouble("curvature")) > Math.abs(origin.get(idxB)
              .getDouble("curvature"))) {
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

  private boolean merge(SortedSet<Integer> candidates, Sequence seq, final int iterationNumber) {
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
          double mergeLeftErrorChange = errorLeft / ((1.5 * errorPrev) + errorThis);
          double mergeRightErrorChange = errorRight / ((1.5 * errorNext) + errorThis);
          animate("Merge iteration " + iterationNumber + ". % left: "
              + Debug.num(mergeLeftErrorChange) + ", % right: " + Debug.num(mergeRightErrorChange),
              candidates, thisSeg);
          if (errorLeft < errorRight
              && (leftSeg.isProbablyLine() || errorLeft < (1.5 * errorPrev) + errorThis)) {
            candidates.remove(thisSeg.start);
            animate("Merged left. Probably line? " + leftSeg.isProbablyLine()
                + ". Percent error change: " + Debug.num(mergeLeftErrorChange), candidates);
            ret = merge(candidates, seq, iterationNumber);
            break;
          } else if (errorRight < errorLeft
              && (rightSeg.isProbablyLine() || errorRight < (1.5 * errorNext) + errorThis)) {
            candidates.remove(thisSeg.end);
            animate("Merged right. Probably line? " + rightSeg.isProbablyLine()
                + ". Percent error change: " + Debug.num(mergeRightErrorChange), candidates);
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

  private void animate(String msg, SortedSet<Integer> ids, Segment focus) {
    if (ani != null) {
      DrawingBuffer db = new DrawingBuffer();
      DrawingBufferRoutines.text(db, new Pt(20, 20), msg, Color.BLACK);
      Set<Pt> junctions = new HashSet<Pt>();
      int prev = -1;
      for (int i : ids) {
        if (prev >= 0) {
          Segment seg = new Segment(prev, i, seq);
          Pt mid = seq.get((seg.start + seg.end) / 2);
          mid = new Pt(mid.x, mid.y + 10);
          DrawingBufferRoutines.text(db, mid, seg.type + ": " + seg.getError(), Color.BLACK);
          junctions.add(seq.get(seg.start));
          junctions.add(seq.get(seg.end));
        }
        prev = i;
      }
      if (focus != null) {
        DrawingBufferRoutines.line(db, seq.get(focus.start), seq.get(focus.end), Color.RED, 2.0);
      }
      for (Pt pt : junctions) {
        DrawingBufferRoutines.dot(db, pt, 5.0, 0.5, Color.BLACK, Color.BLUE);
      }
      ani.addFrame(db, true);
    }
  }

  private void animate(String msg, SortedSet<Integer> ids) {
    animate(msg, ids, null);
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

  public enum Type {
    Line, Arc
  }

}
