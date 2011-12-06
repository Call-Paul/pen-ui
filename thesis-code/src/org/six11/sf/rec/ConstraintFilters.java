package org.six11.sf.rec;

import java.util.HashSet;
import java.util.Set;

import org.six11.util.pen.Pt;
import org.six11.util.solve.Constraint;
import org.six11.util.solve.VariableBank.ConstraintFilter;

public abstract class ConstraintFilters {

  /**
   * A filter that accepts a contraint if it involves all points from some group. This can be used
   * to select constraints that involve (A and B) or (C and D) or (E and F and G). For example, say
   * you have two segments and you would like to select constraints that completely involve one of
   * them. You would call this function thusly: getInvolvesFilter(segA.getEndpointArray(),
   * segB.getEndpointArray())
   * 
   * @param groups
   * @return
   */
  public static ConstraintFilter getInvolvesFilter(final Pt[]... groups) {
    ConstraintFilter ret = new ConstraintFilter() {
      public Set<Constraint> filter(Set<Constraint> input) {
        Set<Constraint> good = new HashSet<Constraint>();
        for (Constraint c : input) {
          for (Pt[] group : groups) {
            boolean ok = true;
            for (Pt pt : group) {
              if (!c.involves(pt)) {
                ok = false;
                break;
              }
            }
            if (ok) {
              good.add(c);
              break;
            }
          }
        }
        return good;
      }
    };
    return ret;
  }
}
