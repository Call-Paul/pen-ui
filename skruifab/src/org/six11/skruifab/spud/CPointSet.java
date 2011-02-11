package org.six11.skruifab.spud;

import java.util.ArrayList;
import java.util.List;

import org.six11.util.Debug;
import org.six11.util.pen.Pt;

/**
 * 
 *
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class CPointSet extends Geom {

  public CPointSet() {
    super();
    addSlot("Points");
  }

  public String getHumanReadableName() {
    return "pointset";
  }
  
  public void offer(Pt[] points) {
    slots.get("Points").value = points;
    bug("Populated CPointSet with points: " + Debug.num(points, " "));
    solved = true;
  }

  public String getDebugString() {
    bug("Geom.getDebugString not implemented yet!");
    return null;
  }

  public Geom intersectCircle(CCircle circ) {
    return circ.intersectPointSet(this);
  }

  public Geom intersectLine(CLine line) {
    return line.intersectPointSet(this);
  }

  public Geom intersectPoint(CPoint pt) {
    return pt.intersectPointSet(this);
  }

  public Pt[] getPoints() {
    return (Pt[]) slots.get("Points").value;
  }

  public Geom intersectPointSet(CPointSet ptset) {
    List<Pt> overlap = new ArrayList<Pt>();
    for (Pt myPoint : getPoints()) {
      for (Pt otherPoint : ptset.getPoints()) {
        if (myPoint.isSameLocation(otherPoint)) {
          overlap.add(myPoint);
        }
      }
    }
    Geom ret = new Nothing();
    if (overlap.size() > 0) {
      ret = new CPointSet();
      ret.offer(overlap.toArray(new Pt[overlap.size()]));
    }
    return ret;
  }

}
