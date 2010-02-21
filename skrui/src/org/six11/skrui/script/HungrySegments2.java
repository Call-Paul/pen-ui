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
import org.six11.util.pen.*;

/**
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class HungrySegments2 extends SkruiScript implements SequenceListener {

  private static final String K_ENV_MIN = "hs-env-min";
  private static final String K_ENV_MAX = "hs-env-max";
  private static final String K_ENV_EXP = "hs-env-exp";

  static int SEGMENT_COUNTER = 0;

  public static Arguments getArgumentSpec() {
    Arguments args = new Arguments();
    args.setProgramName("HungrySegments 2 segment finder");
    args.setDocumentationProgram("Another greedy perception-based segment-finder.");

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
    defs.put(K_ENV_MIN, new BoundedParameter.Double(K_ENV_MIN, "Envelope minimum threshold",
        "The minimum distance a point has to an ideal line/arc before it begins to penalize"
            + " a segment.", 0, 30, 8));
    defs.put(K_ENV_MAX, new BoundedParameter.Double(K_ENV_MAX, "Envelope maximum threshold",
        "The maximum distance a point has to an ideal line/arc before will cause maximum penalty"
            + " to a segment.", 0, 100, 15));
    defs.put(K_ENV_EXP, new BoundedParameter.Double(K_ENV_EXP, "Envelope penalty exponent",
        "Values that fall between the min and max envelope values are first converted to a"
            + " percentage of the total envelope size, and then exponentiated by this value."
            + " To assign greater penalize points that are only slightly painful, use values"
            + " between 0 and 1; to lessen the penalty, use the range 1..2.", 0, 2, 1));

    return defs;
  }

  private static void bug(String what) {
    Debug.out("HungrySegments2", what);
  }

  Envelope envelope;

  @Override
  public void initialize() {
    bug("HungrySegments2 is alive!");
    envelope = new Envelope(getParam(K_ENV_MIN).getDouble(), getParam(K_ENV_MAX).getDouble(),
        getParam(K_ENV_EXP).getDouble());
    main.getDrawingSurface().getSoup().addSequenceListener(this);
  }

  public void handleSequenceEvent(SequenceEvent seqEvent) {
    if (seqEvent.getType() == SequenceEvent.Type.END) {
      Sequence seq = seqEvent.getSeq();
      long startTime = System.currentTimeMillis();
      SortedSet<HungrySegment2> segments = new TreeSet<HungrySegment2>();
      for (int i = 0; i < seq.size(); i++) {
        HungrySegment2 seg = makeHungrySegment2(seq, i);
        segments.add(seg);
      }
      Animation ani = (Animation) main.getScript("Animation");
      if (ani != null) {
        ani.startAnimation(main.getDrawingSurface().getBounds(), "png");
        ani.addFrame();
      }
      // Show the initial fits (one huge jumble)
      if (ani != null) {
        animate(ani, segments, false, "Initial Fit");
      }

      HungrySegment2[] original = new HungrySegment2[segments.size()];
      original = segments.toArray(original);
      for (int i = 0; i < original.length - 1; i++) {
        HungrySegment2 source = original[i];
        for (int j = (i + 1); j < original.length; j++) {
          HungrySegment2 doomed = original[j];
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
      segments.clear();
      for (HungrySegment2 seg : original) {
        if (seg.getPathLength() > 0) {
          if (seg.isOk()) {
            seg.maybeChangeType();
            segments.add(seg);
          }
        }
      }
      // show the longest segments that dominated the others.
      if (ani != null) {
        animate(ani, segments, true, "Longest Segments");
      }

      // Locate the orphans and make new segments out of them.
      int orphanStart = -1;
      int orphanEnd = -1;
      for (int i = 0; i < seq.size(); i++) {
        if (isOrphan(i, segments)) {
          if (orphanStart < 0) {
            orphanStart = i;
            orphanEnd = i;
          } else {
            orphanEnd = i;
          }
        } else if (orphanStart >= 0) {
          HungrySegment2 seg = new HungrySegment2(seq, orphanStart, orphanEnd);
          if (seg.isOk()) {
            segments.add(seg);
            if (ani != null) {
              animate(ani, segments, true, "Found orphan segment from " + orphanStart + " to "
                  + orphanEnd);
            }
          }
          orphanStart = -1;
          orphanEnd = -1;
        }
      }

      segments = negotiate(segments);

      long endTime = System.currentTimeMillis();
      bug("Performed complete analysis in " + (endTime - startTime) + " ms");
      bug("... Showing " + segments.size() + " segments in decending order of length:");
      DrawingBuffer db = new DrawingBuffer();
      Set<Pt> junctions = new HashSet<Pt>();
      for (HungrySegment2 seg : segments) {
        bug("  " + seg.toString());
        if (seg.type == Type.Line) {
          DrawingBufferRoutines.line(db, seg.seq.get(seg.start), seg.seq.get(seg.end), Color.BLUE);
        }
        if (seg.type == Type.Arc) {
          DrawingBufferRoutines.arc(db, seg.getArc(), Color.RED);
        }
        junctions.add(seg.seq.get(seg.start));
        junctions.add(seg.seq.get(seg.end));
      }
      for (Pt pt : junctions) {
        DrawingBufferRoutines.dot(db, pt, 5, 0.5, Color.BLACK, Color.GREEN);
      }
      main.getDrawingSurface().getSoup().addBuffer(db);
    }
  }

  private boolean isOrphan(int idx, Collection<HungrySegment2> segments) {
    boolean ret = true;
    for (HungrySegment2 seg : segments) {
      if (seg.start <= idx && seg.end >= idx) {
        ret = false;
        break;
      }
    }
    return ret;
  }

  private void animate(Animation ani, Collection<HungrySegment2> segments, boolean drawEnds,
      String msg) {
    DrawingBuffer db = new DrawingBuffer();
    Set<Pt> points = new HashSet<Pt>();
    for (HungrySegment2 seg : segments) {
      if (seg.type == Type.Line) {
        DrawingBufferRoutines.line(db, seg.seq.get(seg.start), seg.seq.get(seg.end), Color.BLUE);
      } else if (seg.type == Type.Arc) {
        DrawingBufferRoutines.arc(db, seg.getArc(), Color.RED);
      }
      if (drawEnds) {
        points.add(seg.seq.get(seg.start));
        points.add(seg.seq.get(seg.end));
      }
    }
    if (drawEnds) {
      for (Pt pt : points) {
        DrawingBufferRoutines.dot(db, pt, 7.0, 1.0, Color.BLACK, Color.GREEN);
      }
    }

    if (msg != null) {
      db.up();
      db.moveTo(20, 20);
      db.down();
      db.addText(msg, Color.BLACK);
      db.up();
    }
    ani.addFrame(db, true);
  }

  private SortedSet<HungrySegment2> negotiate(Set<HungrySegment2> notInOrder) {
    double improvement = 0;
    List<HungrySegment2> segments = new ArrayList<HungrySegment2>(notInOrder);
    Collections.sort(segments, new Comparator<HungrySegment2>() {
      public int compare(HungrySegment2 o1, HungrySegment2 o2) {
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
      if (main.getScript("Animation") != null) {
        animate((Animation) main.getScript("Animation"), segments, true, "Negotiation Round "
            + round);
      }
      for (int i = 0; i < segments.size() - 1; i++) {
        improvement = 0;
        HungrySegment2 s1 = segments.get(i);
        HungrySegment2 s2 = segments.get(i + 1);
        if (s1.isOk() && s2.isOk()) {
          improvement = improvement + negotiate(s1, s2);
        }
      }
    } while (improvement > 0);
    // TODO: make sure I return a legit list...
    return new TreeSet<HungrySegment2>(segments);
  }

  private double negotiate(HungrySegment2 s1, HungrySegment2 s2) {
    double improvement = 0;
    HungrySegment2 left = s1.start < s2.start ? s1 : s2;
    HungrySegment2 right = s1.start < s2.start ? s2 : s1;
    // Consider three cases:
    // 1. Do nothing.
    // 2. Enlarge left at (possibly) the expense of right
    // 3. Enlarge right at (possibly) the expense of left
    double doNothingError = left.getStandardError() + right.getStandardError();
    double enlargeLeftError = Double.MAX_VALUE;
    HungrySegment2 llBest = null;
    HungrySegment2 rlBest = null;
    for (int i = 1; i < Math.min(5, right.numPoints()); i++) {
      HungrySegment2 ll = new HungrySegment2(left.seq, left.start, left.end + i);
      HungrySegment2 rl = new HungrySegment2(right.seq, Math.max(ll.end, right.start), right.end);
      double e = ll.getStandardError() + rl.getStandardError();
      if (e < enlargeLeftError) {
        llBest = ll;
        rlBest = rl;
        enlargeLeftError = e;
      }
    }
    double enlargeRightError = Double.MAX_VALUE;
    HungrySegment2 lrBest = null;
    HungrySegment2 rrBest = null;
    for (int i = 1; i < Math.min(5, left.numPoints()); i++) {
      HungrySegment2 rr = new HungrySegment2(right.seq, right.start - i, right.end);
      HungrySegment2 lr = new HungrySegment2(left.seq, left.start, Math.min(left.end, rr.start));
      double e = lr.getStandardError() + rr.getStandardError();
      if (e < enlargeRightError) {
        lrBest = lr;
        rrBest = rr;
        enlargeRightError = e;
      }
    }
    if (doNothingError <= enlargeLeftError && doNothingError <= enlargeRightError) {
      improvement = 0;
    } else if (enlargeLeftError <= enlargeRightError) {
      // moving to the left some amount is the best.
      left.imitate(llBest);
      right.imitate(rlBest);
      improvement = doNothingError - enlargeLeftError;
    } else {
      // moving to the right some amount is the best.
      left.imitate(lrBest);
      right.imitate(rrBest);
      improvement = doNothingError - enlargeRightError;
    }
    return improvement;
  }

  private HungrySegment2 makeHungrySegment2(Sequence seq, int i) {
    HungrySegment2 asLine = new HungrySegment2(seq, i, Type.Line);
    HungrySegment2 asArc = new HungrySegment2(seq, i, Type.Arc);
    HungrySegment2 ret = null;
    if (!asArc.isOk()) {
      ret = asLine;
    } else {
      // return whichever is longer.
      ret = asLine.getPathLength() > asArc.getPathLength() ? asLine : asArc;
    }
    return ret;
  }

  /**
   * Returns a number between 0 and 1 indicating how much 'pain' is involved in fitting a line
   * starting at i with a window size of 2k for the provided sequence. A return value of 0 means all
   * points were less than the minimum pain threshold distance.
   */
  private double measureLinePainInWindow(int i, int k, Sequence seq) {
    int lower = Math.max(0, i - k);
    int upper = Math.min(i + k, seq.size() - 1);
    return measureLinePainInLimits(lower, upper, seq);
  }

  private double measureLinePainInLimits(int lower, int upper, Sequence seq) {
    double ret = 0;
    Pt a = seq.get(lower);
    Pt b = seq.get(upper);
    Pt m = seq.get((lower + upper) / 2);

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

  private ArcPainResult measureArcPainInWindow(int i, int k, Sequence seq) {
    int lower = Math.max(0, i - k);
    int upper = Math.min(i + k, seq.size() - 1);
    return measureArcPainInLimits(lower, upper, seq);
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
      if (min >= max) {
        System.out.println("Error: " + K_ENV_MIN + " must be smaller than " + K_ENV_MAX);
        System.exit(-1);
      }
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

  class HungrySegment2 implements Comparable<HungrySegment2> {

    int id = SEGMENT_COUNTER++;
    Sequence seq;
    int start, end;
    Type type;

    /**
     * Create a segment beginning at index i of the given sequence. It will grow to be the maximum
     * possible size while incurring no 'pain' (see code for details).
     */
    public HungrySegment2(Sequence seq, int i, Type type) {
      this.seq = seq;
      this.type = type;
      int pleasantWindow = 0;
      // set the pleasant window to the largest value that is zero.
      // stop looking when pain grows larger than 1.
      for (int k = 2; k < seq.size(); k++) {
        ArcPainResult arcResult = null;
        double e = 0;
        if (type == Type.Line) {
          e = measureLinePainInWindow(i, k, seq);
        } else {
          arcResult = measureArcPainInWindow(i, k, seq);
          e = arcResult.pain;
        }

        if (e == 0) {
          pleasantWindow = k;
        }
        if (Double.isInfinite(e)) {
          // do nothing
        } else if (e >= 1) {
          break;
        }
      }
      this.start = Math.max(0, i - pleasantWindow);
      this.end = Math.min(seq.size() - 1, i + pleasantWindow);
    }

    public int numPoints() {
      return (end - start) + 1;
    }

    /**
     * Make a HungrySegment with the explicit start/end points, type, and sequence.
     */
    public HungrySegment2(Sequence seq, int start, int end) {
      this.seq = seq;
      this.start = start;
      this.end = end;
      this.type = Type.Line;
      maybeChangeType();
    }

    public void imitate(HungrySegment2 other) {
      this.seq = other.seq;
      this.start = other.start;
      this.end = other.end;
      maybeChangeType();
    }

    private void maybeChangeType() {
      Type origType = type;
      double linePain = measureLinePainInWindow(start, end, seq);
      if (linePain > 0) {
        ArcPainResult apr = measureArcPainInLimits(start, end, seq);
        if (linePain <= apr.pain) {
          this.type = Type.Line;
        } else {
          this.type = Type.Arc;
        }
      } else {
        this.type = Type.Line;
      }
    }

    public double getStandardError() {
      double ret = 0;
      if (type == Type.Line) {
        Line line = new Line(seq.get(start), seq.get(end));
        for (int i = start + 1; i < end; i++) {
          double v = Functions.getDistanceBetweenPointAndLine(seq.get(i), line);
          ret = ret + (v * v);
        }
      } else if (type == Type.Arc) {
        CircleArc arc = getArc();
        for (int i = start + 1; i < end; i++) {
          double v = Math.abs(arc.getRadius() - arc.getCenter().distance(seq.get(i)));
          ret = ret + (v * v);
        }
      }
      return ret / (end - start);
    }

    public boolean isOk() {
      boolean ret = (end - start) > 2;
      if (ret && type == Type.Arc) {
        ret = getArc() != null;
      }
      return ret;
    }

    public String toString() {
      return type + "_" + id + "[" + start + " to " + end + ", length: " + Debug.num(getPathLength())
          + ", error: " + Debug.num(getStandardError()) + "]";
    }

    public int compareTo(HungrySegment2 o) {
      int ret = 0;
      double me = getPathLength();
      double it = o.getPathLength();
      if (me > it) {
        ret = -1;
      } else if (it > me) {
        ret = 1;
      }
      return ret;
    }

    private double getPathLength() {
      return seq.getPathLength(start, end);
    }

    private double getIdealLength() {
      double ret = Double.MAX_VALUE;
      if (type == Type.Line) {
        ret = seq.get(start).distance(seq.get(end));
      } else if (type == Type.Arc) {
        ret = getArc().getArcLength();
      }
      return ret;
    }

    private CircleArc getArc() {
      ArcPainResult result = measureArcPainInLimits(start, end, seq);
      return result.arc;
    }
  }

  class ArcPainResult {
    CircleArc arc;
    double pain;
  }

}
