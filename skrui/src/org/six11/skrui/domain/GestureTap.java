package org.six11.skrui.domain;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.six11.skrui.DrawingBufferRoutines;
import org.six11.skrui.script.Dot;
import org.six11.skrui.script.LineSegment;
import org.six11.skrui.script.Neanderthal;
import org.six11.skrui.script.Primitive;
import org.six11.util.Debug;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
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
          transformNearbyWires(st, data);
          break;
        case 4:
      }
      if (n > 1) {
        data.forget(s);
      }
    }
  }

  private void transformNearbyWires(Pt st, Neanderthal data) {
    // rotate and scale primitives that have an endpoint at this structured point.
    Set<Pt> pts = data.getEndPoints().getNear(st, TAP_DISTANCE * 4);
    Set<Primitive> prims = Neanderthal.getPrimitives(pts);
    Set<Pt> nearPoints = new HashSet<Pt>();
    for (Primitive prim : prims) {
      if (prim instanceof LineSegment) {
        Pt reply = scaleAndRotateWire(prim, st);
        if (reply != null) {
          nearPoints.add(reply);
        } else {
          bug("scaleAndRotateWire returned a null point. I hope that is not a problem.");
        }
      }
    }
    bug("Now moving " + nearPoints.size() + " end points to " + Debug.num(st));
    for (Pt pt : nearPoints) {
      bug("transformed point before: " + Debug.num(pt));
      pt.setLocation(st.getX(), st.getY());
      bug("transformed point after: " + Debug.num(pt));
    }
    for (Primitive prim : prims) {
      data.getSoup().updateFinishedSequence(prim.getSeq());
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
          bug("Avoiding point: " + Debug.num(prim.getSeq().get(i)));
          continue;
        }
        Pt pt = prim.getSeq().get(i);
        rot.transform(pt, pt);
        Vec toPt = new Vec(hinge, pt).getScaled(scale);
        pt.setLocation(hinge.getX() + toPt.getX(), hinge.getY() + toPt.getY());
        bug("transformed point " + i + " of prim " + prim.getShortStr());
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
