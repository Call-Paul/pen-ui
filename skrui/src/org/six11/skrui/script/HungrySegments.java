package org.six11.skrui.script;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Set;
import java.util.HashSet;


import org.six11.skrui.BoundedParameter;
import org.six11.skrui.DrawingBufferRoutines;
import org.six11.skrui.SkruiScript;
import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
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

    int round = 0;
    boolean done = false;
    while (!done) { // loop as long as one of the regions is not done
      done = true;
      for (int i = 0; i < hungryRegions.size(); i++) {
	HungryRegion r = hungryRegions.get(i);
	if (!r.isFinished()) {
	  int who = r.expand();
	  resolveContention(hungryRegions, who);
	  if (r.isFinished()) {
	    bug("Region " + i + " is finished! (round " + round + ")");
	  }
	}
	done = done && r.isFinished();
      }
      round++;
    }
    debugRegions(hungryRegions);
  }

  private void resolveContention(List<HungryRegion> regions, int idx) {

    Set<HungryRegion> contenders = new HashSet<HungryRegion>();
    for (HungryRegion r : regions) {
      if (r.claims(idx)) {
	contenders.add(r);
      }
    }
    if (contenders.size() > 1) {
      bug("point " + idx + " is contended!");
      // more than one region claims the given index. resolve it in
      // one of these ways:
      
      // * Give the index to one region and list it taboo to the
      // * other. (based on error)
      
      // * Merge the two segments by giving all of one region's points
      // * to the other and marking the depleted one as
      // * finished. Remember the loaded-up region should inherit the
      // * depleted's taboo list. The errors should be calculated as
      // * well, but not in the same order they appear in the source
      // * list. Rather they should be recalculated for each point
      // * absorbed by the loaded-up region.
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

  class HungryRegion {

    Sequence seq;
    List<Integer> pointIndices;
    List<Integer> taboo;
    List<Double> lineErrors;
    List<Double> arcErrors;
    boolean finished;

    public HungryRegion(Sequence seq, int ptIdx) {
      this.seq = seq;
      this.pointIndices = new ArrayList<Integer>();
      pointIndices.add(ptIdx);
      this.lineErrors = new ArrayList<Double>();
      lineErrors.add(0.0);
      this.arcErrors = new ArrayList<Double>();
      arcErrors.add(0.0);
      this.taboo = new ArrayList<Integer>();
      finished = false;
    }

    public boolean claims(int idx) {
      return pointIndices.contains(idx);
    }

    public int expand() {
      int ret = -1; // negative return indicates expansion impossible.
      int start = getStartIdx();
      int end = getEndIdx();
      int prev = start - 1;
      int next = end + 1;

      double errorPrev = Double.POSITIVE_INFINITY;
      double errorNext = Double.POSITIVE_INFINITY;
      if (prev >= 0 && !taboo.contains(prev)) {
        errorPrev = measureError(prev, end);
      }
      if (next < seq.size() && !taboo.contains(next)) {
        errorNext = measureError(start, next);
      }
      if (errorPrev < errorNext) {
        if (expandTo(prev, errorPrev, seq)) {
          ret = prev;
        } else {
	  taboo.add(prev);
	}
      } else if (errorNext < errorPrev) {
        if (expandTo(next, errorNext, seq)) {
          ret = next;
        } else {
	  taboo.add(next);
	}
      } else if (errorPrev < Double.POSITIVE_INFINITY && errorNext < Double.POSITIVE_INFINITY) {
	if (expandTo(next, errorNext, seq)) {
	  ret = next;
	} else {
	  taboo.add(next);
	}
      } else {
	finished = true;
      }
      return ret;
    }

    public boolean isFinished() {
      return finished;
    }

    private boolean expandTo(int idx, double err, Sequence seq) {
      if (idx < 0 || idx >= seq.size()) {
	System.out.println("idx: " + idx + ", seq.size(): " + seq.size());
	new RuntimeException("Can't expand past the edge of the sequence.").printStackTrace();
	System.exit(0);
      }
      boolean ret = false;
      if (err < getParam(K_ERROR_TOLERANCE).getDouble()) {
        pointIndices.add(idx);
        lineErrors.add(err);
        ret = true;
      }
      return ret;
    }

    private double measureError(int a, int b) {
      Line line = new Line(seq.get(a), seq.get(b));
      double ret = 0;
      for (int i = a; i <= b; i++) {
        double dist = Functions.getDistanceBetweenPointAndLine(seq.get(i), line);
        ret += dist * dist;
      }
      return ret / (b - a);
    }

    public String toString() {
      int min = getStartIdx();
      int max = getEndIdx();
      int n = pointIndices.size();
      double len = seq.getPathLength(min, max);
      double el = lineErrors.get(lineErrors.size() - 1);
      double ea = arcErrors.get(arcErrors.size() - 1);
      return "HungrySegment[" + min + ", " + max + " (length: " + Debug.num(len) + ", points: " + n
          + ", lineError: " + Debug.num(el) + ", arcError: " + Debug.num(ea) + ")]";
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

}
