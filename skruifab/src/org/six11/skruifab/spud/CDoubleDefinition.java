package org.six11.skruifab.spud;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class CDoubleDefinition extends Constraint {

  private double amt;

  public CDoubleDefinition(CDouble dest, double amt) {
    super();
    geometry.put("double", dest);
    this.amt = amt;
  }

  public void solve() {
    geometry.get("double").offer(amt);
    solved = true;
    bug("My double value is now known to be: " + amt);
    bug("If I ask the Geom object if it knows, what does it say?"
        + geometry.get("double").getDebugString());
  }

  public String getHumanReadableName() {
    return "double-definition";
  }

}
