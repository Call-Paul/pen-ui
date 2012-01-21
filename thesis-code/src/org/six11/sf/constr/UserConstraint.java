package org.six11.sf.constr;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.six11.sf.Ink;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Pt;
import org.six11.util.solve.Constraint;

import static org.six11.util.Debug.bug;

/**
 * A UserConstraint is a place to retain references to several related constraints, and it usually
 * encapsulates an individual user request such as "keep lines A, B, and C the same length." Such a
 * request involves three Constraint objects, but it was made in a single request. Later, if the
 * user also requests that lines C and D should be the same length, the Constraint object can be
 * added to this one, so it involves A, B, C, and D.
 */
public abstract class UserConstraint {

  private String name;
  private Collection<Constraint> constraints;

  public UserConstraint(String name, Constraint... cs) {
    this.name = name;
    this.constraints = new HashSet<Constraint>();
    for (Constraint c : cs) {
      constraints.add(c);
    }
  }
  
  public String getName() {
    return name;
  }

  public Collection<Constraint> getConstraints() {
    return constraints;
  }

  public void addConstraint(Constraint c) {
    constraints.add(c);
  }

  public void removeConstraint(Constraint c) {
    constraints.remove(c);
  }

  public void draw(DrawingBuffer buf, Pt hoverPoint) {
    // by default there is no drawing behavior. subclass this and override draw
  }

  public boolean involves(Pt pt) {
    boolean ret = false;
    for (Constraint c : constraints) {
      if (c.involves(pt)) {
        ret = true;
        break;
      }
    }
    return ret;
  }

  public String toString() {
    return name;
  }
  
}
