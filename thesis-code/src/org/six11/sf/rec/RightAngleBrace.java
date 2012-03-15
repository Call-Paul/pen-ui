package org.six11.sf.rec;

import static java.lang.Math.toRadians;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import org.six11.sf.Ink;
import org.six11.sf.Segment;
import org.six11.sf.SegmentFilter;
import org.six11.sf.SketchBook;
import org.six11.sf.constr.RightAngleUserConstraint;
import org.six11.sf.constr.UserConstraint;
import org.six11.sf.rec.RecognizerPrimitive.Certainty;
import org.six11.util.math.Interval;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;

public class RightAngleBrace extends RecognizedItemTemplate {

  // this is the geometry for the recognized item:
  //
  // B--------A
  //          |
  //          |
  //          |
  // D        C
  public static String CORNER_A = "cornerA"; // this is the corner
  public static String CORNER_B = "cornerB";
  public static String CORNER_C = "cornerC";
  public static String CORNER_D = "cornerD"; // this is opposite of the corner

  public static String TARGET_A = "targetA";
  public static String TARGET_B = "targetB";

  public static String NAME = "RightAngleBrace";

  public RightAngleBrace(SketchBook model) {
    super(model, NAME);
    addPrimitive("line1", RecognizerPrimitive.Type.Line);
    addPrimitive("line2", RecognizerPrimitive.Type.Line);
    Interval yesInterval = new Interval(0, 30);
    Interval maybeInterval = new Interval(0, 50);
    addConstraint(new Coincident("c1", "line1.p2", "line2.p1"));
    addConstraint(new EqualLength("c2", "line1", "line2"));
    addConstraint(new AngleConstraint("c3", new Interval(toRadians(82), toRadians(98)),
        new Interval(toRadians(70), toRadians(110)), "line1", "line2"));
    addConstraint(new LineLengthConstraint("c4", yesInterval, maybeInterval, "line1"));
    addConstraint(new LineLengthConstraint("c5", yesInterval, maybeInterval, "line2"));
  }

  public RecognizedItem makeItem(Stack<String> slots, Stack<RecognizerPrimitive> prims) {
    RecognizedItem item = new RecognizedItem(this, slots, prims);
    RecognizerPrimitive line1 = search(slots, prims, "line1");
    RecognizerPrimitive line2 = search(slots, prims, "line2");
    if ((line1 != null) && (line2 != null)) {
      Pt a = line1.getSubshape("p2");
      Pt b = line1.getSubshape("p1");
      Pt c = line2.getSubshape("p2");
      Vec v1 = new Vec(a, b);
      Vec v2 = new Vec(a, c);
      Pt d = new Pt(a.getX() + v1.getX() + v2.getX(), a.getY() + v1.getY() + v2.getY());
      item.setFeaturedPoint(CORNER_A, a);
      item.setFeaturedPoint(CORNER_B, b);
      item.setFeaturedPoint(CORNER_C, c);
      item.setFeaturedPoint(CORNER_D, d);
    }
    return item;
  }

  public Certainty checkContext(RecognizedItem item, Collection<RecognizerPrimitive> in) {
    Certainty ret = Certainty.No;
    Set<Segment> segs = model.getGeometry();
    Pt hotspot = item.getFeaturePoint(CORNER_D);
    segs = SegmentFilter.makeCohortFilter(in).filter(segs);
    segs = SegmentFilter.makeSegmentTypeFilter(Segment.Type.Line).filter(segs);
    segs = SegmentFilter.makeEndpointRadiusFilter(hotspot, 30).filter(segs);
    Interval adjacentSegAngleRange = new Interval(toRadians(50), toRadians(130));
    Set<Segment> avoid = new HashSet<Segment>();
    Segment good1 = null;
    Segment good2 = null;
    double bestDist = Double.MAX_VALUE;
    for (Segment seg : segs) {
      Set<Segment> adjacentSegs = SegmentFilter.makeCoterminalFilter(seg).filter(segs);
      for (Segment adjacentSeg : adjacentSegs) {
        if (!avoid.contains(adjacentSeg)) {
          if (adjacentSegAngleRange.contains(adjacentSeg.getMinAngle(seg))) {
            Collection<Pt> latches = seg.getLatchPoints(adjacentSeg);
            for (Pt latch : latches) {
              double thisDist = latch.distance(hotspot);
              if (thisDist < bestDist) {
                bestDist = thisDist;
                good1 = seg;
                good2 = adjacentSeg;
              }
            }
          }
        }
      }
    }
    if ((good1 != null) && (good2 != null)) {
      item.addTarget(RightAngleBrace.TARGET_A, good1);
      item.addTarget(RightAngleBrace.TARGET_B, good2);
      ret = Certainty.Yes;
    }
    return ret;
  }

  @Override
  public void create(RecognizedItem item, SketchBook model) {
    Segment s1 = item.getSegmentTarget(RightAngleBrace.TARGET_A);
    Segment s2 = item.getSegmentTarget(RightAngleBrace.TARGET_B);
    UserConstraint uc = new RightAngleUserConstraint(model, s1.getP1(), s1.getP2(), s2.getP1(),
        s2.getP2());
    for (Ink eenk : item.getInk()) {
      model.removeRelated(eenk);
    }
    model.addUserConstraint(uc);
  }

  public static Collection<RecognizedItem> resolveConflictSameLengthGesture(RecognizedItem itemA,
      RecognizedItem itemB) {
    Collection<RecognizedItem> doomed = new HashSet<RecognizedItem>();
    String nameA = itemA.getTemplate().getName();
    String nameB = itemB.getTemplate().getName();
    Collection<Ink> strokesA = itemA.getStrokes();
    Collection<Ink> strokesB = itemB.getStrokes();
    // right-angle gesture with 1 stroke == win
    // ... otherwise ....
    // same-length gesture with 2 strokes == win
    // otherwise just pick the right-angle brace because I don't know wtf is going on
    if (nameA.equals(RightAngleBrace.NAME) && (strokesA.size() == 1)) {
      doomed.add(itemB);
    } else if (nameB.equals(RightAngleBrace.NAME) && (strokesB.size() == 1)) {
      doomed.add(itemA);
    } else if (nameA.equals(SameLengthGesture.NAME) && (strokesA.size() == 2)) {
      doomed.add(itemB);
    } else if (nameB.equals(SameLengthGesture.NAME) && (strokesB.size() == 2)) {
      doomed.add(itemA);
    } else if (nameA.equals(RightAngleBrace.NAME)) {
      doomed.add(itemB);
    } else {
      doomed.add(itemA);
    }
    return doomed;
  }

}
