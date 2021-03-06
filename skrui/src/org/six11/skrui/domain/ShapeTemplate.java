package org.six11.skrui.domain;

import java.util.*;

import org.six11.skrui.constraint.Constraint;
import org.six11.skrui.constraint.TypeConstraint;
import org.six11.skrui.shape.Primitive;
import org.six11.skrui.script.Neanderthal.Certainty;
import org.six11.util.Debug;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public abstract class ShapeTemplate {

  Domain domain;
  String name;
  List<String> slotNames;
  List<Class<? extends Primitive>> slotTypes;
  Map<String, Constraint> constraints;
  Map<String, Set<String>> slotsToConstraints; // slot name -> set of constraint names
  Map<String, SortedSet<Primitive>> valid;

  public ShapeTemplate(Domain domain, String name) {
    this.domain = domain;
    this.name = name;
    slotNames = new ArrayList<String>();
    slotTypes = new ArrayList<Class<? extends Primitive>>();
    constraints = new HashMap<String, Constraint>();
    slotsToConstraints = new HashMap<String, Set<String>>();
    valid = new HashMap<String, SortedSet<Primitive>>();
  }

  public String toString() {
    return name;
  }

  public String getName() {
    return name;
  }

  protected void addConstraint(String cName, Constraint c) {
    constraints.put(cName, c);
    for (String slotName : c.getSlotNames()) {
      String primary = Constraint.primary(slotName);
      if (!slotsToConstraints.containsKey(primary)) {
        slotsToConstraints.put(primary, new HashSet<String>());
      }
      slotsToConstraints.get(primary).add(cName);
    }
  }

  public List<String> getSlotNames() {
    return new ArrayList<String>(slotNames);
  }

  public List<Class<? extends Primitive>> getSlotTypes() {
    return new ArrayList<Class<? extends Primitive>>(slotTypes);
  }

  protected void addPrimitive(String slotName, Class<? extends Primitive> c) {
    slotNames.add(slotName);
    slotTypes.add(c);
    constraints.put(slotName, new TypeConstraint(slotName, domain.getData(), c));
  }

  public Domain getDomain() {
    return domain;
  }

  public List<Shape> apply(Set<Primitive> in) {
    resetValid(false); // reset the list of valid options for each slot.
    // set the valid options for each slot by type. E.g., all lines are valid for a line slot.
    setValid(in);
    // for (String key : slotsToConstraints.keySet()) {
    // bug("key: " + key + ", value: " + slotsToConstraints.get(key));
    // }
    Stack<String> bindSlot = new Stack<String>();
    Stack<Primitive> bindObj = new Stack<Primitive>();
    List<Shape> results = new ArrayList<Shape>();
    fit(0, bindSlot, bindObj, results);
//    for (Shape s : results) {
//      bug("Result: " + s);
//    }
    return results;
  }

  private void fit(int slotIndex, Stack<String> bindSlot, Stack<Primitive> bindObj,
      List<Shape> results) {
    // bug("fit: slotIndex: " + slotIndex + ", slots: " + Debug.num(bindSlot, ", ") + " objects: "
    // + getBugStringPrims(bindObj));
    String topSlot = slotNames.get(slotIndex);
    bindSlot.push(topSlot);
    for (Primitive p : valid.get(topSlot)) {
      if (!bindObj.contains(p)) {
        bindObj.push(p);
        boolean[] revertFixedState = new boolean[bindObj.size()];
        for (int i = 0; i < bindObj.size(); i++) {
          Primitive prim = bindObj.get(i);
          revertFixedState[i] = !prim.isFlippable();
        }
        Certainty result = evaluate(bindSlot, bindObj);
        if (result != Certainty.No) {
          if (slotIndex + 1 < slotNames.size()) {
            fit(slotIndex + 1, bindSlot, bindObj, results);
          } else {
            boolean ok = true;
            for (Shape s : results) {
              if (s.containsAllShapes(bindObj)) {
                ok = false;
                break;
              }
            }
            if (ok) {
              // bug("Located a " + name + ".                   ***** " + name + " <-----------");
              results.add(new Shape(this, bindSlot, bindObj));
              // } else {
              // bug("Avoiding redundant shape.");
            }

          }
        }
        for (int i = 0; i < bindObj.size(); i++) {
          Primitive prim = bindObj.get(i);
          prim.setSubshapeBindingFixed(revertFixedState[i]);
        }
        bindObj.pop();
      }
    }
    bindSlot.pop();
  }

  private Certainty evaluate(Stack<String> bindSlot, Stack<Primitive> bindObj) {
    // bug("evaluate: " + getBugStringPaired(bindSlot, bindObj));
    String topSlot = bindSlot.peek();
    Set<String> constraintNames = slotsToConstraints.get(topSlot);
    Certainty ret = Certainty.Unknown;
    if (constraintNames == null) {
      ret = Certainty.Yes; // This shape has no constraints, so there's nothing to say No.
    } else {
      for (String cName : constraintNames) {
        Constraint c = constraints.get(cName);
        List<String> relevantNames = c.getPrimarySlotNames();
        if (bindSlot.containsAll(relevantNames)) {
          Primitive[] args = c.makeArguments(bindSlot, bindObj);
          Certainty result = c.check(args);
          if (result == Certainty.No) {
            ret = result; // :(
            break;
          }
          ret = result;
        }
      }
    }
    // bug("evaluate: " + getBugStringPaired(bindSlot, bindObj) + " is returning " + ret);
    return ret;
  }

  @SuppressWarnings("unused")
  private static void bug(String what) {
    Debug.out("ShapeTemplate", what);
  }

  public static String getBugStringPaired(Stack<String> slots, Stack<Primitive> prims) {
    StringBuilder buf = new StringBuilder();
    if (slots.size() != prims.size()) {
      // buf.append("size mismatch: " + slots.size() + " != " + prims.size());
    } else {
      boolean first = true;
      for (int i = 0; i < slots.size(); i++) {
        if (!first) {
          buf.append(", ");
        }
        first = false;
        buf.append(slots.get(i) + "=" + prims.get(i).getShortStr());
      }
    }
    return buf.toString();
  }

  public static String getBugStringPrims(Collection<Primitive> prims) {
    StringBuilder buf = new StringBuilder();
    boolean first = true;
    for (Primitive p : prims) {
      if (!first) {
        buf.append(", ");
      }
      first = false;
      buf.append(p.getShortStr());
    }
    return buf.toString();
  }

  public static String getBugStringConstraints(Collection<Constraint> manyConstraints) {
    StringBuilder buf = new StringBuilder();
    boolean first = true;
    for (Constraint c : manyConstraints) {
      if (!first) {
        buf.append(", ");
      }
      first = false;
      buf.append(c.getShortStr());
    }
    return buf.toString();
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
          // bug("Primitive " + p.getShortStr() + ": " + p);
        }
      }
    }
  }

}
