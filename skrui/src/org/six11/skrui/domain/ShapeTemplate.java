package org.six11.skrui.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.six11.skrui.constraint.Constraint;
import org.six11.skrui.constraint.ConstraintSolver;
import org.six11.skrui.constraint.SlotBinding;
import org.six11.skrui.constraint.TypeConstraint;
import org.six11.skrui.script.Primitive;
import org.six11.skrui.script.Neanderthal.Certainty;
import org.six11.util.Debug;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public abstract class ShapeTemplate {

  Domain domain;
  List<String> slotNames;
  Map<String, Constraint> constraints;
  Map<String, Set<String>> slotsToConstraints; // slot name -> set of constraint names
  Map<String, SortedSet<Primitive>> valid;

  public ShapeTemplate(Domain domain) {
    this.domain = domain;
    slotNames = new ArrayList<String>();
    constraints = new HashMap<String, Constraint>();
    slotsToConstraints = new HashMap<String, Set<String>>();
    valid = new HashMap<String, SortedSet<Primitive>>();
  }

  protected void addConstraint(String cName, Constraint c) {
    constraints.put(cName, c);
    for (String slotName : c.getSlotNames()) {
      if (!slotsToConstraints.containsKey(slotName)) {
        slotsToConstraints.put(slotName, new HashSet<String>());
      }
      slotsToConstraints.get(slotName).add(cName);
    }
  }

  protected void addPrimitive(String slotName, Class<? extends Primitive> c) {
    slotNames.add(slotName);
    constraints.put(slotName, new TypeConstraint(slotName, domain.getData(), c));
  }

  public Domain getDomain() {
    return domain;
  }

  public void apply(Set<Primitive> in) {
    resetValid(false); // reset the list of valid options for each slot.
    // set the valid options for each slot by type. E.g., all lines are valid for a line slot.
    setValid(in);

    // Voting algorithm.
    VoteTable vt = new VoteTable();
    for (String slot : slotNames) {
      for (Primitive p : valid.get(slot)) {
        boolean ok = true;
        List<SlotBinding> accumulator = new ArrayList<SlotBinding>();
        for (String cName : slotsToConstraints.get(slot)) {
          Constraint c = constraints.get(cName);
          List<SlotBinding> result = vote(slot, p, c);
          if (result.size() == 0) {
            ok = false;
            break;
          } else {
            accumulator.addAll(result);
          }
        }
        if (ok) {
          // This slot/primitive combination is acceptable. Consilodate the slot bindings. The
          // important thing is that this slot/object cell has pointers to other slot/object cells.
          Map<String, List<Primitive>> consolidated = consolidate(accumulator);
          vt.setVotes(slot, p, consolidated);
        } else {
          vt.invalidate(slot, p);
        }
      }
    }
    // Now the voting table has a whole bunch of votes to go through, but some of them are for
    // invalid cells, and others are from invalid cells. Only count those votes that are from and
    // for valid cells.
    vt.activateVotes();
  }

  // consolodate all the slot->object bindings into a map with multiple values. keys are slot names.
  private Map<String, List<Primitive>> consolidate(List<SlotBinding> in) {
    Map<String, List<Primitive>> consolidated = new HashMap<String, List<Primitive>>();
    for (SlotBinding sb : in) {
      Map<String, Primitive> bindingMap = sb.getBindingMap();
      for (String name : bindingMap.keySet()) {
        if (!consolidated.containsKey(name)) {
          consolidated.put(name, new ArrayList<Primitive>());
        }
        if (!consolidated.get(name).contains(bindingMap.get(name))) {
          consolidated.get(name).add(bindingMap.get(name));
        }
      }
    }
    return consolidated;
  }

  private static void bug(String what) {
    Debug.out("ShapeTemplate", what);
  }

  private List<SlotBinding> vote(String slot, Primitive p, Constraint c) {
    ConstraintSolver solver = c.getSolver();
    solver.bind(slot, p);
    solver.setAlternatives(valid);
    List<SlotBinding> result = solver.solve();
    return result;
  }

  public void resetValid(boolean create) {
    for (String name : slotNames) {
      if (create) {
        valid.put(name, new TreeSet<Primitive>());
      } else {
        valid.get(name).clear();
      }
    }
  }

  public void setValid(Set<Primitive> in) {
    for (String slot : slotNames) {
      for (Primitive p : in) {
        if (constraints.get(slot).check(new Primitive[] {
          p
        }) == Certainty.Yes) {
          valid.get(slot).add(p);
          bug("Primitive " + p.getShortStr() + ": " + p);
        }
      }
    }
  }

}
