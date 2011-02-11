package org.six11.skruifab.spud;

import org.six11.util.pen.Vec;

/**
 * 
 *
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class CParallelLines extends Constraint {

  public CParallelLines(CLine lineA, CLine lineB) {
    geometry.put("lineA", lineA);
    geometry.put("lineB", lineB);
  }

  public void solve() {
    bug("Attempting to solve parallel lines constraint");
    CLine lineA = (CLine) geometry.get("lineA");
    CLine lineB = (CLine) geometry.get("lineB");
    if (lineA.isSlotValid("Dir") && !lineB.isSlotValid("Dir")) {
      Vec dirA = lineA.getDir();
      lineB.offer(dirA);
      solved = true;
    } else if (!lineA.isSlotValid("Dir") && lineB.isSlotValid("Dir")) {
      Vec dirB = lineB.getDir();
      lineA.offer(dirB);
      solved = true;
    }
    if (solved) {
      lineA.solveRelatedConstraints();
      lineB.solveRelatedConstraints();
    }
  }

  public String getHumanReadableName() {
    return "parallel-lines";
  }

}
