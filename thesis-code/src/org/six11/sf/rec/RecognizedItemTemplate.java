package org.six11.sf.rec;

import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;

import org.six11.sf.SketchBook;
import org.six11.sf.SketchRecognizer;
import org.six11.sf.rec.RecognizerPrimitive.Certainty;

import static org.six11.util.Debug.num;
import static org.six11.util.Debug.bug;

/**
 * This is the base class for recognizable entities like arrows and right-angle gestures. Subclass
 * it and add slots and constraints in the constructor.
 * 
 */
public abstract class RecognizedItemTemplate extends SketchRecognizer {

  /**
   * The name of the recognized item, e.g. "Arrow".
   */
  private String name;

  /**
   * A list of slot names. For example, an Arrow template might have 'head1', 'head2', and 'shaft'.
   */
  private List<String> slotNames;

  /**
   * Records each slot's type. Order matters, so slotNames[1] pairs with the type in slotTypes[1].
   */
  private List<RecognizerPrimitive.Type> slotTypes;

  /**
   * Map of constraints, named so we can refer to them easily (e.g. for debugging).
   */
  private Map<String, RecognizerConstraint> constraints;

  /**
   * A reverse map of slot names to constraints. This is so we can easily find get all the
   * constraints related to a geometric part, for example if we would like to see if enough
   * information is available to know if a specific constraint can can be checked.
   */
  private Map<String, Set<String>> slotsToConstraints;

  /**
   * Holds a set of candidates that could potentially fill a given slot. This is used to restrict
   * the search. For example, if slot 'shaft' requries a line, the set of candidates will only
   * include lines, but no dots.
   */
  private Map<String, SortedSet<RecognizerPrimitive>> validSlotCandidates;

  public RecognizedItemTemplate(SketchBook model, String name) {
    super(model);
    this.name = name;
    this.slotNames = new ArrayList<String>();
    this.slotTypes = new ArrayList<RecognizerPrimitive.Type>();
    this.constraints = new HashMap<String, RecognizerConstraint>();
    this.slotsToConstraints = new HashMap<String, Set<String>>();
    this.validSlotCandidates = new HashMap<String, SortedSet<RecognizerPrimitive>>();
  }

  public String toString() {
    return name;
  }

  public String getName() {
    return name;
  }

  public Map<String, RecognizerConstraint> getConstraints() {
    return constraints;
  }

  protected void addConstraint(RecognizerConstraint c) {
    // 1. Add the constraint to the map so we can access it by name easily.
    constraints.put(c.getName(), c);

    // 2. Retain a list of all the slots used in this constraint, so we can
    // easily get the related constraints to each slot.
    for (String slotName : c.getSlotNames()) {
      String primary = RecognizerConstraint.primary(slotName);
      // ensure there's a place to record the pairing
      if (!slotsToConstraints.containsKey(primary)) {
        slotsToConstraints.put(primary, new HashSet<String>());
      }
      slotsToConstraints.get(primary).add(c.getName());
    }
  }

  protected void addPrimitive(String slotName, RecognizerPrimitive.Type type) {
    slotNames.add(slotName);
    slotTypes.add(type);
    constraints.put(slotName, new TypeConstraint(slotName, type));
  }

  public Collection<RecognizedItem> apply(Collection<RecognizerPrimitive> in) {
    List<RecognizedItem> ret = new ArrayList<RecognizedItem>();
    resetValid();
    setValid(in);
    Stack<String> bindSlot = new Stack<String>();
    Stack<RecognizerPrimitive> bindObj = new Stack<RecognizerPrimitive>();
    fit(0, bindSlot, bindObj, ret);
    return ret;
  }

  /**
   * Attempts to fit a slot binding to this shape. This is a recursive function. Sometimes it does
   * not have enough information to evaluate a slot binding (e.g. not all slots are full), so it
   * will add more information and recurse. The current slot binding is stored in the bindObj
   * stack---the elements correspond with the slots as described in the bindSlot stack.
   * 
   * In an absolutely worst case scenario, given N slots, there are N! possible configurations.
   * Happily the search space can be pruned pretty aggressively. Primitives of the wrong type are
   * not included. As soon as a constraint has the necessary information it can test to see if it
   * passes. If it does not, any further exploration in this branch would be dumb, so it turns back.
   * 
   * @param slotIndex
   *          the current index that we will modify. Only one slot is changed when this is called.
   * @param bindSlot
   *          a stack of slot names that are bound. the values are in the bindObj stack.
   * @param bindObj
   *          a stack of slot values that are bound. the names are in the bindSlot stack.
   * @param results
   *          when a shape completely matches (all constraints pass and all slots are filled)
   */
  private void fit(int slotIndex, Stack<String> bindSlot, Stack<RecognizerPrimitive> bindObj,
      List<RecognizedItem> results) {
    String topSlot = slotNames.get(slotIndex);
    bindSlot.push(topSlot);
    // Each slot might have several possible values. Iterate through them all.
    for (RecognizerPrimitive p : validSlotCandidates.get(topSlot)) {
      // avoid adding the same primitive to the slot binding if it is already present.
      if (!bindObj.contains(p)) {
        bindObj.push(p); // add this primitive to the current binding (for slot "slotIndex")
        // Sometimes shapes can be flipped---reversing p1 and p2. If a shape has been flipped
        // it is polite to undo that later, at the end of the fit() function.
        boolean[] revertFixedState = new boolean[bindObj.size()];
        for (int i = 0; i < revertFixedState.length; i++) {
          revertFixedState[i] = !bindObj.get(i).isFlippable();
        }
        Certainty result = evaluate(bindSlot, bindObj); // sees if the current slot binding works.
        if (result != Certainty.No) {
          if (slotIndex + 1 < slotNames.size()) {
            // there are unexplored branches below here. go do the next one!
            fit(slotIndex + 1, bindSlot, bindObj, results);
          } else {
            // all slots are filled, so we've found a combination that works.
            boolean ok = true;
            for (RecognizedItem item : results) {
              if (item.containsAll(bindObj)) {
                ok = false;
                break;
              }
            }
            if (ok) {
              bug("About to make recognized shape on evaluate result: " + result);
              RecognizedItem item = new RecognizedItem(this, bindSlot, bindObj);
              Toolkit.getDefaultToolkit().beep();
              results.add(item);
            }
          }
        }
        for (int i = 0; i < bindObj.size(); i++) {
          RecognizerPrimitive prim = bindObj.get(i);
          prim.setSubshapeBindingFixed(revertFixedState[i]);
        }
        bindObj.pop();
      }
    }
    bindSlot.pop();
  }

  private Certainty evaluate(Stack<String> bindSlot, Stack<RecognizerPrimitive> bindObj) {
    Certainty ret = Certainty.Unknown;
    String topSlot = bindSlot.peek(); // the most recently added slot
    Set<String> constraintNames = slotsToConstraints.get(topSlot);
    if (constraintNames == null) {
      ret = Certainty.Yes; // The shape has no constraints to disqualify this item
    } else {
      // Examine all constraints related to the most recently added slot. See if they can tell 
      // us if we should continue or not by evaluating the ones that have enough info.
      for (String cName : constraintNames) {
        RecognizerConstraint c = constraints.get(cName);
        List<String> relevantNames = c.getPrimarySlotNames();
        if (bindSlot.containsAll(relevantNames)) { // if related slots are bound...
          RecognizerPrimitive[] arguments = c.makeArguments(bindSlot, bindObj);
          ret = c.check(arguments);
          if (ret == Certainty.No) { // give up after the first No.
            break;
          }
        }
      }
    }
    return ret; // the return value is either No (fail) or something else (success);
  }

  /**
   * Resets (or creates) the map of slot names to qualified primitives so it is empty.
   */
  private void resetValid() {
    for (String name : slotNames) {
      if (!validSlotCandidates.containsKey(name)) {
        validSlotCandidates.put(name, new TreeSet<RecognizerPrimitive>());
      }
      validSlotCandidates.get(name).clear();
    }
  }

  /**
   * Establishes which slots each input primitive qualifies for. It does this by finding the
   * TypeConstraint for each slot, and putting all appropriate primitives in its list.
   */
  private void setValid(Collection<RecognizerPrimitive> in) {
    for (String slot : slotNames) {
      for (RecognizerPrimitive p : in) {
        // the TypeConstraint for each slot has the name of the slot. For example for the 
        // 'shaft' slot, the type is Line. Put all Line primitives in the list for 'shaft'.
        if (constraints.get(slot).check(p) == Certainty.Yes) {
          validSlotCandidates.get(slot).add(p);
        }
      }
    }
  }

  private static String getBindingString(Stack<String> names, Stack<RecognizerPrimitive> vals) {
    StringBuilder buf = new StringBuilder();
    if (names.size() != vals.size()) {
      buf.append("stacks have different sizes: " + names.size() + " != " + vals.size());
    } else if (names.size() == 0) {
      buf.append("<no bindings>");
    } else {
      for (int i = 0; i < names.size(); i++) {
        buf.append(names.get(i) + "=" + vals.get(i));
        if (i < names.size() - 1) {
          buf.append(", ");
        }
      }
    }
    return buf.toString();
  }
}