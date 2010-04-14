package org.six11.skrui.domain;

import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.six11.skrui.shape.Dot;
import org.six11.skrui.shape.LineSegment;
import org.six11.skrui.script.Neanderthal;
import org.six11.skrui.shape.Primitive;
import org.six11.skrui.script.Neanderthal.Certainty;
import org.six11.util.Debug;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class GestureTap extends GestureShapeTemplate {

  public static double TAP_DISTANCE = 10;
  public static long TAP_TIMEOUT = 2000; // minimum time to tap again to find a double-tap

  public GestureTap(Domain domain) {
    super(domain, "Gesture Tap");
    addPrimitive("dot", Dot.class);
    resetValid(true);
  }

  public void trigger(Neanderthal data, Shape s, List<Primitive> matches) {
    // get structured points near the dot.
    Pt spot = matches.get(0).getStartPt();
    long time = spot.getTime();
    Set<Pt> struct = data.getStructurePoints().getNear(spot, TAP_DISTANCE);
    for (Pt st : struct) {
      addTap(st, time);
      int n = countTaps(st, time);
      switch (n) {
        case 1:
          updateTimeStamp(st, data, time);
          break;
        case 2:
          transformNearbyWires(st, data);
          break;
        case 3:
          rectifyNearbyWires(st, data);
          break;
        case 4:
          structureNearbyWires(st, data);
          break;
      }
      if (n > 1) {
        data.forget(s);
      }
    }
  }

  private void structureNearbyWires(Pt st, Neanderthal data) {
    Set<Pt> pts = data.getAllPoints().getNear(st, TAP_DISTANCE * 4);
    Set<Primitive> prims = Neanderthal.getPrimitives(pts);
    for (Primitive prim : prims) {
      if (prim instanceof LineSegment && prim.getCert() == Certainty.Yes) {
        data.addStructureLine((LineSegment) prim);
        data.forget(prim); // yes, massive bug.
        // shouldn't remove sequence entirely because it might have non-structure primitives.
        bug("Made a structural line: " + prim.getShortStr());
      }
    }
  }

  private void rectifyNearbyWires(Pt st, Neanderthal data) {
    Set<Pt> pts = data.getEndPoints().getNear(st, TAP_DISTANCE * 4);
    Set<Primitive> endPrims = Neanderthal.getPrimitives(pts);
    for (Primitive prim : endPrims) {
      if (prim instanceof LineSegment && prim.getCert() == Certainty.Yes) {
        rectifyLine((LineSegment) prim);
        data.getMain().updateFinishedSequence(prim.getSeq());
        bug("Rectified line: " + prim.getShortStr());
      }
    }
    // rectify primitives that are near the structured point so they pass through it. Exclude those
    // already straightened.
    pts = data.getAllPoints().getNear(st, TAP_DISTANCE * 4);
    Set<Primitive> nearPrims = Neanderthal.getPrimitives(pts);
    nearPrims.removeAll(endPrims);
    for (Primitive prim : nearPrims) {
      if (prim instanceof LineSegment && prim.getCert() == Certainty.Yes) {
        rectifyLine((LineSegment) prim);
        data.getMain().updateFinishedSequence(prim.getSeq());
        bug("Rectified line: " + prim.getShortStr());
      }
    }
  }

  private void rectifyLine(LineSegment prim) {
    Line ideal = prim.getGeometryLine();
    Vec vec = new Vec(ideal.getStart(), ideal.getEnd());
    double x = ideal.getStart().getX();
    double y = ideal.getStart().getY();
    double length = ideal.getLength();
    int n = prim.getEndIdx() - prim.getStartIdx(); // n is number of segments. n+1 number of points.
    double incr = length / n;
    for (int i = prim.getStartIdx() + 1; i < prim.getEndIdx(); i++) { // no need to move end points
      int j = i - prim.getStartIdx();
      Vec offset = vec.getVectorOfMagnitude(j * incr);
      prim.getSeq().get(i).setLocation(x + offset.getX(), y + offset.getY());
    }

  }

  private void transformNearbyWires(Pt st, Neanderthal data) {
    // rotate and scale primitives that have an endpoint at this structured point.
    Set<Pt> pts = data.getEndPoints().getNear(st, TAP_DISTANCE * 4);
    Set<Primitive> endPrims = Neanderthal.getPrimitives(pts);
    Set<Pt> nearPoints = new HashSet<Pt>();
    for (Primitive prim : endPrims) {
      // since the same points might be part of arcs and lines, we have to be careful about
      // not moving the same point twice. The following code does not constitute 'careful', but
      // it does constitute 'works for now'.
      if (prim instanceof LineSegment) {
        Pt reply = scaleAndRotateWire(prim, st);
        if (reply != null) {
          nearPoints.add(reply);
        } else {
          bug("scaleAndRotateWire returned a null point. I hope that is not a problem.");
        }
      }
    }
    for (Pt pt : nearPoints) {
      pt.setLocation(st.getX(), st.getY());
    }
    for (Primitive prim : endPrims) {
      data.getMain().updateFinishedSequence(prim.getSeq());
    }

    // move primitives that are near the structured point so they pass through it. Exclude those
    // already modified.
    pts = data.getAllPoints().getNear(st, TAP_DISTANCE * 4);
    Set<Primitive> nearPrims = Neanderthal.getPrimitives(pts);
    nearPrims.removeAll(endPrims);
    for (Primitive prim : nearPrims) {
      moveWire(prim, st);
      data.getMain().updateFinishedSequence(prim.getSeq());
    }
  }

  private void moveWire(Primitive prim, Pt st) {
    // Find the point on prim that is closest to structure point, and move all points in the
    // sequence by the vector formed between them.
    double dist = Double.MAX_VALUE;
    int idxClosest = -1;
    for (int i = prim.getStartIdx(); i <= prim.getEndIdx(); i++) {
      Pt pt = prim.getSeq().get(i);
      double thisDist = pt.distance(st);
      if (thisDist < dist) {
        idxClosest = i;
        dist = thisDist;
      }
    }
    if (idxClosest >= 0) {
      Pt closest = prim.getSeq().get(idxClosest);
      Vec toSt = new Vec(closest, st);
      for (int i = prim.getStartIdx(); i <= prim.getEndIdx(); i++) {
        Pt here = prim.getSeq().get(i);
        here.setLocation(here.getX() + toSt.getX(), here.getY() + toSt.getY());
      }
    }
  }

  /**
   * If the primitive doesn't end/begin on the given point, the primitive is rotated and scaled so
   * that the nearer endpoint matches st.
   */
  private Pt scaleAndRotateWire(Primitive prim, Pt st) {
    Pt ret = null;
    bug("Doing scale/rotate thing on " + prim + " (" + Debug.num(prim.getLength()) + " px long)");
    if (prim.getSeq().getAttribute(Neanderthal.SCRAP) != null) {
      bug("Hmm, i'm about to manipulate a scrap sequence, and that isn't right.");
    }
    if ((st.distance(prim.getStartPt()) > 0 && st.distance(prim.getEndPt()) > 0)) {
      Pt hinge, prev;
      int idxPrev;
      if (st.distance(prim.getStartPt()) < st.distance(prim.getEndPt())) {
        hinge = prim.getEndPt();
        prev = prim.getStartPt();
        idxPrev = prim.getStartIdx();
      } else {
        hinge = prim.getStartPt();
        prev = prim.getEndPt();
        idxPrev = prim.getEndIdx();
      }
      ret = prev;
      Vec toPrev = new Vec(hinge, prev);
      Vec toSt = new Vec(hinge, st);
      double scale = toSt.mag() / toPrev.mag();
      double theta = Math.atan2(toSt.getY(), toSt.getX())
          - Math.atan2(toPrev.getY(), toPrev.getX());
      AffineTransform rot = Functions.getRotationInstance(hinge, theta);

      for (int i = prim.getStartIdx(); i <= prim.getEndIdx(); i++) {
        if (i == idxPrev) {
          continue;
        }
        Pt pt = prim.getSeq().get(i);
        rot.transform(pt, pt);
        Vec toPt = new Vec(hinge, pt).getScaled(scale);
        pt.setLocation(hinge.getX() + toPt.getX(), hinge.getY() + toPt.getY());
      }

    }
    return ret;
  }

  private void updateTimeStamp(Pt st, Neanderthal data, long time) {
    // update the point's timestamp so it becomes recent
    data.getStructurePoints().remove(st);
    st.setTime(time);
    data.getStructurePoints().add(st);
  }

  @SuppressWarnings("unchecked")
  private int countTaps(Pt st, long time) {
    int ret = 0;
    List<Long> tapTimes = (List<Long>) st.getList("taps");
    int lastI = -1;
    int round = 1;
    for (int i = tapTimes.size() - 1; i >= 0; i--) {
      long here = tapTimes.get(i);
      if ((time - here) < (TAP_TIMEOUT * round)) {
        ret++;
      } else {
        lastI = i; // indexes zero through lastI are all out of date.
      }
      round++;
    }
    for (int i = 0; i <= lastI; i++) {
      tapTimes.remove(0);
    }
    return ret;
  }

  @SuppressWarnings("unchecked")
  private void addTap(Pt st, long time) {
    if (st.getList("taps") == null) {
      st.setList("taps", new ArrayList<Long>());
    }
    ((List<Long>) st.getList("taps")).add(time);
  }

  private static void bug(String what) {
    Debug.out("GestureTap", what);
  }

}
