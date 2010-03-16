package org.six11.skrui.constraint;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.six11.skrui.script.Neanderthal;
import org.six11.skrui.script.Primitive;
import org.six11.skrui.script.Neanderthal.Certainty;
import org.six11.util.Debug;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public abstract class Constraint {

  List<String> slotNames;
  List<String> primarySlotNames;

  String name;
  Neanderthal data;

  public Constraint(String name, Neanderthal data, String... sNames) {
    this.name = name;
    this.data = data;
    slotNames = new ArrayList<String>();
    primarySlotNames = new ArrayList<String>();
    for (String n : sNames) {
      slotNames.add(n);
      if (n.indexOf(".") > 0) {
        bug("Found constraint with sub-shape binding: " + n.substring(n.indexOf(".") + 1));
      }
      primarySlotNames.add(primary(n));
    }
  }

  private static void bug(String what) {
    Debug.out("Constraint", what);
  }

  public abstract Certainty check(Primitive[] p);

  protected boolean ok(Certainty certainty) {
    return (certainty == Certainty.Yes || certainty == Certainty.Maybe);
  }

  public List<String> getSlotNames() {
    return slotNames;
  }

  public String getName() {
    return name;
  }

  public abstract String getShortStr();

  public ConstraintSolver getSolver() {
    return new ConstraintSolver(this);
  }

  public int getNumSlots() {
    return slotNames.size();
  }

  public static String primary(String slotName) {
    // lineA.p1 --> lineA
    // lineA --> lineA
    String ret = slotName;
    if (slotName.indexOf('.') > 0) {
      ret = slotName.substring(0, slotName.indexOf('.'));
    }
    bug("primary of " + slotName + " is: " + ret);
    return ret;
  }

  public static String subshape(String slotName) {
    // lineA.p1 --> p1
    // lineA --> null
    String ret = null;
    if (slotName.indexOf('.') > 0) {
      ret = slotName.substring(slotName.indexOf('.') + 1);
    }
    return ret;
  }

  public List<String> getPrimarySlotNames() {
    return primarySlotNames;
  }

  /**
   * Create an array that can be used for the check() method.
   */
  public Primitive[] makeArguments(List<String> slots, List<Primitive> prims) {
    Primitive[] ret = new Primitive[slotNames.size()];
    for (int i = 0; i < primarySlotNames.size(); i++) {
      String requiredName = primarySlotNames.get(i);
      int where = slots.indexOf(requiredName);
      ret[i] = prims.get(where);
    }
    return ret;
  }

}
