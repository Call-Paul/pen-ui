package org.six11.skruifab.spud;

import java.util.HashMap;
import java.util.Map;

public abstract class Constraint extends Node {

  protected Map<String, Geom> geometry;

  public Constraint() {
    this.geometry = new HashMap<String, Geom>();
  }

  public String getMondoDebugString(String space) {
    StringBuilder buf = new StringBuilder();
    for (String key : geometry.keySet()) {
      Geom g = geometry.get(key);
      buf.append(space + key + " = " + (g == null ? "<unknown>" : g.getDebugString()) + "\n");
    }
    return buf.toString();
  }

  public String toString() {
    return getHumanReadableName();
  }

  public void solveSafely() {
    if (!isSolved()) {
      bug("Solving " + getHumanReadableName());
      solve();
    }
  }

  protected boolean known(Geom... geothings) {
    boolean ret = true;
    for (Geom g : geothings) {
      ret = (g != null && g.isSolved());
      if (g == null) {
        bug("This one is null.");
      } else {
        bug("is " + g.getDebugString() + " known?" + ret);
      }
      if (!ret) {
        break;
      }
    }
    return ret;
  }

  public abstract void solve();

}
