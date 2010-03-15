package org.six11.skrui.domain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.six11.skrui.constraint.Adjacent;
import org.six11.skrui.constraint.Constraint;
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
    super(d);
    addPrimitive("line1", LineSegment.class);
    addPrimitive("line2", LineSegment.class);
    addPrimitive("line3", LineSegment.class);
    addConstraint("c1", new Adjacent("c1", d.getData(), "line1", "line2"));
    addConstraint("c2", new Adjacent("c2", d.getData(), "line2", "line3"));
    addConstraint("c3", new Adjacent("c3", d.getData(), "line3", "line1"));
    resetValid(true);
  }


  private void bug(String what) {
    Debug.out("TriangleTemplate", what);
  }

}
