package org.six11.sf;

import static java.lang.Math.ceil;
import static java.lang.Math.min;

import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;

import org.six11.util.gui.shape.ShapeFactory;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import static org.six11.util.Debug.num;
import static org.six11.util.Debug.bug;

public class Blob extends SegmentDelegate {

  private List<Pt> ctrl;

  public Blob(Ink ink, List<Pt> points) {
    this.ink = ink;
    this.ctrl = new ArrayList<Pt>();
    ctrl.addAll(points);
    this.p1 = points.get(0);
    this.p2 = p1;
    this.type = Segment.Type.Blob;
  }

  public boolean hasEndCaps() {
    return false;
  }

  public Sequence asSpline() {
    double roughLength = 0;
    for (int i = 0; i < ctrl.size() - 1; i++) {
      roughLength = roughLength + ctrl.get(i).distance(ctrl.get(i + 1));
    }
    int numSteps = (int) ceil(min(roughLength / 100, 10));
    Sequence spline = Functions.makeClosedNaturalSpline(numSteps, ctrl);
    Pt splineA = spline.getFirst();
    Pt splineB = spline.getLast();
    double d = splineA.distance(splineB);
    return spline;
  }

  public List<Pt> getPointList() {
    Shape myShape = asSpline();
    return ShapeFactory.makePointList(myShape.getPathIterator(null));
  }

  public List<Pt> asPolyline() {
    return getPointList();
  }
}
