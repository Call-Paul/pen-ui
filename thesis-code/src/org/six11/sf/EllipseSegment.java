package org.six11.sf;

import static java.lang.Math.ceil;
import static java.lang.Math.min;
import static org.six11.util.Debug.bug;

import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;
import org.six11.util.gui.shape.ShapeFactory;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.RotatedEllipse;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.Vec;
import org.six11.util.solve.VariableBank;

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
    Pt center = ellie.getCentroid();
    Vec axisBoldAsLove = ellie.getAxisA();
    this.p1 = center.getTranslated(axisBoldAsLove);
    this.p2 = center;
    this.type = Segment.Type.Ellipse;
  }
  
  public EllipseSegment(SketchBook model, JSONObject json) throws JSONException {
    this.type = Segment.Type.Ellipse;
    String pt1Name = json.optString("p1");
    if (pt1Name == null) {
      bug("pt1Name is null while loading circle segment");
    }
    String pt2Name = json.optString("p2");
    if (pt2Name == null) {
      bug("pt2Name is null while loading circle segment");
    }
    VariableBank bank = model.getConstraints().getVars();
    this.p1 = bank.getPointWithName(pt1Name);
    this.p2 = bank.getPointWithName(pt2Name);
    double a = json.getDouble("a");
    double b = json.getDouble("b");
    double rot = json.getDouble("rot");
    this.ellie = new RotatedEllipse(p2, a, b, rot);
  }

  public JSONObject toJson() throws JSONException {
    JSONObject ret = new JSONObject();    
    ret.put("type", type);
    ret.put("p1", SketchBook.n(p1));
    ret.put("p2", SketchBook.n(p2));
    ret.put("a", ellie.getParamA());
    ret.put("b", ellie.getParamB());
    ret.put("rot", ellie.getRotation());
    return ret;
  }
  
  public boolean hasEndCaps() {
    return false;
  }

  public Shape asEllipse() {
    Shape ret = null;
    if ((cachedA == ellie.getMajorRadius()) && (cachedB == ellie.getMinorRadius())
        && (cachedX == ellie.getCentroid().x) && (cachedY == ellie.getCentroid().y)
        && (cachedShape != null)) {
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
  

  
  @Override
  public Pt getVisualMidpoint() {
    return p1;
  }
}
