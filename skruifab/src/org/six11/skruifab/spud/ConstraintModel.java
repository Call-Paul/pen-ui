package org.six11.skruifab.spud;

import java.util.ArrayList;
import java.util.List;

import org.six11.util.Debug;

public class ConstraintModel {
  
  private List<Constraint> constraints;
  
  public ConstraintModel() {
    this.constraints = new ArrayList<Constraint>();
  }

  public void addConstraint(Constraint c) {
    constraints.add(c);
  }

  public String getMondoDebugString() {
    StringBuilder buf = new StringBuilder();
    String space1 = "  ";
    String space2 = "     ";
    buf.append("\n-------------------------------\n");
    for (Constraint c : constraints) {
      buf.append(space1 + c + ":\n");
      buf.append(c.getMondoDebugString(space2));
    }
    buf.append("-------------------------------");
    return buf.toString();
  }

  public void solve() {
    int round = 1;
    for (Constraint c : constraints) {
      c.solveSafely();
      bug("End of round " + round + ": " + getMondoDebugString());
      round++;
    }
  }
  
  private static void bug(String what) {
    Debug.out("ConstraintModel", what);
  }

}
