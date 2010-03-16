package org.six11.skrui.domain;

import java.util.Stack;

import org.six11.skrui.constraint.Adjacent;
import org.six11.skrui.script.LineSegment;
import org.six11.skrui.script.Primitive;
import org.six11.util.Debug;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class TriangleTemplate extends ShapeTemplate {

  public TriangleTemplate(Domain d) {
    super(d, "Triangle");
    addPrimitive("line1", LineSegment.class);
    addPrimitive("line2", LineSegment.class);
    addPrimitive("line3", LineSegment.class);
    addConstraint("c1", new Adjacent("c1", d.getData(), "line1.p2", "line2.p1"));
    addConstraint("c2", new Adjacent("c2", d.getData(), "line2.p2", "line3.p1"));
    addConstraint("c3", new Adjacent("c3", d.getData(), "line3.p2", "line1.p1"));
    resetValid(true);
  }


  @SuppressWarnings("unused")
  private void bug(String what) {
    Debug.out("TriangleTemplate", what);
  }

}
