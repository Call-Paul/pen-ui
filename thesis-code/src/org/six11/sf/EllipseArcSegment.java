package org.six11.sf;

import java.awt.Color;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.six11.util.gui.shape.ShapeFactory;
import org.six11.util.gui.shape.ShapeFactory.RotatedEllipseShape;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.RotatedEllipse;
import org.six11.util.pen.Vec;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;
import static java.lang.Math.toDegrees;

public class EllipseArcSegment extends Segment {

  RotatedEllipse ellie;
  private int numSegments = 60;

  public EllipseArcSegment(Ink ink, List<Pt> points, boolean termA, boolean termB) {
    // The ellipse is fit using regression, and does not necessarily begin and end at the first 
    // and last points in the list. So get the elliptical region, and transform it two times so it
    // does begin and end at those points.
    ellie = Functions.createEllipse(points);
    int n = points.size();
    ellie.setArcRegion(points.get(0), points.get(n / 2), points.get(n - 1));
    int len = (int) Math.ceil(Functions.getCurvilinearLength(points));
    List<Pt> surface = ellie.getRestrictedArcPath(len);

    // first transform: use 0 as hinge
    int idxHinge = 0;
    int idxReference = surface.size() - 1;
    Pt target = points.get(points.size() - 1);
    List<Pt> transformed = Functions.hinge(idxHinge, idxReference, target, surface);
    for (int i = 0; i < points.size(); i++) {
      points.get(i).setLocation(transformed.get(i));
    }

    // second transform: use last as hinge
    idxHinge = surface.size() - 1;
    idxReference = 0;
    target = points.get(0);
    transformed = Functions.hinge(idxHinge, idxReference, target, surface);
    for (int i = 0; i < points.size(); i++) {
      points.get(i).setLocation(transformed.get(i));
    }
    init(ink, surface, termA, termB, Type.EllipticalArc);
  }

  public void drawDebug(DrawingBuffer buf) {
    bug("called");
    Pt c = ellie.getCentroid();
    Pt arc1 = ellie.getRegionPoints().get(0);
    Pt arc2 = ellie.getRegionPoints().get(1);
    Pt arc3 = ellie.getRegionPoints().get(2);
    RotatedEllipseShape fullEllipse = new RotatedEllipseShape(ellie, numSegments);
    List<Pt> surfacePoints = fullEllipse.getSegmentedSurface();
    Shape surface = ShapeFactory.makeLinePath(surfacePoints, true);
    DrawingBufferRoutines.drawShape(buf, surface, Color.LIGHT_GRAY, 1.0);
    System.out.println("angle\tt");
    for (int i = 0; i < surfacePoints.size(); i++) {
      float frac = 1f - (float) i / (float) surfacePoints.size();
      Pt pt = surfacePoints.get(i);
      Color color = new Color(frac, frac, frac);
      DrawingBufferRoutines.dot(buf, pt, 2.0, 0.3, color, color);
//      double ptAng = ellie.getEllipticalAngle(pt);
//      double ptT = pt.getDouble("ellipse_t");
//      if ((i + 3) % 6 == 0) { // every few dots write its angle and ellipse_t value
//        double distToC = c.distance(pt);
//        Vec toPt = ellie.getVector(ptAng).getVectorOfMagnitude(distToC + 20);
//        DrawingBufferRoutines.text(buf, c.getTranslated(toPt.getX(), toPt.getY()), num(toDegrees(ptAng)), Color.GRAY);
//        DrawingBufferRoutines.text(buf, c.getTranslated(toPt.getX(), toPt.getY() + 22), "t=" + num(toDegrees(ptT)), Color.GRAY);
//      }
//      System.out.println(num(toDegrees(ptAng)) + "\t" + num(toDegrees(ptT)));
    }
    DrawingBufferRoutines.dot(buf, c, 4.0, 0.4, Color.BLACK, Color.BLUE);
//    DrawingBufferRoutines.dot(buf, arc1, 4.0, 0.4, Color.BLACK, Color.GREEN);
//    DrawingBufferRoutines.dot(buf, arc2, 4.0, 0.4, Color.BLACK, Color.YELLOW);
//    DrawingBufferRoutines.dot(buf, arc3, 4.0, 0.4, Color.BLACK, Color.RED);
    Vec north = ellie.getAxisA();
    Vec east = ellie.getAxisB();
    Pt northPt = c.getTranslated(north.getX(), north.getY());
    Pt eastPt = c.getTranslated(east.getX(), east.getY());
//    DrawingBufferRoutines.dot(buf, northPt, 4.0, 0.4, Color.BLACK, Color.MAGENTA);
//    DrawingBufferRoutines.dot(buf, eastPt, 4.0, 0.4, Color.BLACK, Color.CYAN);
    Vec northPlus = north.getVectorOfMagnitude(north.mag() + 10);
//    DrawingBufferRoutines.line(buf, c, northPt, Color.MAGENTA, 1);
//    DrawingBufferRoutines.line(buf, c, eastPt, Color.CYAN, 1);
//    DrawingBufferRoutines.text(buf, c.getTranslated(northPlus.getX(), northPlus.getY()),
//        num(toDegrees(ellie.getRotation())), Color.BLACK);
    double arc1T = ellie.searchForParameter(arc1);
    Pt arc1Final = ellie.getEllipticalPoint(arc1T);
    DrawingBufferRoutines.dot(buf, arc1Final, 4.0, 0.4, Color.BLACK, Color.GREEN);
    
    double arc2T = ellie.searchForParameter(arc2);
    Pt arc2Final = ellie.getEllipticalPoint(arc2T);
    DrawingBufferRoutines.dot(buf, arc2Final, 4.0, 0.4, Color.BLACK, Color.YELLOW);
    
    double arc3T = ellie.searchForParameter(arc3);
    Pt arc3Final = ellie.getEllipticalPoint(arc3T);
    DrawingBufferRoutines.dot(buf, arc3Final, 4.0, 0.4, Color.BLACK, Color.RED);
    bug("Parameters are: " + num(arc1T) + ", " + num(arc2T) + ", " + num(arc3T));
  }

  public void drawArc(DrawingBuffer buf) {
//    Pt c = ellie.getCentroid();
    Pt arc1 = ellie.getRegionPoints().get(0);
    Pt arc2 = ellie.getRegionPoints().get(1);
    Pt arc3 = ellie.getRegionPoints().get(2);
    double arc1Ang = ellie.getEllipticalAngle(arc1) - ellie.getRotation();
    double arc2Ang = ellie.getEllipticalAngle(arc2) - ellie.getRotation();
    double arc3Ang = ellie.getEllipticalAngle(arc3) - ellie.getRotation();
    Pt arc1Reconstructed = ellie.getEllipticalPoint(-arc1Ang);
    Pt arc2Reconstructed = ellie.getEllipticalPoint(-arc2Ang);
    Pt arc3Reconstructed = ellie.getEllipticalPoint(-arc3Ang);
    DrawingBufferRoutines.cross(buf, arc1Reconstructed, 6, Color.GREEN);
    DrawingBufferRoutines.cross(buf, arc2Reconstructed, 6, Color.YELLOW);
    DrawingBufferRoutines.cross(buf, arc3Reconstructed, 6, Color.RED);
  }

  private List<Pt> getSegmentedArc(double angle1, double angle2) {
    double step = (angle2 - angle1) / (double) numSegments;
    List<Pt> ret = new ArrayList<Pt>();
    for (double t = angle1; t <= angle2; t += step) {
      Pt pt = ellie.getEllipticalPoint(t);
      ret.add(pt);
    }
    return ret;
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
  private List<Double> makeMonotonicallyIncreasingAngles(List<Pt> regionPoints) {
    if (regionPoints.size() != 3) {
      bug("Warning: I need exactly three points. Chaos ensues...");
    }
    List<Double> angles = new ArrayList<Double>(); // in radians
    double twoPi = Math.PI * 2;
    for (Pt rp : regionPoints) {
      angles.add(ellie.getEllipticalAngle(rp));
    }
    StringBuilder buf = new StringBuilder();
    buf.append(getAngleCode(angles.get(0), angles.get(1))); // a to b
    buf.append(getAngleCode(angles.get(1), angles.get(2))); // b to c
    buf.append(getAngleCode(angles.get(0), angles.get(2))); // a to c
    String code = buf.toString();
    bug("Code: " + code);
    // there are eight possible values for the angle code. +++, ++-, +-+, etc.
    // two aren't possible (++- and --+).
    // two others don't require adjustment (+++ and ---).
    // the remaining four require us to add 2pi to one or two values.
    if (code.equals("+-+")) {
      bug("Adjust a");
      angles.set(0, angles.get(0) + twoPi); // adjust a
    }
    if (code.equals("+--")) {
      bug("Adjust c");
      angles.set(2, angles.get(2) + twoPi); // adjust c
    }
    if (code.equals("-++")) {
      bug("Adjust a and b");
      angles.set(0, angles.get(0) + twoPi); // adjust a and b
      angles.set(1, angles.get(1) + twoPi);
    }
    if (code.equals("-+-")) {
      bug("Adjust b and c");
      angles.set(1, angles.get(1) + twoPi); // adjust b and c
      angles.set(2, angles.get(2) + twoPi);
    }
    if (code.equals("++-") || code.equals("--+")) {
      bug("Ugh, encountered an 'impossible' code.");
    }
    if (code.equals("+++") || code.equals("---")) {
      bug("No action required.");
    }
    buf.setLength(0);
    buf.append(getAngleCode(angles.get(0), angles.get(1))); // a to b
    buf.append(getAngleCode(angles.get(1), angles.get(2))); // b to c
    buf.append(getAngleCode(angles.get(0), angles.get(2))); // a to c
    code = buf.toString();
    bug("After adjucting, code: " + code);
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
