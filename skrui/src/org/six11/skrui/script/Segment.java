package org.six11.skrui.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.six11.skrui.script.Neanderthal.Certainty;
import org.six11.skrui.script.Polyline.Type;
import org.six11.util.pen.CircleArc;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

public class Segment implements Comparable<Segment> {
  int start, end;
  CircleArc bestCircle;
  Type type;
  Certainty lineCertainty, arcCertainty;
  Sequence seq;

  public Segment(int start, int end, Sequence seq) {
    this.start = start;
    this.end = end;
    this.lineCertainty = Certainty.Unknown;
    this.arcCertainty = Certainty.Unknown;
    this.seq = seq;
    List<CircleArc> arcs = new ArrayList<CircleArc>();
    for (int i = start + 1; i < end; i++) {
      CircleArc ca = new CircleArc(seq.get(start), seq.get(i), seq.get(end));
      arcs.add(ca);
    }
    Collections.sort(arcs, CircleArc.comparator); // sort based on radius
    if (arcs.size() > 1) {
      bestCircle = arcs.get(arcs.size() / 2); // get the arc with median radius
    } else if (arcs.size() == 1) {
      bestCircle = arcs.get(0);
    } else {
      bestCircle = null; // hmm?
    }
    type = getLikelyType();
  }

  public Pt getStartPoint() {
    return seq.get(start);
  }

  public Pt getEndPoint() {
    return seq.get(end);
  }

  private Type getLikelyType() {
    Type ret = null;
    boolean probablyLine = isProbablyLine();
    if (probablyLine) {
      ret = Type.Line;
    } else {
      double le = getLineError();
      double ae = getArcError();
      if (le < ae) {
        ret = Type.Line;
      } else {
        ret = Type.Arc;
      }
    }
    return ret;
  }

  public boolean isProbablyLine() {
    double euclideanDistance = getLineLength();
    double curvilinearDistance = getPathLength();
    return (euclideanDistance / curvilinearDistance) > 0.90;
  }

  public double getArcLength() {
    double ret = Double.POSITIVE_INFINITY;
    if (bestCircle != null) {
      ret = bestCircle.getArcLength();
    }
    return ret;
  }

  public double getLineLength() {
    return seq.get(start).distance(seq.get(end));
  }

  public double getLength() {
    double ret = 0;
    if (type == null || type == Type.Line) {
      ret = getLineLength();
    } else {
      ret = getArcLength();
    }
    return ret;
  }

  public double getArcErrorSum() {
    double ret = Double.POSITIVE_INFINITY;
    if (bestCircle != null && bestCircle.isValid()) {
      double sum = 0;
      double r = bestCircle.getRadius();
      Pt c = bestCircle.getCenter();
      for (int i = start; i <= end; i++) {
        Pt pt = seq.get(i);
        double d = pt.distance(c) - r;
        sum += d * d;
      }
      ret = sum;
    }
    return ret;
  }
  
  public double getArcError() {
    if (bestCircle != null && bestCircle.isValid()) {
      return getArcErrorSum() / getNumPoints();
    } else {
      return Double.POSITIVE_INFINITY;
    }
  }

  /**
   * Returns the sum of squared error between each point and the idealized line from start to end.
   */
  public double getLineErrorSum() {
    double sum = 0;
    Line line = new Line(seq.get(start), seq.get(end));
    for (int i = start; i <= end; i++) {
      Pt pt = seq.get(i);
      double d = Functions.getDistanceBetweenPointAndLine(pt, line);
      sum = sum + d * d;
    }
    return sum;
  }

  public double getLineError() {
    return getLineErrorSum() / getNumPoints();
  }

  public int getNumPoints() {
    return end - start;
  }

  public double getError() {
    double ret = 0;
    if (type == null || type == Type.Line) {
      ret = getLineError();
    } else {
      ret = getArcError();
    }
    return ret;
  }

  /**
   * Compares based on length().
   */
  public int compareTo(Segment other) {
    return orderByLength.compare(this, other);
  }

  public double getPathLength() {
    return seq.getPathLength(start, end);
  }
  
  public final static Comparator<Segment> orderByLength = new Comparator<Segment>() {
    public int compare(Segment s1, Segment s2) {
      int ret = 0;
      if (s1.getLength() < s2.getLength()) {
        ret = -1;
      } else if (s1.getLength() > s2.getLength()) {
        ret = 1;
      }
      return ret;
    }
  };

  public final static Comparator<Segment> orderByPoints = new Comparator<Segment>() {
    public int compare(Segment s1, Segment s2) {
      int ret = 0;
      if (s1.getStartPoint().getTime() < s2.getStartPoint().getTime()) {
        ret = -1;
      } else if (s1.getStartPoint().getTime() > s2.getStartPoint().getTime()) {
        ret = 1;
      }
      return ret;
    }
  };
}