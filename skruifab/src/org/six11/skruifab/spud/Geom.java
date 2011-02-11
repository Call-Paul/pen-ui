package org.six11.skruifab.spud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.six11.util.pen.CircleArc;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;

public abstract class Geom extends Node {

  Map<String, Slot> slots;
  List<Constraint> relatedConstraints;
  Set<Geom> solutionSpace;

  public Geom() {
    this.slots = new HashMap<String, Slot>();
    this.relatedConstraints = new ArrayList<Constraint>();
    this.solutionSpace = new HashSet<Geom>();
  }

  public void addConstraint(Constraint c) {
    if (!relatedConstraints.contains(c)) {
      relatedConstraints.add(c);
    }
  }

  public void addSlot(String slotName) {
    slots.put(slotName, new Slot(slotName));
  }

  public String getMondoDebugString(String leadingSpace) {
    StringBuilder buf = new StringBuilder();
    for (String slotName : slots.keySet()) {
      Slot slot = slots.get(slotName);
      buf.append(leadingSpace + slot.toString() + "\n");
    }
    return buf.toString();
  }

  public abstract String getDebugString();

  //  private static void bug(String what) {
  //    Debug.out("Geom", what);
  //  }

  public void offer(@SuppressWarnings("unused") CLine line) {
    bug("Warning: offer(Pt) not implemented by " + getClass().getSimpleName() + ". Override it!");
  }

  public void offer(@SuppressWarnings("unused") Pt data) {
    bug("Warning: offer(Pt) not implemented by " + getClass().getSimpleName() + ". Override it!");
  }

  public void offer(@SuppressWarnings("unused") Pt[] data) {
    bug("Warning: offer(Pt[]) not implemented by " + getClass().getSimpleName() + ". Override it!");
  }

  public void offer(@SuppressWarnings("unused") Vec dir) {
    bug("Warning: offer(Vec) not implemented by " + getClass().getSimpleName() + ". Override it!");
  }

  public void offer(@SuppressWarnings("unused") CircleArc circle) {
    bug("Warning: offer(CircleArc) not implemented by " + getClass().getSimpleName()
        + ". Override it!");
  }
  
  public void offer(@SuppressWarnings("unused") CCircle circle) {
    bug("Warning: offer(CCircle) not implemented by " + getClass().getSimpleName()
        + ". Override it!");
  }

  public void offer(@SuppressWarnings("unused") double amt) {
    bug("Warning: offer(double) not implemented by " + getClass().getSimpleName()
        + ". Override it!");
  }

  public void solveRelatedConstraints() {
    if (isSolved()) {
      for (Constraint c : relatedConstraints) {
        c.solveSafely();
      }
    }
  }

  protected boolean slotsValid(String... slotNames) {
    boolean ret = true;
    for (String name : slotNames) {
      Slot slot = slots.get(name);
      if (slot != null) {
        ret = slot.valid;
      } else {
        ret = false;
      }
      if (!ret) {
        break;
      }
    }
    return ret;
  }

  protected Geom intersectSolutionSpaces() {
    bug("Intersecting solution space of " + solutionSpace.size() + " items...");
    Geom space = new Infinity();
    for (Geom thing : solutionSpace) {
      bug("  " + space.getHumanReadableName() + " x " + thing.getHumanReadableName() + " ==> ");
      space = thing.intersect(space);
      bug("      " + space.getHumanReadableName());
    }
    bug("Solution space is: " + space.getHumanReadableName());
    return space;
  }

  public Geom intersect(Geom other) {
    Geom ret = null;
    if (other instanceof CPoint) {
      ret = intersectPoint((CPoint) other);
    } else if (other instanceof CLine) {
      ret = intersectLine((CLine) other);
    } else if (other instanceof Infinity) {
      ret = this;
    } else if (other instanceof Nothing) {
      ret = other;
    } else if (other instanceof CCircle) {
      ret = intersectCircle((CCircle) other);
    } else if (other instanceof CPointSet) {
      ret = intersectPointSet((CPointSet) other);
    } else {
      if (other == null) {
        bug("Warning: I don't know how to intersect " + getClass().getSimpleName() + " and " + null);
      } else {
        bug("Warning: I don't know how to intersect " + getClass().getSimpleName() + " and "
            + other.getClass().getSimpleName());
      }
    }

    return ret;
  }

  public abstract Geom intersectCircle(CCircle circ);

  public abstract Geom intersectPoint(CPoint pt);

  public abstract Geom intersectPointSet(CPointSet ptset);
  
  public abstract Geom intersectLine(CLine line);

}
