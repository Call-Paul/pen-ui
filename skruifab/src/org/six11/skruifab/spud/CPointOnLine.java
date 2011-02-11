package org.six11.skruifab.spud;

import org.six11.util.pen.Functions;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class CPointOnLine extends Constraint {

  public CPointOnLine(Geom line, Geom pt) {
    geometry.put("line", line);
    geometry.put("pt", pt);
  }

  public void solve() {
    bug("Attempting to solve point-on-line");
    CPoint pt = (CPoint) geometry.get("pt");
    CLine line = (CLine) geometry.get("line");
    if (known(pt)) {
      // point is known, so offer it to the line.
      bug("point known.");
      line.offer(pt.getPt());
    }
    if (known(line)) {
      bug("line known.");
      // line is known, so that constrains where the point can be.
      pt.offer(line);
    }
    if (known(pt, line)) {
      double dist = Functions.getDistanceBetweenPointAndLine(pt.getPt(), line.getLine());
      solved = dist < Functions.EQ_TOL;
      if (solved) {
        pt.solveRelatedConstraints();
        line.solveRelatedConstraints();
      }
    }
  }

  public String getHumanReadableName() {
    return "point-on-line";
  }

}
