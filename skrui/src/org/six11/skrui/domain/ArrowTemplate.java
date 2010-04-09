package org.six11.skrui.domain;

import org.six11.skrui.constraint.AcuteAngle;
import org.six11.skrui.constraint.Coincident;
import org.six11.skrui.constraint.EqualLength;
import org.six11.skrui.constraint.Larger;
import org.six11.skrui.shape.LineSegment;
import org.six11.util.Debug;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class ArrowTemplate extends ShapeTemplate {

  public ArrowTemplate(Domain d) {
    super(d, "Arrow");
    addPrimitive("shaft", LineSegment.class);
    addPrimitive("head1", LineSegment.class);
    addPrimitive("head2", LineSegment.class);
    addConstraint("c1", new Coincident("c1", d.getData(), "shaft.p1", "head1.p1"));
    addConstraint("c2", new Coincident("c2", d.getData(), "shaft.p1", "head2.p1"));
    addConstraint("c3", new AcuteAngle("c3", d.getData(), "head1", "shaft"));
    addConstraint("c4", new AcuteAngle("c4", d.getData(), "head2", "shaft"));
    addConstraint("c5", new EqualLength("c5", d.getData(), "head1", "head2"));
    addConstraint("c6", new Larger("c5", d.getData(), "shaft", "head1"));
    
    resetValid(true);
  }

  @SuppressWarnings("unused")
  private void bug(String what) {
    Debug.out("ArrowTemplate", what);
  }
}