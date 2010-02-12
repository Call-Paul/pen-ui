package org.six11.skrui;

import java.util.Comparator;

import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;

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

}
