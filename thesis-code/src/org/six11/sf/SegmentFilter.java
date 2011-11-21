package org.six11.sf;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.six11.sf.rec.RecognizerPrimitive;
import org.six11.util.math.Interval;
import org.six11.util.pen.Functions;
import org.six11.util.pen.IntersectionData;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;

import static org.six11.util.Debug.num;
import static org.six11.util.Debug.bug;

public abstract class SegmentFilter {

  public abstract Set<Segment> filter(Set<Segment> segments);

  public static boolean hasPoint(Pt target, Segment seg) {
    return seg.getP1() == target || seg.getP2() == target;
  }

  public static SegmentFilter makeEndpointRadiusFilter(final Pt pt, final double radius) {
    SegmentFilter filter = new SegmentFilter() {
      public Set<Segment> filter(Set<Segment> segments) {
        Set<Segment> ret = new HashSet<Segment>();
        for (Segment s : segments) {
          boolean answer = false;
          if (s.getP1().distance(pt) <= radius) {
            ret.add(s);
            answer = true;
          } else if (s.getP2().distance(pt) <= radius) {
            ret.add(s);
            answer = true;
          }
          bug("are " + num(s.getP1()) + " or " + num(s.getP2()) + " within " + num(radius) + " of "
              + num(pt) + "? " + answer);
        }
        return ret;
      }
    };
    return filter;
  };

  public static SegmentFilter makeSimilarLengthFilter(final double length, final double minRatio) {
    SegmentFilter filter = new SegmentFilter() {
      public Set<Segment> filter(Set<Segment> segments) {
        Set<Segment> ret = new HashSet<Segment>();
        for (Segment s : segments) {
          double sLen = s.length();
          double ratio = Math.min(sLen, length) / Math.max(sLen, length);
          if (ratio >= minRatio) {
            ret.add(s);
          }
        }
        return ret;
      }
    };
    return filter;
  };

  public static SegmentFilter makeSimilarOrientationFilter(final Vec target,
      final double maxDeviation) {
    SegmentFilter filter = new SegmentFilter() {
      public Set<Segment> filter(Set<Segment> segments) {
        Set<Segment> ret = new HashSet<Segment>();
        for (Segment s : segments) {
          Vec segStart = s.getStartDir();
          Vec segEnd = s.getEndDir();
          double angleStart = Math.abs(Functions.getSignedAngleBetween(target, segStart));
          double angleEnd = Math.abs(Functions.getSignedAngleBetween(target, segEnd));
          if (angleStart <= maxDeviation || angleEnd <= maxDeviation) {
            ret.add(s);
          }
        }
        return ret;
      }
    };
    return filter;
  }

  public static SegmentFilter makeCohortFilter(final Collection<RecognizerPrimitive> in) {
    SegmentFilter filter = new SegmentFilter() {
      public Set<Segment> filter(Set<Segment> segments) {
        Set<Ink> avoidUs = new HashSet<Ink>();
        for (RecognizerPrimitive prim : in) {
          avoidUs.add(prim.getInk());
        }
        bug("Will avoid " + avoidUs + " inks");
        Set<Segment> ret = new HashSet<Segment>();
        for (Segment s : segments) {
          Ink segmentInk = s.getOriginalInk();
          if (!avoidUs.contains(segmentInk)) {
            ret.add(s);
          }
        }
        return ret;
      }
    };
    return filter;
  }

  public static SegmentFilter makeCoterminalFilter(final Segment reference) {
    SegmentFilter filter = new SegmentFilter() {
      public Set<Segment> filter(Set<Segment> segments) {
        Set<Segment> ret = new HashSet<Segment>();
        for (Segment seg : segments) {
          if (seg != reference
              && (hasPoint(reference.getP1(), seg) || hasPoint(reference.getP2(), seg))) {
            ret.add(seg);
          }
        }
        return ret;
      }
    };
    return filter;
  };

  /**
   * Filters segments whose midpoints are near some reference segment. The 'slop' is the deviation
   * from the candidate's midpoint expressed as a percentage of its total length. So say a candidate
   * is 300 units long and the slop is 0.1. If the reference's midpoint is within 30 units of the
   * candidate's mid, the candidate is included in the return set.
   */
  public static SegmentFilter makeMidpointFilter(final RecognizerPrimitive reference,
      final double slop) {
    SegmentFilter filter = new SegmentFilter() {
      public Set<Segment> filter(Set<Segment> segments) {
        Set<Segment> ret = new HashSet<Segment>();
        Pt refMid = Functions.getMean(reference.getP1(), reference.getP2());
        for (Segment cand : segments) {
          Pt candMid = Functions.getMean(cand.getP1(), cand.getP2());
          double radius = cand.length() * slop;
          if (refMid.distance(candMid) <= radius) {
            ret.add(cand);
          }
        }
        return ret;
      }
    };
    return filter;
  }

  public static SegmentFilter makeLengthFilter(final Interval range) {
    SegmentFilter filter = new SegmentFilter() {
      public Set<Segment> filter(Set<Segment> segments) {
        Set<Segment> ret = new HashSet<Segment>();
        for (Segment cand : segments) {
          bug("is " + num(cand.length()) + " in " + range + "? " + (range.contains(cand.length())));
          if (range.contains(cand.length())) {
            ret.add(cand);
          }
        }
        return ret;
      }
    };
    return filter;
  }

  public static SegmentFilter makeIntersectFilter(final RecognizerPrimitive prim) {
    SegmentFilter filter = new SegmentFilter() {
      public Set<Segment> filter(Set<Segment> segments) {
        Set<Segment> ret = new HashSet<Segment>();
        for (Segment cand : segments) {
          IntersectionData id = Functions.getIntersectionData(new Line(prim.getP1(), prim.getP2()),
              new Line(cand.getP1(), cand.getP2()));
          if (id.intersectsInSegments()) {
            ret.add(cand);
          }
        }
        return ret;
      }
    };
    return filter;
  }

}
