package org.six11.skrui.script;

import java.awt.Color;
import java.util.*;

import org.six11.skrui.BoundedParameter;
import org.six11.skrui.DrawingBufferRoutines;
import org.six11.skrui.SkruiScript;
import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.data.Statistics;
import org.six11.util.pen.CircleArc;
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

  public class CombinedErrorResult {
    public double error;
    public Pt intersection;
    public int nearestSeqPoint;

    public CombinedErrorResult() {
      this.error = Double.MAX_VALUE;
      this.intersection = null;
      this.nearestSeqPoint = -1;
    }
  }

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
        bug("Drawing segment of type " + seg.getType());
        if (seg.getType() == Type.Line) {
          Line ideal = seg.getIdealLine();
          Pt start = ideal.getStart();
          Pt end = ideal.getEnd();
          DrawingBufferRoutines.line(db, start, end, Color.BLUE);
          DrawingBufferRoutines.dot(db, start, 6.0, 0.5, Color.BLACK, Color.GREEN);
          DrawingBufferRoutines.dot(db, end, 6.0, 0.5, Color.BLACK, Color.RED);
        } else if (seg.getType() == Type.Arc) {
          CircleArc arc = seg.getArc();
          DrawingBufferRoutines.arc(db, arc, Color.CYAN);
        }
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
      negotiate(segments);

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
    public Pt endIntersection;
    public Pt startIntersection;

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

    public HungrySegment3(int start, int end, Region r) {
      this.start = start;
      this.end = end;
      this.r = r;
      setType();
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
      Line ret = null;
      if (startIntersection != null && endIntersection != null) {
        ret = new Line(startIntersection, endIntersection);
      } else {
        // OK, time for some trickery. We have start/end indices, and we could just leave well
        // enough alone and use those as the endpoints for our line. But no, we have to make things
        // difficult by fitting a similar (but slightly different) line, all in the name of science
        // and aesthetics. Use the vector from the segment start to end points, but find the line
        // that minimizes standard error to the actual sequence points in that region.
        double bestError = Double.MAX_VALUE;
        Vec dir = new Vec(r.seq.get(start), r.seq.get(end)); // dir is start/end of this segment
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

        if (bestLine != null) {
          Pt altStart = Functions.getNearestPointOnLine(r.seq.get(r.start), bestLine);
          Pt altEnd = Functions.getNearestPointOnLine(r.seq.get(r.end), bestLine);
          ret = new Line(altStart, altEnd);
        }
      }
      return ret;
    }

    public CircleArc getArc() {
      return measureArcPainInLimits(start, end, r.seq).arc;
    }

    public boolean isOk() {
      return (end - start) > 1 && (r.seq.get(start).distance(r.seq.get(end)) > 20);
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

    public int numPoints() {
      return end - start;
    }

    public void imitate(HungrySegment3 copyMe) {
      this.start = copyMe.start;
      this.end = copyMe.end;
      this.center = copyMe.center;
      this.r = copyMe.r;
      this.type = copyMe.type;
    }
  }

  class ArcPainResult {
    CircleArc arc;
    double pain;
  }

  public List<HungrySegment3> negotiate(SortedSet<HungrySegment3> notInOrder) {
    // incoming segments are in order of length, not position.

    double improvement = 0;
    List<HungrySegment3> segments = new ArrayList<HungrySegment3>(notInOrder);
    Collections.sort(segments, new Comparator<HungrySegment3>() {
      public int compare(HungrySegment3 o1, HungrySegment3 o2) {
        int ret = 0;
        if (o1.start < o2.start) {
          ret = -1;
        } else if (o1.start > o2.start) {
          ret = 1;
        }
        return ret;
      }
    });
    int round = 1;
    do {
      if (round > 10) {
        System.exit(0);
      }
      animate(segments, "Negotiation Round " + round);
      Collection<HungrySegment3> doomed = new HashSet<HungrySegment3>();
      for (int i = 0; i < segments.size() - 1; i++) {
        improvement = 0;
        HungrySegment3 s1 = segments.get(i);
        HungrySegment3 s2 = segments.get(i + 1);
        if (s1.isOk() && s2.isOk()) {
          bug("Negotiating segments:");
          bug("  " + s1);
          bug("  " + s2);
          improvement = improvement + negotiate(s1, s2);
          bug("  improvement: " + Debug.num(improvement));
        }
        if (!s1.isOk()) {
          doomed.add(s1);
        }
        if (!s2.isOk()) {
          doomed.add(s2);
        }
        if (doomed.size() > 0) {
          bug("Removing " + doomed.size() + " doomed segments.");
        }
        segments.removeAll(doomed);
      }
    } while (improvement > 0);

    List<HungrySegment3> ret = new ArrayList<HungrySegment3>();
    for (HungrySegment3 seg : segments) {
      if (seg.isOk()) {
        ret.add(seg);
      }
    }
    // set the segment intersection points.
    for (int i = 0; i < ret.size(); i++) {
      HungrySegment3 seg = ret.get(i);
      if (i == 0) {
        // the 'intersection' for the first point is on the ideal line/arc that is closest to the
        // user's raw ink point.
        if (seg.getType() == Type.Line) {
          Pt where = Functions.getNearestPointOnLine(seg.r.seq.getFirst(), seg.getIdealLine());
          seg.startIntersection = where;
        } else if (seg.getType() == Type.Arc) {
          // a bit trickier: cast a ray from the arc center to the sequence last point and
          // get the intersecting point
          CircleArc arc = seg.getArc();
          Vec vec = new Vec(arc.getCenter(), seg.r.seq.getFirst()).getVectorOfMagnitude(arc
              .getRadius());
          Pt where = new Pt(arc.getCenter().x + vec.getX(), arc.getCenter().y + vec.getY());
          seg.startIntersection = where;
        }
      }
      if (i == (ret.size() - 1)) {
        // on the very last segment I only need to set the endIntersection.
        if (seg.getType() == Type.Line) {
          Pt where = Functions.getNearestPointOnLine(seg.r.seq.getLast(), seg.getIdealLine());
          seg.endIntersection = where;
        } else if (seg.getType() == Type.Arc) {
          // a bit trickier: cast a ray from the arc center to the sequence last point and
          // get the intersecting point
          CircleArc arc = seg.getArc();
          Vec vec = new Vec(arc.getCenter(), seg.r.seq.getLast()).getVectorOfMagnitude(arc
              .getRadius());
          Pt where = new Pt(arc.getCenter().x + vec.getX(), arc.getCenter().y + vec.getY());
          seg.endIntersection = where;
        }
      } else {
        // on all other segments I set the end of this one and the start of the next.
        HungrySegment3 next = ret.get(i + 1);
        CombinedErrorResult errorData = getCombinedError(seg, next);
        seg.endIntersection = errorData.intersection;
        next.startIntersection = errorData.intersection;
      }
    }
    return ret;
  }

  private CombinedErrorResult getCombinedError(HungrySegment3 s1, HungrySegment3 s2) {
    CombinedErrorResult ret = new CombinedErrorResult();

    // Calculate the error between the ideal line/arcs and the raw ink.

    // First find the intersection of s1 and s2
    Pt intersection = null;
    if (s1.getType() == Type.Line && s2.getType() == Type.Line) {
      intersection = Functions.getIntersectionPoint(s1.getIdealLine(), s2.getIdealLine());
    } else if (s1.getType() == Type.Line && s2.getType() == Type.Arc) {
      intersection = Functions.getIntersectionPoint(s2.getArc(), s1.getIdealLine());
    } else if (s1.getType() == Type.Arc && s2.getType() == Type.Line) {
      intersection = Functions.getIntersectionPoint(s1.getArc(), s2.getIdealLine());
    } else if (s1.getType() == Type.Arc && s2.getType() == Type.Arc) {
      intersection = Functions.getIntersectionPoint(s1.getArc(), s2.getArc());
    }

    if (intersection == null) {
      bug("Intersection of " + s1.getType() + " and " + s2.getType() + " is null.");
    } else {
      bug("Intersection of " + s1.getType() + " and " + s2.getType() + " is ok: "
          + Debug.num(intersection));
      // Note which point on the sequence is closest to that intersection.
      HungrySegment3 left = s1;
      HungrySegment3 right = s2;
      if (s1.start > s2.start) {
        left = s2;
        right = s1;
      }
      int closestIdx = -1;
      double closestDist = Double.MAX_VALUE;
      for (int i = left.start; i <= right.end; i++) {
        Pt pt = left.r.seq.get(i);
        double d = pt.distance(intersection);
        if (d < closestDist) {
          closestDist = d;
          closestIdx = i;
        }
      }
      bug("Closest index is " + closestIdx);
      // calculate standard error between each point and the ideal line/arc as appropriate.
      ret.error = measureStandardError(left, left.start, closestIdx)
          + measureStandardError(right, closestIdx, right.end);
      ret.nearestSeqPoint = closestIdx;
      ret.intersection = intersection;
    }
    return ret;
  }

  private double measureStandardError(HungrySegment3 seg, int start, int end) {
    double ret = 0;
    Sequence seq = seg.r.seq;
    if (seg.getType() == Type.Line) {
      Line ideal = seg.getIdealLine();
      for (int i = start; i <= end; i++) {
        double v = Functions.getDistanceBetweenPointAndLine(seq.get(i), ideal);
        ret = ret + (v * v);
      }
    } else if (seg.getType() == Type.Arc) {
      CircleArc arc = seg.getArc();
      for (int i = start; i <= end; i++) {
        double v = seq.get(i).distance(arc.center) - arc.getRadius();
        ret = ret + (v * v);
      }
    }
    ret = ret / (end - start);
    bug("measureStandardError for " + seg.getType() + " in range: " + start + ", " + end + ": "
        + Debug.num(ret));
    return ret;
  }

  private double negotiate(HungrySegment3 s1, HungrySegment3 s2) {
    double improvement = 0;
    HungrySegment3 left = s1.start < s2.start ? s1 : s2;
    HungrySegment3 right = s1.start < s2.start ? s2 : s1;
    // Consider three cases:
    // 1. Do nothing.
    // 2. Enlarge left at (possibly) the expense of right
    // 3. Enlarge right at (possibly) the expense of left
    CombinedErrorResult doNothingError = getCombinedError(left, right);
    CombinedErrorResult enlargeLeftError = new CombinedErrorResult();
    HungrySegment3 llBest = null;
    HungrySegment3 rlBest = null;
    for (int i = 1; i < Math.min(5, right.numPoints()); i++) {
      HungrySegment3 ll = new HungrySegment3(left.start, left.end + i, left.r);
      HungrySegment3 rl = new HungrySegment3(Math.max(ll.end, right.start), right.end, right.r);
      if (ll.isOk() && rl.isOk()) {
        CombinedErrorResult enLeft = getCombinedError(ll, rl);
        if (enLeft.error < enlargeLeftError.error) {
          llBest = ll;
          rlBest = rl;
          enlargeLeftError = enLeft;
        }
      }
    }
    CombinedErrorResult enlargeRightError = new CombinedErrorResult();
    HungrySegment3 lrBest = null;
    HungrySegment3 rrBest = null;
    for (int i = 1; i < Math.min(5, left.numPoints()); i++) {
      HungrySegment3 rr = new HungrySegment3(right.start - i, right.end, right.r);
      HungrySegment3 lr = new HungrySegment3(left.start, Math.min(left.end, rr.start), left.r);
      if (rr.isOk() && lr.isOk()) {
        CombinedErrorResult enRight = getCombinedError(lr, rr);
        if (enRight.error < enlargeRightError.error) {
          lrBest = lr;
          rrBest = rr;
          enlargeRightError = enRight;
        }
      }
    }
    if (doNothingError.error <= enlargeLeftError.error
        && doNothingError.error <= enlargeRightError.error) {
      improvement = 0;
      bug("  No improvement...");
    } else if (enlargeLeftError.error <= enlargeRightError.error) {
      // moving to the left some amount is the best.
      left.imitate(llBest);
      right.imitate(rlBest);
      bug("  Enlarge left...");
      improvement = doNothingError.error - enlargeLeftError.error;
    } else {
      // moving to the right some amount is the best.
      left.imitate(lrBest);
      right.imitate(rrBest);
      bug("  Enlarge right...");
      improvement = doNothingError.error - enlargeRightError.error;
    }
    return improvement;
  }

}
