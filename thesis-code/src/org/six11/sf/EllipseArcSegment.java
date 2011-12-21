package org.six11.sf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.RotatedEllipse;
import static org.six11.util.Debug.bug;

public class EllipseArcSegment extends Segment {

  RotatedEllipse ellie;
  private double arc1T, arc2T, arc3T;
  private int numSegments = 60;

  public EllipseArcSegment(Ink ink, List<Pt> points, boolean termA, boolean termB) {
    // The ellipse is fit using regression, and does not necessarily begin and end at the first 
    // and last points in the list. So get the elliptical region, and transform it two times so it
    // does begin and end at those points.

    ellie = Functions.createEllipse(points);
    int n = points.size();
    ellie.setArcRegion(points.get(0), points.get(n / 2), points.get(n - 1));
    List<Pt> surface = initArc();
    init(ink, surface, termA, termB, Type.EllipticalArc);
  }

  private final List<Pt> initArc() {
    Pt arc1 = ellie.getRegionPoints().get(0);
    Pt arc2 = ellie.getRegionPoints().get(1);
    Pt arc3 = ellie.getRegionPoints().get(2);
    arc1T = ellie.searchForParameter(arc1);
    arc2T = ellie.searchForParameter(arc2);
    arc3T = ellie.searchForParameter(arc3);
    List<Pt> surface = new ArrayList<Pt>();
    List<Double> arcParams = makeMonotonicallyIncreasingAngles(arc1T, arc2T, arc3T);
    double numSteps = 60;
    double start = arcParams.get(0);
    double end = arcParams.get(2);
    double step = (end - start) / numSteps;
    for (double t = start; t <= end; t += step) {
      surface.add(ellie.getEllipticalPoint(t));
    }
    return surface;
  }

  /**
   * Given some input points that are on the ellipse surface, return a list of angles (in radians)
   * that increase. The input points may be on different sides of the 0-degree line, so for example
   * your points might be at 10, 350, and 320. This means the ellipse arc starts just above the
   * 0-degree line and moves clockwise. The return list of angles would be 320, 350, 370. (370 is
   * equivalent to 10 degrees plus a full rotation of the circle).
   * 
   * @param regionPoints
   *          a list of exactly three points that are on the ellipse arc surface.
   * @return three doubles representing the increasing angles, in radians. If the arc passes through
   *         the 0-degree line, some of the values will be greater than 2*pi.
   */
  private List<Double> makeMonotonicallyIncreasingAngles(double t1, double t2, double t3) {
    double twoPi = Math.PI * 2;
    StringBuilder buf = new StringBuilder();
    buf.append(getAngleCode(t1, t2)); // a to b
    buf.append(getAngleCode(t2, t3)); // b to c
    buf.append(getAngleCode(t1, t3)); // a to c
    String code = buf.toString();
    bug("Code: " + code);
    // there are eight possible values for the angle code. +++, ++-, +-+, etc.
    // two aren't possible (++- and --+).
    // two others don't require adjustment (+++ and ---).
    // the remaining four require us to add 2pi to one or two values.
    if (code.equals("+-+")) {
      t1 = t1 + twoPi; // adjust a
    }
    if (code.equals("+--")) {
      t3 = t3 + twoPi; // adjust c
    }
    if (code.equals("-++")) {
      t1 = t1 + twoPi; // adjust a and b
      t2 = t2 + twoPi;
    }
    if (code.equals("-+-")) {
      t2 = t2 + twoPi; // adjust b and c
      t3 = t3 + twoPi;
    }
    if (code.equals("++-") || code.equals("--+")) {
      bug("Ugh, encountered an 'impossible' code.");
    }
    buf.setLength(0);
    buf.append(getAngleCode(t1, t2)); // a to b
    buf.append(getAngleCode(t2, t3)); // b to c
    buf.append(getAngleCode(t1, t3)); // a to c
    code = buf.toString();
    List<Double> angles = new ArrayList<Double>();
    angles.add(t1);
    angles.add(t2);
    angles.add(t3);
    if (code.equals("---")) {
      Collections.reverse(angles);
    }
    return angles;
  }

  private char getAngleCode(double m, double n) {
    char ret = '+'; // non-decreasing: m < n
    if (m > n) {
      ret = '-';
    }
    return ret;
  }
}
