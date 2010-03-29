package org.six11.skrui.domain;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
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
    for (Primitive prim : prims) {
      if (prim instanceof LineSegment) {
        scaleAndRotateWire((LineSegment) prim, st, data);
      }
    }
  }

  private void scaleAndRotateWire(LineSegment prim, Pt st, Neanderthal data) {
    bug("Doing scale/rotate thing on " + prim + " (" + Debug.num(prim.getLength()) + " px long)");
    if (prim.getSeq().getAttribute(Neanderthal.SCRAP) != null) {
      bug("Hmm, i'm about to manipulate a scap sequence, and that isn't right.");
    }
    Pt hinge, prev;
    if (st.distance(prim.getStartPt()) < st.distance(prim.getEndPt())) {
      hinge = prim.getEndPt();
      prev = prim.getStartPt();
    } else {
      hinge = prim.getStartPt();
      prev = prim.getEndPt();
    }

    Vec toPrev = new Vec(hinge, prev);
    Vec toSt = new Vec(hinge, st);
    double scale = toSt.mag() / toPrev.mag();
    double theta = Math.atan2(toSt.getY(), toSt.getX()) - Math.atan2(toPrev.getY(), toPrev.getX());
    AffineTransform rot = Functions.getRotationInstance(hinge, theta);

    for (int i = prim.getStartIdx(); i <= prim.getEndIdx(); i++) {
      Pt pt = prim.getSeq().get(i);
      rot.transform(pt, pt);
      Vec toPt = new Vec(hinge, pt).getScaled(scale);
      pt.setLocation(hinge.getX() + toPt.getX(), hinge.getY() + toPt.getY());
    }
    data.getSoup().updateFinishedSequence(prim.getSeq());
    // if (pt != hinge && pt.getDouble("fs strength", 0) > HINGE_THRESHOLD) {
    // rot.transform(pt, pt);
    // Vec toPt = new Vec(hinge, pt).getScaled(scale);
    // pt.setLocation(hinge.getX() + toPt.getX(), hinge.getY() + toPt.getY());
    // }
    // }
    // DrawingBufferRoutines.lines(db, seq.getPoints(), Color.BLACK, 1.0);
    // DrawingBufferRoutines.flowSelectEffect(db, seq, 4.0);
    // }

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
