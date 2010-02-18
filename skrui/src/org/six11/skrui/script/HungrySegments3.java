package org.six11.skrui.script;

import java.awt.Color;
import java.util.*;

import org.six11.skrui.BoundedParameter;
import org.six11.skrui.CircleArc;
import org.six11.skrui.DrawingBufferRoutines;
import org.six11.skrui.SkruiScript;
import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.data.Statistics;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.SequenceEvent;
import org.six11.util.pen.SequenceListener;
import org.six11.util.pen.Vec;

/**
 * This is a 'hello world' implementation of DrawingScript. To use it, just mention it on the Main
 * command line.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class HungrySegments3 extends SkruiScript implements SequenceListener {

  // private static final String K_SOME_THING = "some-thing";
  // private static final String K_ANOTHER_THING = "another-thing";

  public static Arguments getArgumentSpec() {
    Arguments args = new Arguments();
    args.setProgramName("A Nice Hello World");
    args.setDocumentationProgram("This serves as a template for new scripts. Copy away!");

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
    // defs.put(K_SOME_THING, new BoundedParameter.Double(K_SOME_THING, "Some Thing",
    // "This is some sort of double-precision floating point parameter.", 0, 1, 0.75));
    // defs.put(K_ANOTHER_THING, new BoundedParameter.Double(K_ANOTHER_THING, "Another Thing",
    // "This is another parameter.", 1, 5, 2.5));
    return defs;
  }

  private static void bug(String what) {
    Debug.out("HungrySegments3", what);
  }

  // ///
  //
  // * Put member variable declarations here. *
  //
  // //
  Envelope envelope = new Envelope(6, 15, 1);

  @Override
  public void initialize() {
    bug("HungrySegments3 is alive!");
    main.getDrawingSurface().getSoup().addSequenceListener(this);
  }

  public void handleSequenceEvent(SequenceEvent seqEvent) {
    if (seqEvent.getType() == SequenceEvent.Type.END) {
      bug("Sequence received.");
      Animation ani = (Animation) main.getScript("Animation");
      if (ani != null) {
        ani.startAnimation(main.getDrawingSurface().getBounds(), "png");
      }

      // Identify the Sezgin-style corners
      SortedSet<Region> regions = findRegions(seqEvent.getSeq());
      List<HungrySegment3> segments = new ArrayList<HungrySegment3>();
      for (Region r : regions) {
        segments.addAll(r.segments);
      }
      animate(segments, "All segments");
    }
  }

  /**
   * This identifies the Sezgin-style corners and creates a new Region for each.
   */
  private SortedSet<Region> findRegions(Sequence seq) {
    SortedSet<Region> ret = new TreeSet<Region>();
    seq.calculateCurvatureEuclideanWindowSize(24.0);
    Statistics stats = new Statistics();
    for (Pt pt : seq) {
      stats.addData(Math.abs(pt.getDouble("curvature")));
    }
    double medianCurve = stats.getMedian();
    double aveSpeed = seq.calculateSpeed() / (double) seq.size();
    double threshSpeed = 0.75 * aveSpeed;
    double threshCurve = 2.5 * medianCurve;

    SortedSet<Integer> candidates = new TreeSet<Integer>();
    candidates.add(0);
    candidates.add(seq.size() - 1);

    for (int i = 0; i < seq.size(); i++) {
      Pt pt = seq.get(i);
      if ((Math.abs(pt.getDouble("curvature")) > threshCurve)
          && (pt.getDouble("speed") < threshSpeed)) {
        candidates.add(i);
      }
    }
    candidates = removeDupes(candidates, seq);
    animate(candidates, seq, "Initial corners");
    int prev = -1;
    for (int idx : candidates) {
      if (prev >= 0) {
        Region r = new Region(prev, idx, seq);
        ret.add(r);
      }
      prev = idx;
    }
    return ret;
  }

  private void animate(Collection<Integer> indexes, Sequence seq, String msg) {
    if (main.getScript("Animation") != null) {
      Animation ani = (Animation) main.getScript("Animation");
      DrawingBuffer db = new DrawingBuffer();
      if (msg != null) {
        db.moveTo(20, 20);
        db.down();
        db.addText(msg, Color.BLACK);
        db.up();
      }
      for (int i : indexes) {
        Pt pt = seq.get(i);
        DrawingBufferRoutines.dot(db, pt, 6.0, 0.5, Color.BLACK, Color.RED);
      }
      ani.addFrame(db, true);
    }
  }

  public void animate(Collection<HungrySegment3> segments, String msg) {
    if (main.getScript("Animation") != null) {
      Animation ani = (Animation) main.getScript("Animation");
      DrawingBuffer db = new DrawingBuffer();
      if (msg != null) {
        db.moveTo(20, 20);
        db.down();
        db.addText(msg, Color.BLACK);
        db.up();
      }
      for (HungrySegment3 seg : segments) {
        Line ideal = seg.getIdealLine();
        Pt start = ideal.getStart();
        Pt end = ideal.getEnd();
        DrawingBufferRoutines.line(db, start, end, Color.BLUE);
        DrawingBufferRoutines.dot(db, start, 6.0, 0.5, Color.BLACK, Color.GREEN);
        DrawingBufferRoutines.dot(db, end, 6.0, 0.5, Color.BLACK, Color.RED);
      }
      ani.addFrame(db, true);
    }
  }

  private SortedSet<Integer> removeDupes(Collection<Integer> in, Sequence origin) {
    List<Integer> working = new ArrayList<Integer>();
    working.addAll(in);
    for (int i = 0; i < (working.size() - 1);) {
      Pt a = origin.get(working.get(i));
      Pt b = origin.get(working.get(i + 1));
      int idxA = origin.indexOf(a);
      int idxB = origin.indexOf(b);
      double dist = origin.getPathLength(idxA, idxB);
      if (dist < 15) {
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
    return new TreeSet<Integer>(working);
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

  class Envelope {
    double min, max, exp;

    Envelope(double min, double max, double exp) {
      this.min = min;
      this.max = max;
      this.exp = exp;
    }

    double eval(double x) {
      double ret = 0;
      if (x < min) {
        ret = 0;
      } else if (x > max) {
        ret = 1;
      } else {
        double numer = max - x;
        double denom = max - min;
        ret = Math.pow(numer / denom, exp);
      }
      return ret;
    }
  }

  enum Type {
    Line, Arc
  }

  public class Region implements Comparable<Region> {

    int start, end;
    Sequence seq;
    SortedSet<HungrySegment3> segments;

    public Region(int start, int end, Sequence seq) {
      this.start = start;
      this.end = end;
      this.seq = seq;
      this.segments = makeSegments();
    }

    public int numPoints() {
      return end - start;
    }

    private SortedSet<HungrySegment3> makeSegments() {
      SortedSet<HungrySegment3> segments = new TreeSet<HungrySegment3>();
      for (int i = start; i <= end; i++) {
        HungrySegment3 seg = new HungrySegment3(this, i);
        segments.add(seg);
      }
      // At this point 'segments' has a whole mash of overlapping segments. Let the longest segment
      // consume the shorter ones that claim the same points.
      HungrySegment3[] original = new HungrySegment3[segments.size()];
      original = segments.toArray(original);
      for (int i = 0; i < original.length - 1; i++) {
        HungrySegment3 source = original[i];
        for (int j = (i + 1); j < original.length; j++) {
          HungrySegment3 doomed = original[j];
          int overlapStart = Math.max(source.start, doomed.start);
          int overlapEnd = Math.min(source.end, doomed.end);
          int diff = overlapEnd - overlapStart;
          if (overlapStart < overlapEnd) {
            if (doomed.end == overlapEnd) {
              doomed.end = doomed.end - diff;
            }
            if (doomed.start == overlapStart) {
              doomed.start = doomed.start + diff;
            }
          }
        }
      }
      // now reload 'segments' with the now-shorter segments.
      segments.clear();
      for (HungrySegment3 seg : original) {
        if (seg.getPathLength() > 0) {
          if (seg.isOk()) {
            seg.setType();
            segments.add(seg);
          }
        }
      }
      // Finally, run a relaxation algorithm to let adjacent segments move into better positions to
      // reduce overall error.
      // TODO: do the relaxation thing here.
      
      animate(segments, "Segments for region " + this);
      return segments;
    }

    public String toString() {
      return "Region [" + start + ", " + end + "]";
    }

    public int compareTo(Region o) {
      int ret = 0;
      if (start < o.start) {
        ret = -1;
      } else if (start > o.start) {
        ret = 1;
      }
      return ret;
    }
  }

  public class HungrySegment3 implements Comparable<HungrySegment3> {
    Region r;
    int center, start, end;
    Type type;

    HungrySegment3(Region r, int center) {
      int pleasantWindow = 0;
      for (int k = 2; k < r.numPoints(); k++) {
        double e = 0;
        e = measureLinePainInWindow(center, k, r);
        if (e == 0) {
          pleasantWindow = k;
        }
        ArcPainResult arcResult = measureArcPainInWindow(center, k, r);
        e = Math.min(e, arcResult.pain);
        if (Double.isInfinite(e)) {
          // do nothing
        } else if (e >= 1) {
          break;
        }
      }
      this.r = r;
      this.start = Math.max(r.start, center - pleasantWindow);
      this.end = Math.min(r.end, center + pleasantWindow);
    }

    public void setType() {
      getIdealLine(); // causes 'center' to be set.
      double linePain = measureLinePainInLimits(start, center, end, r.seq);
      if (linePain == 0) {
        this.type = Type.Line;
      } else {
        ArcPainResult apr = measureArcPainInLimits(start, end, r.seq);
        if (apr.pain < linePain) {
          this.type = Type.Arc;
        } else {
          this.type = Type.Line;
        }
      }
    }

    public Type getType() {
      return type;
    }

    public Line getIdealLine() {
      // OK, time for some trickery. We have start/end indices, and we could just leave well enough
      // alone and use those as the endpoints for our line. But no, we have to make things difficult
      // by fitting a similar (but slightly different) line, all in the name of science and
      // aesthetics. Use the vector from the segment start to end points, but find the line that
      // minimizes standard error to the actual sequence points in that region.
      double bestError = Double.MAX_VALUE;
      Vec dir = new Vec(r.seq.get(start), r.seq.get(end)); // direction is start/end of this segment
      Line bestLine = null;
      for (int idx = start; idx <= end; idx++) {
        Line line = new Line(r.seq.get(idx), dir);
        double thisError = 0;
        for (int i = start; i < end; i++) {
          double v = Functions.getDistanceBetweenPointAndLine(r.seq.get(i), line);
          thisError = thisError + (v * v);
        }
        if (thisError < bestError) {
          bestError = thisError;
          bestLine = line;
          center = idx;
        }
      }
      Pt altStart = Functions.getNearestPointOnLine(r.seq.get(r.start), bestLine);
      Pt altEnd = Functions.getNearestPointOnLine(r.seq.get(r.end), bestLine);
      return new Line(altStart, altEnd);
    }

    public boolean isOk() {
      return (end - start) > 1;
    }

    public String toString() {
      return "Hungry [" + start + ", " + center + ", " + end + "], length: "
          + Debug.num(getPathLength()) + ", " + r;
    }

    /**
     * Returns a number between 0 and 1 indicating how much 'pain' is involved in fitting a line
     * starting at i with a window size of 2k for the provided sequence. A return value of 0 means
     * all points were less than the minimum pain threshold distance.
     */
    private double measureLinePainInWindow(int i, int k, Region r) {
      int lower = Math.max(r.start, i - k);
      int upper = Math.min(i + k, r.end);
      return measureLinePainInLimits(lower, i, upper, r.seq);
    }

    private double measureLinePainInLimits(int lower, int target, int upper, Sequence seq) {
      double ret = 0;
      Pt a = seq.get(lower);
      Pt b = seq.get(upper);
      Pt m = seq.get(target);

      Vec dir = new Vec(a, b);
      Line line = new Line(m, dir);
      for (int j = lower; j <= upper; j++) {
        Pt pt = seq.get(j);
        double dist = Functions.getDistanceBetweenPointAndLine(pt, line);
        double e = envelope.eval(dist);
        ret = Math.max(ret, e);
      }
      return ret;
    }

    private ArcPainResult measureArcPainInWindow(int i, int k, Region r) {
      int lower = Math.max(r.start, i - k);
      int upper = Math.min(i + k, r.end);
      return measureArcPainInLimits(lower, upper, r.seq);
    }

    private ArcPainResult measureArcPainInLimits(int lower, int upper, Sequence seq) {
      ArcPainResult ret = new ArcPainResult();
      ret.pain = Double.POSITIVE_INFINITY;
      CircleArc bestCircle = CircleArc.makeBestCircle(lower, upper, seq);
      if (bestCircle != null) {
        ret.arc = bestCircle;
        ret.pain = 0;
        Pt c = ret.arc.getCenter();
        double r = ret.arc.getRadius();
        for (int j = lower; j <= upper; j++) {
          Pt pt = seq.get(j);
          double dist = Math.abs(r - c.distance(pt));
          double e = envelope.eval(dist);
          ret.pain = Math.max(e, ret.pain);
        }
      }

      return ret;
    }

    private double getPathLength() {
      return r.seq.get(start).distance(r.seq.get(end));
    }

    public int compareTo(HungrySegment3 other) {
      int ret = 0;
      if (this.getPathLength() < other.getPathLength()) {
        ret = 1;
      } else if (this.getPathLength() > other.getPathLength()) {
        ret = -1;
      }
      return ret;
    }
  }

  class ArcPainResult {
    CircleArc arc;
    double pain;
  }

}
