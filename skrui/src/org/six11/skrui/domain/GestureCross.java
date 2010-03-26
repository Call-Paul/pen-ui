package org.six11.skrui.domain;

import org.six11.skrui.script.Dot;
import org.six11.skrui.script.LineSegment;

/**
 * 
 *
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class GestureCross extends ShapeTemplate {

  /**
   * @param domain
   * @param name
   */
  public GestureCross(Domain domain) {
    super(domain, "Gesture Cross");
    
    addPrimitive("dot", Dot.class);
    addPrimitive("line1", LineSegment.class);
    addPrimitive("line2", LineSegment.class);
    
    
  }

}
