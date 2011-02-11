package org.six11.skruifab.spud;

/**
 * A special geometric thing used as a starting point (rather, an infinite set of starting points?)
 * for whittling down a bunch of solution spaces into something more manageable. When a thing
 * intersects with infinity, you just get the thing back.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Infinity extends Geom {

  public Infinity() {
  }

  public String getDebugString() {
    return "infinity";
  }

  public String getHumanReadableName() {
    return "infinity";
  }
  
  public Geom intersectPoint(CPoint pt) {
    return pt;
  }
  
  public Geom intersectLine(CLine line) {
    return line;
  }

  public Geom intersectCircle(CCircle circ) {
    return circ;
  }

  public Geom intersectPointSet(CPointSet ptset) {
    return ptset;
  }



}
