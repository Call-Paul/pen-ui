package org.six11.sf;

import static java.lang.Math.ceil;
import static java.lang.Math.min;
import static org.six11.util.Debug.bug;

import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;
import org.six11.util.gui.shape.Circle;
import org.six11.util.gui.shape.ShapeFactory;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.RotatedEllipse;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.Vec;

public class EllipseSegment extends SegmentDelegate {

  RotatedEllipse ellie;
  private transient Shape cachedShape;
  private transient double cachedY;
  private transient double cachedX;
  private transient double cachedA;
  private transient double cachedB;


  public EllipseSegment(Ink ink, List<Pt> points) {
    this.ellie = Functions.createEllipse(points, false);
    this.ink = ink;
    this.p1 = points.get(0);
    this.p2 = p1;
    this.type = Segment.Type.Ellipse;
  }
  
  public EllipseSegment(SketchBook model, JSONObject json) throws JSONException {
    this.type = Segment.Type.Ellipse;
    double ex = json.getDouble("ex");
    double ey = json.getDouble("ey");
    double a = json.getDouble("a");
    double b = json.getDouble("b");
    Pt c = new Pt(ex, ey);
    double rot = json.getDouble("rot");
    double p1x = json.getDouble("p1x");
    double p1y = json.getDouble("p1y");
    this.ellie = new RotatedEllipse(c, a, b, rot);
    this.p1 = new Pt(p1x, p1y);
    this.p2 = p1;
  }

  public boolean hasEndCaps() {
    return false;
  }

  public Shape asEllipse() {
    Shape ret = null;
    if (cachedA == ellie.getMajorRadius() && cachedB == ellie.getMinorRadius()
        && cachedX == ellie.getCentroid().x && cachedY == ellie.getCentroid().y
        && cachedShape != null) {
      ret = cachedShape;
    } else {
      double bigR = ellie.getMajorRadius();
      double closeToCircumference = bigR * 2.0 * Math.PI;
      int numSteps = (int) closeToCircumference;
      ret = new ShapeFactory.RotatedEllipseShape(ellie, numSteps);
      cachedA = ellie.getMajorRadius();
      cachedB = ellie.getMinorRadius();
      cachedX = ellie.getCentroid().x;
      cachedY = ellie.getCentroid().y;
      cachedShape = ret;
    }
    return ret;
  }

  public List<Pt> getPointList() {
    Shape myShape = asEllipse();
    return ShapeFactory.makePointList(myShape.getPathIterator(null));
  }

  public List<Pt> asPolyline() {
    return getPointList();
  }

  public RotatedEllipse getEllipse() {
    return ellie;
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
    bug("eeeeeeee elipse does not need to do this!");
  }
  
  public JSONObject toJson() throws JSONException {
    JSONObject ret = new JSONObject();    
    ret.put("type", type);
    ret.put("ex", ellie.getCentroid().x);
    ret.put("ey", ellie.getCentroid().y);
    ret.put("a", ellie.getParamA());
    ret.put("b", ellie.getParamB());
    ret.put("rot", ellie.getRotation());
    ret.put("p1x", p1.x);
    ret.put("p1y", p1.y);
    return ret;
  }
}
