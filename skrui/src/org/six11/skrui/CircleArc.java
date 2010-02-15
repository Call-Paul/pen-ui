package org.six11.skrui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.six11.util.Debug;
import org.six11.util.gui.shape.ShapeFactory.ArcData;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class CircleArc implements Comparable<CircleArc> {

  public static Comparator<CircleArc> comparator = new Comparator<CircleArc>() {
    public int compare(CircleArc o1, CircleArc o2) {
      return o1.compareTo(o2);
    }
  };

  double radius;

  Pt start;
  Pt mid;
  Pt end;
  Pt center; // can be null in the event of colinear input points.

  public CircleArc(Pt start, Pt mid, Pt end) {
    this.start = start;
    this.mid = mid;
    this.end = end;
    this.center = Functions.getCircleCenter(start, mid, end);
    if (center == null) {
      this.radius = Double.POSITIVE_INFINITY;
    } else {
      this.radius = mid.distance(center);
    }
  }

  public int compareTo(CircleArc o) {
    int ret = 0;
    if (radius < o.radius) {
      ret = -1;
    } else if (radius > o.radius) {
      ret = 1;
    }
    return ret;
  }

  public Pt getCenter() {
    return center;
  }

  public double getRadius() {
    return radius;
  }

  public double getArcLength() {
    ArcData data = new ArcData(start, mid, end);
    double ret = (2 * Math.PI) / Math.toRadians(Math.abs(data.extent));
    bug("Arc extent is " + Debug.num(data.extent) + " degrees, radius is: "
        + Debug.num(data.radius) + ", so arc length is " + Debug.num(ret));
    return ret;
  }

  public static void bug(String what) {
    Debug.out("CircleArc", what);
  }
  
  public static CircleArc makeBestCircle(int start, int end, Sequence seq) {
    CircleArc ret = null;
    List<CircleArc> arcs = new ArrayList<CircleArc>();
    for (int j = start + 1; j < end; j++) {
      CircleArc ca = new CircleArc(seq.get(start), seq.get(j), seq.get(end));
      if (!Double.isInfinite(ca.getRadius())) {
        arcs.add(ca);
      }
    }
//    Collections.sort(arcs, CircleArc.comparator); // sort based on radius
//    if (arcs.size() > 0) {
//      ret = arcs.get(arcs.size() / 2);
//    }
    double lowestError = Double.MAX_VALUE;
    for (CircleArc arc : arcs) {
      double error = 0;
      for (int i = start; i <= end; i++) {
        error += arc.center.distance(seq.get(i));
      }
      if (error < lowestError) {
        lowestError = error;
        ret = arc;
      }
    }
    return ret;
  }
}
