package org.six11.skrui.domain;

import org.six11.skrui.constraint.Coincident;
import org.six11.skrui.constraint.Parallel;
import org.six11.skrui.constraint.Perpendicular;
import org.six11.skrui.shape.LineSegment;
import org.six11.util.Debug;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class RectangleTemplate extends ShapeTemplate {

  public RectangleTemplate(Domain d) {
    super(d, "Rectangle");
    addPrimitive("line1", LineSegment.class);
    addPrimitive("line2", LineSegment.class);
    addPrimitive("line3", LineSegment.class);
    addPrimitive("line4", LineSegment.class);
    addConstraint("c1", new Perpendicular("c1", d.getData(), "line1", "line2"));
    addConstraint("c2", new Parallel("c2", d.getData(), "line1", "line3"));
    addConstraint("c3", new Perpendicular("c3", d.getData(), "line3", "line4"));
    addConstraint("c4", new Parallel("c4", d.getData(), "line2", "line4"));
    addConstraint("c10", new Coincident("c10", d.getData(), "line1.p2", "line2.p1"));
    addConstraint("c20", new Coincident("c20", d.getData(), "line2.p2", "line3.p1"));
    addConstraint("c30", new Coincident("c30", d.getData(), "line3.p2", "line4.p1"));
    addConstraint("c40", new Coincident("c40", d.getData(), "line4.p2", "line1.p1"));
    resetValid(true);
  }


  @SuppressWarnings("unused")
  private void bug(String what) {
    Debug.out("RectangleTemplate", what);
  }

}
