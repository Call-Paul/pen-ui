package org.six11.skrui.domain;

import java.util.List;

import org.six11.skrui.constraint.EqualLength;
import org.six11.skrui.constraint.Intersects;
import org.six11.skrui.constraint.Near;
import org.six11.skrui.shape.Dot;
import org.six11.skrui.shape.LineSegment;
import org.six11.skrui.script.Neanderthal;
import org.six11.skrui.shape.Primitive;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class GestureCross extends GestureShapeTemplate {

  public GestureCross(Domain domain) {
    super(domain, "Gesture Cross");

    addPrimitive("dot", Dot.class);
    addPrimitive("line1", LineSegment.class);
    addPrimitive("line2", LineSegment.class);
    addConstraint("c1", new EqualLength("c1", domain.getData(), "line1", "line2"));
    addConstraint("c2", new Intersects("c2", domain.getData(), "line1", "line2"));
    addConstraint("c3", new Near("c3", domain.getData(), "dot", "line1"));
    
    resetValid(true);
  }

  public static Pt getPt(Primitive line1, Primitive line2) {
    Pt ret = Functions.getIntersectionPoint(line1.getGeometryLine(), line2.getGeometryLine());
    ret.setTime(Math.max(line1.getEndPt().getTime(), line2.getEndPt().getTime()));
    return ret;
  }

  public void trigger(Neanderthal data, Shape s, List<Primitive> matches) {
    data.addStructurePoint(GestureCross.getPt(s.subshapes.get("line1"), s.subshapes.get("line2")));
//    for (Primitive p : matches) {
//      data.forget(p);
//    }
    data.forget(s, false);
  }

}
