package org.six11.sf.rec;

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

  protected void addConstraint(String constraintName, RecognizerConstraint c) {
    // 1. Add the constraint to the map so we can access it by name easily.
    constraints.put(constraintName, c);

    // 2. Retain a list of all the slots used in this constraint, so we can
    // easily get the related constraints to each slot.
    for (String slotName : c.getSlotNames()) {
      String primary = RecognizerConstraint.primary(slotName);
      // ensure there's a place to record the pairing
      if (!slotsToConstraints.containsKey(primary)) {
        slotsToConstraints.put(primary, new HashSet<String>());
      }
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

  private void fit(int slotIndex, Stack<String> bindSlot, Stack<RecognizerPrimitive> bindObj,
      List<RecognizedItem> results) {
    bug("fit: " + slotIndex + ", slots: " + num(bindSlot, " ") + ", objects: " + num(bindObj, " "));
    String topSlot = slotNames.get(slotIndex);
    bindSlot.push(topSlot);
    bug("Filling top slot for " + getName() + ": " + topSlot);
    for (RecognizerPrimitive p : validSlotCandidates.get(topSlot)) {
      if (!bindObj.contains(p)) {
        bindObj.push(p);
      }
    }
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
}
