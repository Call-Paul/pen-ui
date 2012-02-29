package org.six11.sf;

import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.ceil;
import static java.lang.Math.cos;
import static java.lang.Math.min;
import static java.lang.Math.sin;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.six11.util.Debug;
import org.six11.util.gui.shape.Circle;
import org.six11.util.gui.shape.ShapeFactory;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.RotatedEllipse;
import org.six11.util.pen.Sequence;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

public class CircleSegment extends SegmentDelegate {
  private Circle circ;
  private transient double cachedR;
  private transient double cachedX;
  private transient double cachedY;
  private transient List<Pt> cachedPL;

  public CircleSegment(Ink ink, List<Pt> points) {
    bug("Creating closed circle!");
    RotatedEllipse ellie = Functions.createEllipse(points, false);
    double a = ellie.getMajorRadius();
    double b = ellie.getMinorRadius();
    this.circ = new Circle(ellie.getCentroid(), (a + b) / 2.0);
    this.ink = ink;
    this.p1 = points.get(0);
    this.p2 = p1;
    this.type = Segment.Type.Circle;
  }

  public CircleSegment(SketchBook model, JSONObject json) throws JSONException {
    this.type = Segment.Type.Circle;
    double cx = json.getDouble("cx");
    double cy = json.getDouble("cy");
    double p1x = json.getDouble("p1x");
    double p1y = json.getDouble("p1y");
    Pt c = new Pt(cx, cy);
    this.p1 = new Pt(p1x, p1y);
    this.p2 = p1;
    double r = c.distance(p1);
    this.circ = new Circle(c, r);
  }

  public JSONObject toJson() throws JSONException {
    JSONObject ret = new JSONObject();
    ret.put("type", type);
    ret.put("cx", circ.getCenterX());
    ret.put("cy", circ.getCenterY());
    ret.put("p1x", p1.x);
    ret.put("p1y", p1.y);
//    ret.put("p2x", p2.x);
//    ret.put("p2y", p2.y);
//    Debug.errorOnNull(SketchBook.n(p1), "p1.name");
//    Debug.errorOnNull(SketchBook.n(p2), "p2.name");
//    Debug.errorOnNull(pri, "pri");
//    Debug.errorOnNull(alt, "alt");
//    Debug.errorOnNull(type, "type");
//    JSONObject ret = new JSONObject();
//    ret.put("p1", SketchBook.n(p1));
//    ret.put("p2", SketchBook.n(p2));
//    ret.put("pri", new JSONArray(pri));
//    ret.put("alt", new JSONArray(alt));
//    ret.put("type", type);
    return ret;
  }
  
  public boolean hasEndCaps() {
    return false;
  }

  public Shape asCircle() {
    return circ;
  }

  public Circle getCircle() {
    return circ;
  }

  public List<Pt> getPointList() {
    List<Pt> ret = null;
    if (cachedR == circ.getRadius() && cachedX == circ.x && cachedY == circ.y && cachedPL != null) {
//      bug("Using cached circle pointlist");
      ret = cachedPL;
    } else {
      ret = new ArrayList<Pt>();
      double r = circ.getRadius();
      double circumference = 2.0 * r * Math.PI;
      int numSteps = (int) circumference;
      bug("Using " + numSteps + " steps with circle of circumference " + num(circumference));
      double step = (2.0 * Math.PI) / numSteps;
      for (int i = 0; i <= numSteps; i++) {
        double ang = ((double) i) * step;
        double x = circ.x + r + (r * cos(ang));
        double y = circ.y + r + (r * sin(ang));
        ret.add(new Pt(x, y));
      }
      cachedR = r;
      cachedX = circ.x;
      cachedY = circ.y;
      cachedPL = ret;
    }
    return ret;
  }

  public List<Pt> asPolyline() {
    return getPointList();
  }
  
  public Sequence asSpline() {
    Sequence plSpline = new Sequence(getPointList());
    List<Pt> downsampled = plSpline.getDownsample(10);
    double roughLength = Functions.getCurvilinearLength(downsampled);
    int numSteps = (int) ceil(min(roughLength / 100, 10));
    Sequence spline = Functions.makeNaturalSpline(numSteps, downsampled);
    spline.add(spline.getFirst());
    return spline;
  }

  public List<Pt> storeParaPointsForDeformation() {
    deformedPoints = new ArrayList<Pt>();
    Sequence plSeq = asSpline();
    deformedPoints.addAll(plSeq.getDownsample(5.0));
    return deformedPoints;
  }

  public void calculateParameters(List<Pt> points) {
    bug("eeeeeeee circle does not need to do this!");
  }
}
