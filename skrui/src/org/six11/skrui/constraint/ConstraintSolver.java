package org.six11.skrui.constraint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.Stack;

import org.six11.skrui.shape.Primitive;
import org.six11.skrui.script.Neanderthal.Certainty;
import org.six11.util.Debug;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class ConstraintSolver {

  Constraint c;
  Primitive[] bindings;
  boolean[] bound;
  Set<Primitive> boundSet;
  Map<String, SortedSet<Primitive>> alts;

  public ConstraintSolver(Constraint sourceConstraint) {
    this.c = sourceConstraint;
    this.bindings = new Primitive[c.getNumSlots()];
    this.bound = new boolean[c.getNumSlots()];
    this.boundSet = new HashSet<Primitive>();
  }

  public void bind(String slot, Primitive p) {
    int where = getIndex(slot);
    bindings[where] = p;
    bound[where] = true;
    boundSet.add(p);
  }

  private int getIndex(String slot) {
    return c.getSlotNames().indexOf(slot);
  }

  public List<SlotBinding> solve() {
    List<Primitive[]> possible = makeAllBindings();
    bug("Solve constraint " + c.getName() + " given bindings: " + getBindingsString() + " ("
        + (bound.length - getNumBound()) + " free vars). " + possible.size()
        + " possible bindings.");
    List<SlotBinding> ok = new ArrayList<SlotBinding>();
    for (Primitive[] binding : possible) {
      Certainty result = c.check(binding);
      bug("  " + c.getShortStr() + "(" + getBindingsStringShort(binding) + "): " + result);
      if (result != Certainty.No) {
        ok.add(new SlotBinding(c, binding, result));
      }
    }
    return ok;
  }

  private void makeBinding(List<Primitive[]> fillMe, Stack<Primitive> taken, int slotNumber) {
    if (slotNumber == bound.length) {
      // We've reached the end. Make a new Primitive[] and add it to fillMe.
      Primitive[] binding = new Primitive[taken.size()];
      for (int i = 0; i < taken.size(); i++) {
        binding[i] = taken.get(i);
      }
      fillMe.add(binding);
    } else {
      if (!bound[slotNumber]) {
        String name = c.getSlotNames().get(slotNumber);
        Set<Primitive> slotOptions = alts.get(name); // values this slot may legally take
        for (Primitive option : slotOptions) {
          if (!taken.contains(option) && !boundSet.contains(option)) {
            taken.push(option);
            makeBinding(fillMe, taken, slotNumber + 1);
            taken.pop();
          }
        }
      } else {
        taken.push(bindings[slotNumber]);
        makeBinding(fillMe, taken, slotNumber + 1);
        taken.pop();
      }
    }
  }

  private List<Primitive[]> makeAllBindings() {
    List<Primitive[]> ret = new ArrayList<Primitive[]>();
    Stack<Primitive> taken = new Stack<Primitive>();
    makeBinding(ret, taken, 0);
    return ret;
  }

  private int getNumBound() {
    int numBound = 0;
    for (int i = 0; i < bound.length; i++) {
      if (bound[i]) {
        numBound++;
      }
    }
    return numBound;
  }

  private static void bug(String what) {
    Debug.out("ConstraintSolver", what);
  }

  private String getBindingsString() {
    StringBuilder buf = new StringBuilder();
    boolean first = true;
    for (int i = 0; i < bindings.length; i++) {
      if (bound[i]) {
        if (!first) {
          buf.append(", ");
        }
        first = false;
        buf.append(c.getSlotNames().get(i) + " = " + bindings[i].getShortStr());
      }
    }
    if (first) {
      buf.append("(no binding)");
    }
    return buf.toString();
  }

  @SuppressWarnings("unused")
  private String getBindingsString(Primitive[] binding) {
    StringBuilder buf = new StringBuilder();
    boolean first = true;
    for (int i = 0; i < binding.length; i++) {
      if (!first) {
        buf.append(", ");
      }
      first = false;
      buf.append(c.getSlotNames().get(i) + " = " + binding[i].getShortStr());
    }
    if (first) {
      buf.append("(no binding)");
    }
    return buf.toString();
  }

  private String getBindingsStringShort(Primitive[] binding) {
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < binding.length; i++) {
      buf.append(binding[i].getShortStr() + " ");
    }
    return buf.toString().trim();
  }

  public void setAlternatives(Map<String, SortedSet<Primitive>> valid) {
    alts = valid;
  }
}
