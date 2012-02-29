package org.six11.sf;

import static java.lang.Math.ceil;
import static java.lang.Math.min;

import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.six11.util.gui.shape.ShapeFactory;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.solve.JsonIO;

import static org.six11.util.Debug.num;
import static org.six11.util.Debug.bug;

public class Blob extends SegmentDelegate {

  private List<Pt> ctrl;

  public Blob(Ink ink, List<Pt> points) {
    init(ink, points);
  }

  public Blob(SegmentDelegate parent) {
    List<Pt> points = parent.getPointList();
    Sequence seq = new Sequence(points);
    List<Pt> downsampled = seq.getDownsample(5);
    init(parent.ink, downsampled);
    this.deformedPoints = parent.deformedPoints;
  }

  public Blob(SketchBook model, JSONObject json) throws JSONException {
    JsonIO io = new JsonIO();
    List<Pt> ptsFromJson = io.readPoints(json.getJSONArray("ctrl"));
    init(null, ptsFromJson);
  }

  public JSONObject toJson() throws JSONException {
    JSONObject ret = new JSONObject();
    ret.put("type", type);
    JsonIO io = new JsonIO();
    JSONArray arr = io.write(ctrl);
    ret.put("ctrl", arr);
    return ret;
  }

  private void init(Ink ink, List<Pt> points) {
    this.ink = ink;
    this.ctrl = new ArrayList<Pt>();
    ctrl.addAll(points);
    cleanOverlap();
    this.p1 = ctrl.get(0);
    this.p2 = p1;
    this.type = Segment.Type.Blob;
  }

  private final void cleanOverlap() {
    // There are two possible conditions: there is a gap, or there 
    // is an overlap. Only need to take action on overlaps. An gap is
    // when p(0) is closer to p(n-1) than it is to p(n-i), 
    // where i is 2..10 or so. An overlap is when p(0) is closer to 
    // one of the p(n-i) points.
    Pt p0 = ctrl.get(0);
    double rl = getRoughLength();
    double t = rl / 10;
    double d = 0;
    double minD;
    int idx = ctrl.size() - 1;
    int closestIdx = idx;
    Pt prev = ctrl.get(idx--);
    minD = prev.distance(p0);
    while (d < t && idx > 1) {
      Pt pi = ctrl.get(idx);
      double thisD = pi.distance(p0);
      if (thisD < minD) {
        minD = thisD;
        closestIdx = idx;
      }
      idx--;
      d = d + pi.distance(prev);
      prev = pi;
    }
    boolean gap = closestIdx == ctrl.size() - 1;
    if (!gap) {
      // there's an overlap. remove all points whose index is larger than closestIdx
      List<Pt> removed = new ArrayList<Pt>();
      while (ctrl.size() >= closestIdx) {
        removed.add(ctrl.remove(ctrl.size() - 1));
      }
      bug("Removed " + removed.size()
          + " points from 'end' of closed spline to increase general happiness and well-being.");
    }
  }

  public boolean hasEndCaps() {
    return false;
  }

  private double getRoughLength() {
    double roughLength = 0;
    for (int i = 0; i < ctrl.size() - 1; i++) {
      roughLength = roughLength + ctrl.get(i).distance(ctrl.get(i + 1));
    }
    return roughLength;
  }

  public Sequence asSpline() {
    double roughLength = getRoughLength();
    int numSteps = (int) ceil(min(roughLength / 100, 10));
    Sequence spline = Functions.makeClosedNaturalSpline(numSteps, ctrl);
    return spline;
  }

  public List<Pt> getPointList() {
    Shape myShape = asSpline();
    return ShapeFactory.makePointList(myShape.getPathIterator(null));
  }

  public List<Pt> asPolyline() {
    return getPointList();
  }

  public void calculateParameters(List<Pt> points) {
    this.ctrl = points;
  }

  public List<Pt> storeParaPointsForDeformation() {
    deformedPoints = new ArrayList<Pt>();
    Sequence plSeq = asSpline();
    deformedPoints.addAll(plSeq.getDownsample(5.0));
    return deformedPoints;
  }

  protected void doPara() {

  }
}
