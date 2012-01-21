package org.six11.sf.rec;

import java.awt.Color;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import org.six11.sf.DrawingBufferLayers;
import org.six11.sf.Ink;
import org.six11.sf.Segment;
import org.six11.sf.SegmentFilter;
import org.six11.sf.SketchBook;
import org.six11.sf.constr.RightAngleUserConstraint;
import org.six11.sf.constr.UserConstraint;
import org.six11.sf.rec.RecognizerPrimitive.Certainty;
import org.six11.util.math.Interval;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;
import org.six11.util.solve.Constraint;
import org.six11.util.solve.NumericValue;
import org.six11.util.solve.OrientationConstraint;

import static java.lang.Math.toRadians;
import static java.lang.Math.toDegrees;
import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

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
    if (line1 != null && line2 != null) {
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
    segs = SegmentFilter.makeEndpointRadiusFilter(hotspot, 30).filter(segs);
    Interval adjacentSegAngleRange = new Interval(toRadians(70), toRadians(110));
    Set<Segment> avoid = new HashSet<Segment>();
    Segment good1 = null;
    Segment good2 = null;
    double bestAngle = 0;
    for (Segment seg : segs) {
      Set<Segment> adjacentSegs = SegmentFilter.makeCoterminalFilter(seg).filter(segs);
      for (Segment adjacentSeg : adjacentSegs) {
        if (!avoid.contains(adjacentSeg)) {
          double ang = adjacentSeg.getMinAngle(seg);
          if (toRadians(90) - ang < toRadians(90) - bestAngle
              && adjacentSegAngleRange.contains(ang)) {
            avoid.add(seg);
            good1 = seg;
            good2 = adjacentSeg;
            bestAngle = ang;
          }
        }
      }
    }
    if (good1 != null && good2 != null) {
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
    OrientationConstraint rightAngleConstraint = new OrientationConstraint(s1.getP1(), s1.getP2(),
        s2.getP1(), s2.getP2(), new NumericValue(Math.toRadians(90)));
    rightAngleConstraint.setSecretName(NAME);
    UserConstraint uc = makeUserConstraint(rightAngleConstraint);
    for (Ink eenk : item.getInk()) {
      model.removeRelated(eenk);
    }
    model.addUserConstraint(uc);
  }

  private UserConstraint makeUserConstraint(OrientationConstraint rightAngleConstraint) {
    UserConstraint ret = new RightAngleUserConstraint(rightAngleConstraint);
    return ret;
  }

  public void draw(Constraint c, RecognizedItem item, DrawingBuffer buf, Pt hoverPoint) {
    if (hoverPoint != null) {
      Pt fulcrum = null;
      Pt left = null;
      Pt right = null;
      Segment s1 = item.getSegmentTarget(RightAngleBrace.TARGET_A);
      Segment s2 = item.getSegmentTarget(RightAngleBrace.TARGET_B);
      if (s1.getP1() == s2.getP1()) {
        fulcrum = s1.getP1();
        left = s1.getP2();
        right = s2.getP2();
      } else if (s1.getP1() == s2.getP2()) {
        fulcrum = s1.getP1();
        left = s1.getP2();
        right = s2.getP1();
      } else if (s1.getP2() == s2.getP1()) {
        fulcrum = s1.getP2();
        left = s1.getP1();
        right = s2.getP2();
      } else if (s1.getP2() == s2.getP2()) {
        fulcrum = s1.getP2();
        left = s1.getP1();
        right = s2.getP1();
      }
      if (fulcrum == null || left == null || right == null) {
        // do nothing
      } else {
        Vec leftV = new Vec(fulcrum, left).getUnitVector();
        Vec rightV = new Vec(fulcrum, right).getUnitVector();
        Vec diagonal = Vec.sum(leftV, rightV).getUnitVector();
        double root2 = Math.sqrt(2);
        double braceLen = 16;
        Pt braceCorner = fulcrum.getTranslated(diagonal, root2 * braceLen);
        Pt braceLeft = fulcrum.getTranslated(leftV, braceLen);
        Pt braceRight = fulcrum.getTranslated(rightV, braceLen);
        List<Pt> points = new ArrayList<Pt>();
        points.add(braceLeft);
        points.add(braceCorner);
        points.add(braceRight);
        double alpha = DrawingBufferLayers.getAlpha(fulcrum.distance(hoverPoint), 10, 80, 0.1);
        Color color = new Color(1, 0, 0, (float) alpha);
        DrawingBufferRoutines.lines(buf, points, color, 1.0);
      }
    }
  }

  public static Collection<RecognizedItem> resolveConflictSameLengthGesture(RecognizedItem itemA,
      RecognizedItem itemB) {
    Collection<RecognizedItem> doomed = new HashSet<RecognizedItem>();
    bug("Resolving conflict between...");
    bug(itemA.getTemplate().getName() + ": " + num(itemA.getCertainties()));
    bug(" ... and ...");
    bug(itemB.getTemplate().getName() + ": " + num(itemB.getCertainties()));
    if (itemA.getTemplate().getName().equals(RightAngleBrace.NAME)) {
      doomed.add(itemB);
    } else {
      doomed.add(itemA);
    }
    return doomed;
  }

}
