package org.six11.sf.rec;

import java.awt.geom.Area;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import org.six11.sf.Segment;
import org.six11.sf.SegmentFilter;
import org.six11.sf.SketchBook;
import org.six11.sf.rec.RecognizerPrimitive.Certainty;
import org.six11.sf.rec.RecognizerPrimitive.Type;
import org.six11.util.math.Interval;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;
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

  public RightAngleBrace(SketchBook model) {
    super(model, "RightAngleBrace");
    addPrimitive("line1", Type.Line);
    addPrimitive("line2", Type.Line);
    addConstraint(new Coincident("c1", "line1.p2", "line2.p1"));
    addConstraint(new EqualLength("c2", "line1", "line2"));
    //    addConstraint(new RightAngle("c3", "line1", "line2"));
    // the 'normal' range for RightAngle is too strict.
    addConstraint(new AngleConstraint("c3", 
        new Interval(toRadians(82), toRadians(98)), new Interval(toRadians(70),
        toRadians(110)), "line1", "line2"));
    setDebugAll(true);
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
    model.getConstraints().addConstraint(
        new OrientationConstraint(s1.getP1(), s1.getP2(), s2.getP1(), s2.getP2(),
            new NumericValue(Math.toRadians(90))));
    for (RecognizerPrimitive prim : item.getSubshapes()) {
      model.removeRelated(prim.getInk());
    }    
  }

}