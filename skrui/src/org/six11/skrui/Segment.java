package org.six11.skrui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.six11.util.Debug;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

/**
 * Represents a portion of a drawn stroke that might (or might not) be a visually distinct
 * component. This implementation can represent straight lines and arcs (of circles).
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Segment implements Comparable<Segment> {
  String label;
  Pt start, end;
  int idxStart, idxEnd;
  double errorLine, errorCircle;
  Sequence seq;
  CircleArc bestCircle;

  // List<Pt> splineControlPoints;

  public static enum Type {
    LINE, ARC
  }

  public Segment(Pt start, Pt end, Sequence seq) {
    this.start = start;
    this.end = end;
    this.seq = seq;
    this.idxStart = seq.indexOf(start);
    this.idxEnd = seq.indexOf(end);
    this.errorLine = calculateLineError();
    this.errorCircle = calculateCircleError();
  }

  public double getBestError(double lineRatio) {
    double ret = Double.POSITIVE_INFINITY;
    switch (getBestType(lineRatio)) {
      case LINE:
        ret = errorLine;
        break;
      case ARC:
        ret = errorCircle;
        break;
    }
    return ret;
  }

  public Type getBestType(double lineRatio) {
    if (isProbablyLine(lineRatio)) {
      return Type.LINE;
    }
    if (errorLine < errorCircle) {
      return Type.LINE;
    } else {
      return Type.ARC;
    }
  }

  public double getErrorLine() {
    return errorLine;
  }

  public double getErrorCircle() {
    return errorCircle;
  }

  /**
   * Return the curvilinear distance of this segment.
   */
  double length() {
    return end.getDouble("curvilinear-distance") - start.getDouble("curvilinear-distance");
  }

  /**
   * Compares based on length().
   */
  public int compareTo(Segment other) {
    return orderByLength.compare(this, other);
  }

  public static Comparator<Segment> orderByLength = new Comparator<Segment>() {
    public int compare(Segment s1, Segment s2) {
      int ret = 0;
      if (s1.length() < s2.length()) {
        ret = -1;
      } else if (s1.length() > s2.length()) {
        ret = 1;
      }
      return ret;
    }
  };

  public static Comparator<Segment> orderByPoints = new Comparator<Segment>() {
    public int compare(Segment s1, Segment s2) {
      int ret = 0;
      if (s1.start.getTime() < s2.start.getTime()) {
        ret = -1;
      } else if (s1.start.getTime() > s2.start.getTime()) {
        ret = 1;
      }
      return ret;
    }
  };

  public boolean isProbablyLine(double threshold) {
    double euclideanDistance = end.distance(start);
    double curvilinearDistance = seq.getPathLength(idxStart, idxEnd);
    return (euclideanDistance / curvilinearDistance) > threshold;
  }

  private double calculateLineError() {
    double ret = 0.0;
    Line line = new Line(start, end);
    for (int i = idxStart; i <= idxEnd; i++) {
      double dist = Functions.getDistanceBetweenPointAndLine(seq.get(i), line);
      ret += dist * dist;
    }
    return ret / (idxEnd - idxStart);
  }

  private double calculateCircleError() {
    double errorSum = 0.0;
    List<CircleArc> arcs = new ArrayList<CircleArc>();
    for (int i = idxStart + 1; i < idxEnd; i++) {
      CircleArc ca = new CircleArc(start, seq.get(i), end);
      arcs.add(ca);
    }
    Collections.sort(arcs, CircleArc.comparator); // sort based on radius
    bestCircle = arcs.get(arcs.size() / 2); // get the arc with median radius
    if (bestCircle.center == null) {
      errorSum = Double.POSITIVE_INFINITY;
    } else {
      for (int i = idxStart; i < idxEnd; i++) {
        Pt pt = seq.get(i);
        double r1 = bestCircle.center.distance(pt);
        double d = r1 - bestCircle.radius;
        errorSum += d * d;
      }
    }
    return errorSum / (idxEnd - idxStart);
  }

  public String toString() {
    int idxA = seq.indexOf(start);
    int idxB = seq.indexOf(end);
    return "Segment " + (label == null ? "" : "[" + label + "]") + " " + idxA + ", " + idxB
        + ", length = " + Debug.num(length()) + ", line error: " + Debug.num(getErrorLine())
        + ", errorCircle: " + Debug.num(getErrorCircle());
  }

  @SuppressWarnings("unused")
  private static void bug(String what) {
    Debug.out("Segment", what);
  }
}