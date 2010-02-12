package org.six11.skrui.script;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.SortedSet;
import java.util.TreeSet;

import org.six11.skrui.BoundedParameter;
import org.six11.skrui.CircleArc;
import org.six11.skrui.DrawingBufferRoutines;
import org.six11.skrui.SkruiScript;
import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.data.Lists;
import org.six11.util.data.Statistics;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.SequenceEvent;
import org.six11.util.pen.SequenceListener;

public class HungrySegments extends SkruiScript implements SequenceListener {

  public static final String K_MEAN_SPEED_MULT = "hs-speed-mult";
  public static final String K_ERROR_TOLERANCE = "hs-error-tolerance";

  // private static final String K_ANOTHER_THING = "anot";

  @Override
  public void initialize() {
    bug("HungrySegments is alive!");
    main.getDrawingSurface().getSoup().addSequenceListener(this);
  }

  public static Arguments getArgumentSpec() {
    Arguments args = new Arguments();
    args.setProgramName("HungrySegments junction/segment finder.");
    args.setDocumentationProgram("This uses a greedy algorithm to fit lines/arcs to portions of "
        + "drawn strokes in order to determine where corners are, and what the "
        + "intervening segments are.");

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
    defs.put(K_MEAN_SPEED_MULT, new BoundedParameter.Double(K_MEAN_SPEED_MULT,
        "Mean speed multiplier",
        "Points whose velocity is below the mean multiplied by this value are considered slow.", 0,
        1, 0.75));
    defs.put(K_ERROR_TOLERANCE, new BoundedParameter.Double(K_ERROR_TOLERANCE,
        "Maximum error tolerance",
        "The maximum error the algorithm will tolerate before discontinuing its greedy expansion.",
        0, 100, 60));
    return defs;
  }

  private static void bug(String what) {
    Debug.out("HungrySegments", what);
  }

  public void handleSequenceEvent(SequenceEvent seqEvent) {
    if (seqEvent.getType() == SequenceEvent.Type.END) {
      hungry(seqEvent.getSeq());
    }
  }

  private void hungry(Sequence seq) {
    seq.calculateCurvatureEuclideanWindowSize(24.0);

    List<Pt> slow = findSlow(seq);
    List<Pt> fast = findFast(seq, slow);

    // debugPoints(slow, Color.RED);
    // debugPoints(fast, Color.BLUE);
    List<HungryRegion> hungryRegions = new ArrayList<HungryRegion>();
    for (Pt pt : fast) {
      hungryRegions.add(new HungryRegion(seq, seq.indexOf(pt)));
    }

    for (HungryRegion r : hungryRegions) {
      int round = 0;
      while (!r.isFinished()) {
        r.expand();
        round++;
      }
      bug("Region " + r + " is finished! (round " + round + ")");
    }

    SortedSet<ContendedRegion> contenders = new TreeSet<ContendedRegion>();
    for (int i = 0; i < hungryRegions.size() - 1; i++) {
      HungryRegion r1 = hungryRegions.get(i);
      for (int j = i + 1; j < hungryRegions.size(); j++) {
        HungryRegion r2 = hungryRegions.get(j);
        List<Integer> overlap = r1.getOverlap(r2);
        if (overlap.size() > 0) {
          contenders.add(new ContendedRegion(r1, r2, overlap));
        }
      }
    }
    // Resolve contended regions in order of most contentious to least.
    for (ContendedRegion contender : contenders) {
      resolve(contender.r1, contender.r2, contender.overlap);
    }
    debugRegions(hungryRegions);
  }

  private void resolve(HungryRegion r1, HungryRegion r2, List<Integer> overlap) {
    for (int removeMe : overlap) {
      r1.retreat(removeMe);
      r2.retreat(removeMe);
    }
    bug("Resolving regions fighting over " + Debug.num(overlap, " "));
    bug("  " + r1);
    bug("  " + r2);
    int r1s = r1.getStartIdx();
    int r1e = r1.getEndIdx();
    int r2s = r2.getStartIdx();
    int r2e = r2.getEndIdx();

    int bestIdx = -1;
    HungryError bestError = new HungryError(Double.POSITIVE_INFINITY, null);
    for (int i = 0; i < overlap.size(); i++) {
      int idx = overlap.get(i);
      HungryError err1 = HungrySegments.measureBestError(r1.seq, Math.min(r1s, idx), Math.max(r1e,
          idx));
      HungryError err2 = HungrySegments.measureBestError(r2.seq, Math.min(r2s, idx), Math.max(r2e,
          idx));
      bug("If region 1 goes from " + Math.min(r1s, idx) + " to " + Math.max(r1e, idx)
          + " the error is: " + Debug.num(err1.error));
      bug("If region 2 goes from " + Math.min(r2s, idx) + " to " + Math.max(r2e, idx)
          + " the error is: " + Debug.num(err2.error));
      if (bestError.error > (err1.error + err2.error)) {
        bug("The total error is " + Debug.num(err1.error + err2.error)
            + " and beats the previous error, so we'll roll with index" + idx);
        bestIdx = idx;
        bestError.error = err1.error + err2.error;
      } else {
        bug("Sadly, the total error is " + Debug.num(err1.error + err2.error)
            + " and does not beat the previous error.");
      }
    }
    if (bestIdx < 0) {
      bug("I couldn't resolve this region! " + r1 + " " + r2 + " " + Debug.num(overlap, " "));
      System.exit(-1);
    } else {
      bug("Best index is: " + bestIdx);
      HungryRegion lower = r1.getEndIdx() < r2.getEndIdx() ? r1 : r2;
      HungryRegion upper = lower == r1 ? r2 : r1;
      while (lower.getEndIdx() < bestIdx) {
        lower.expandTo(lower.getEndIdx() + 1, new HungryError(Double.POSITIVE_INFINITY, null),
            r1.seq);
      }
      while (upper.getStartIdx() > bestIdx) {
        upper.expandTo(upper.getStartIdx() - 1, new HungryError(Double.POSITIVE_INFINITY, null),
            r2.seq);
      }
    }
  }

  private void debugRegions(List<HungryRegion> hungryRegions) {
    DrawingBuffer db = new DrawingBuffer();
    for (HungryRegion r : hungryRegions) {
      Pt start = r.seq.get(r.getStartIdx());
      Pt end = r.seq.get(r.getEndIdx());
      DrawingBufferRoutines.line(db, start, end, Color.BLUE);
    }
    main.getDrawingSurface().getSoup().addBuffer(db);
  }

  private void debugPoints(List<Pt> points, Color fillColor) {
    DrawingBuffer db = new DrawingBuffer();
    DrawingBufferRoutines.dots(db, points, 4.0, 0.3, Color.BLACK, fillColor);
    main.getDrawingSurface().getSoup().addBuffer(db);
  }

  private List<Pt> findFast(Sequence seq, List<Pt> slow) {
    List<Pt> fast = new ArrayList<Pt>();
    Pt prev = null;
    for (Pt pt : slow) {
      if (prev != null) {
        int idxPrev = seq.indexOf(prev);
        int idxThis = seq.indexOf(pt);
        Pt fastPt = null;
        double fastPtSpeed = 0.0;
        for (int i = idxPrev; i <= idxThis; i++) {
          Pt who = seq.get(i);
          if (who.getDouble("speed") > fastPtSpeed) {
            fastPt = who;
            fastPtSpeed = fastPt.getDouble("speed");
          }
        }
        if (fastPt != null) {
          fast.add(fastPt);
        }
      }
      prev = pt;
    }
    return fast;
  }

  private List<Pt> findSlow(Sequence seq) {
    double aveSpeed = seq.calculateSpeed() / (double) seq.size();
    double threshSpeed = getParam(K_MEAN_SPEED_MULT).getDouble() * aveSpeed;
    List<Pt> slow = new ArrayList<Pt>();
    for (Pt pt : seq) {
      if (pt.getDouble("speed") < threshSpeed) {
        if (slow.size() > 0) {
          Pt prev = slow.get(slow.size() - 1);
          if (prev.distance(pt) < 20) {
            if (prev.getDouble("speed") > pt.getDouble("speed")) {
              slow.remove(slow.size() - 1);
              slow.add(pt);
            }
          } else {
            slow.add(pt);
          }
        } else {
          slow.add(pt);
        }
      }
    }
    return slow;
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

  private static HungryError measureBestError(Sequence seq, int min, int max) {
    HungryError lineError = HungrySegments.measureLineError(seq, min, max);
    HungryError arcError = HungrySegments.measureArcError(seq, min, max);

    HungryError ret = null;
    // TODO: The constants here should be parameters.
    if (lineError.error < arcError.error * 1.5) {
      // it is a line if the arcError is more than 50% more than lineError
      ret = lineError;
    } else if (seq.getPathLength(min, max) > 50) {
      // if the path length is long enough, we can say it is an arc.
      ret = arcError;
    } else {
      // path isn't long enough, so it's a line.
      ret = lineError;
    }
    return ret;
  }

  private static HungryError measureLineError(Sequence seq, int a, int b) {
    Line line = new Line(seq.get(a), seq.get(b));
    double ret = 0;
    for (int i = a; i <= b; i++) {
      double dist = Functions.getDistanceBetweenPointAndLine(seq.get(i), line);
      ret += dist * dist;
    }
    return new HungryError(ret / (b - a), null);
  }

  private static HungryError measureArcError(Sequence seq, int a, int b) {
    double errorSum = 0.0;
    List<CircleArc> arcs = new ArrayList<CircleArc>();
    for (int i = a + 1; i < b; i++) {
      CircleArc ca = new CircleArc(seq.get(a), seq.get(i), seq.get(b));
      arcs.add(ca);
    }
    Collections.sort(arcs, CircleArc.comparator); // sort based on radius
    CircleArc bestCircle = arcs.get(arcs.size() / 2); // get the arc with median radius

    if (bestCircle.getCenter() == null) {
      errorSum = Double.POSITIVE_INFINITY;
    } else {
      for (int i = a; i < b; i++) {
        Pt pt = seq.get(i);
        double r1 = bestCircle.getCenter().distance(pt);
        double d = r1 - bestCircle.getRadius();
        errorSum += d * d;
      }
    }
    return new HungryError(errorSum / (b - a), bestCircle);
  }

  class HungryRegion {

    Sequence seq;
    List<Integer> pointIndices;
    List<Integer> taboo;
    List<HungryError> errors;
    boolean finished;

    public HungryRegion(Sequence seq, int ptIdx) {
      this.seq = seq;
      this.pointIndices = new ArrayList<Integer>();
      pointIndices.add(ptIdx);
      this.errors = new ArrayList<HungryError>();
      errors.add(new HungryError(0.0, null));
      this.taboo = new ArrayList<Integer>();
      finished = false;
    }

    public void retreat(int removeMe) {
      pointIndices.remove(new Integer(removeMe));
    }

    public List<Integer> getOverlap(HungryRegion other) {
      List<Integer> ret = new ArrayList<Integer>();
      for (int i = Math.max(getStartIdx(), other.getStartIdx()); i <= Math.min(getEndIdx(), other
          .getEndIdx()); i++) {
        ret.add(i);
      }
      bug("Overlap of [" + getStartIdx() + ", " + getEndIdx() + "] and [" + other.getStartIdx()
          + ", " + other.getEndIdx() + "] is " + Debug.num(ret, " "));
      return ret;
    }

    public int expand() {
      int ret = -1; // negative return indicates expansion impossible.
      int start = getStartIdx();
      int end = getEndIdx();
      int prev = start - 1;
      int next = end + 1;

      HungryError errorPrev = new HungryError(Double.POSITIVE_INFINITY, null);
      HungryError errorNext = new HungryError(Double.POSITIVE_INFINITY, null);
      if (prev >= 0 && !taboo.contains(prev)) {
        errorPrev = HungrySegments.measureBestError(seq, prev, end);
      }
      if (next < seq.size() && !taboo.contains(next)) {
        errorNext = HungrySegments.measureBestError(seq, start, next);
      }
      if (errorPrev.error < errorNext.error) {
        if (expandTo(prev, errorPrev, seq)) {
          ret = prev;
        } else {
          taboo.add(prev);
        }
      } else if (errorNext.error < errorPrev.error) {
        if (expandTo(next, errorNext, seq)) {
          ret = next;
        } else {
          taboo.add(next);
        }
      } else if (errorPrev.error < Double.POSITIVE_INFINITY
          && errorNext.error < Double.POSITIVE_INFINITY) {
        if (expandTo(next, errorNext, seq)) {
          ret = next;
        } else {
          taboo.add(next);
        }
      } else {
        finish();
      }
      return ret;
    }

    private void finish() {
      if (errors.size() == pointIndices.size()) {
        // find the last 'stable' line. Do this by examining the error values. There should be a
        // roughly steady state that gets bad near the end. Find the most recent value that could be
        // considered in the steady state.
        List<Double> errorValues = new ArrayList<Double>();
        for (HungryError error : errors) {
          errorValues.add(error.error);
        }
        List<Double> deltas = Lists.getDeltaList(errorValues);
        double deltaThresh = 0;

        int casualties = 0;
        while (deltas.size() > 0 && deltas.get(deltas.size() - 1) > deltaThresh) {
          deltas.remove(deltas.size() - 1);
          pointIndices.remove(pointIndices.size() - 1);
          casualties++;
        }
        errors.clear(); // won't be needing these any longer.
        errors.add(HungrySegments.measureBestError(seq, getStartIdx(), getEndIdx()));
        bug("Expunged " + casualties + " points.");
      } else {
        bug("error: lineErrors and pointIndices are not of same size!");
      }
      finished = true;
    }

    public boolean isFinished() {
      return finished;
    }

    private boolean expandTo(int idx, HungryError err, Sequence seq) {
      if (idx < 0 || idx >= seq.size()) {
        System.out.println("idx: " + idx + ", seq.size(): " + seq.size());
        new RuntimeException("Can't expand past the edge of the sequence.").printStackTrace();
        System.exit(0);
      }
      boolean ret = false;
      if (err.error < getParam(K_ERROR_TOLERANCE).getDouble()) {
        pointIndices.add(idx);
        errors.add(err);
        ret = true;
      }
      return ret;
    }

    public String toString() {
      int min = getStartIdx();
      int max = getEndIdx();
      int n = pointIndices.size();
      double len = seq.getPathLength(min, max);
      HungryError el = errors.size() > 0 ? errors.get(errors.size() - 1) : new HungryError(0.0,
          null);
      return "HungrySegment[" + min + ", " + max + " (length: " + Debug.num(len) + ", points: " + n
          + ", lineError: " + el.toString() + ")]";
    }

    private int getEndIdx() {
      int ret = -1;
      for (int i = 0; i < pointIndices.size(); i++) {
        ret = Math.max(pointIndices.get(i), ret);
      }
      return ret;
    }

    private int getStartIdx() {
      int ret = Integer.MAX_VALUE;
      for (int i = 0; i < pointIndices.size(); i++) {
        ret = Math.min(pointIndices.get(i), ret);
      }
      return ret;
    }
  }

  class ContendedRegion implements Comparable<ContendedRegion> {
    HungryRegion r1, r2;
    List<Integer> overlap;

    ContendedRegion(HungryRegion r1, HungryRegion r2, List<Integer> overlap) {
      this.r1 = r1;
      this.r2 = r2;
      this.overlap = overlap;
    }

    // Sorts in descending order of overlap size.
    public int compareTo(ContendedRegion o) {
      int ret = 0;
      if (overlap.size() < o.overlap.size()) {
        ret = 1;
      } else if (overlap.size() > o.overlap.size()) {
        ret = -1;
      }
      return ret;
    }
  }

  static class HungryError {
    double error;
    CircleArc arc;

    HungryError(double error, CircleArc arc) {
      this.error = error;
      this.arc = arc;
    }
  }

}
