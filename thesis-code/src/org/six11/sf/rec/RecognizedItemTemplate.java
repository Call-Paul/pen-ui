package org.six11.sf.rec;

import java.awt.geom.Area;
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

import org.six11.sf.Ink;
import org.six11.sf.SketchBook;
import org.six11.sf.SketchRecognizer;
import org.six11.sf.rec.RecognizerPrimitive.Certainty;
import org.six11.util.pen.ConvexHull;

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
   * Map of recognized parts (e.g. "shaft.p1") to higher level item feature points (e.g. "arrowTip")
   */
  protected Map<String, String> pointBindings;

  /**
   * Holds a set of candidates that could potentially fill a given slot. This is used to restrict
   * the search. For example, if slot 'shaft' requries a line, the set of candidates will only
   * include lines, but no dots.
   */
  private Map<String, SortedSet<RecognizerPrimitive>> validSlotCandidates;

  protected boolean debugAll = false;

  public RecognizedItemTemplate(SketchBook model, String name) {
    super(model, Type.Standard);
    this.name = name;
    this.slotNames = new ArrayList<String>();
    this.slotTypes = new ArrayList<RecognizerPrimitive.Type>();
    this.constraints = new HashMap<String, RecognizerConstraint>();
    this.slotsToConstraints = new HashMap<String, Set<String>>();
    this.validSlotCandidates = new HashMap<String, SortedSet<RecognizerPrimitive>>();
    this.pointBindings = new HashMap<String, String>();
  }

  public abstract RecognizedItem makeItem(Stack<String> slots, Stack<RecognizerPrimitive> prims);

  public abstract Certainty checkContext(RecognizedItem item, Collection<RecognizerPrimitive> in);

  /**
   * Subclasses should implement this if they want the constraint to be actualized. At the end it
   * should call model.addUserConstraint(uc) with a user constraint that works well with any others
   * that might already exist. For example if you are adding a same-length constraint that is one
   * among many, make sure you don't make two same-length constraints compete.
   * 
   * @param item
   * @param model
   */
  public void create(RecognizedItem item, SketchBook model) {
  }

  protected void setDebugAll(boolean v) {
    this.debugAll = v;
    for (RecognizerConstraint c : constraints.values()) {
      c.setDebugging(v);
    }
  }

  protected void say(String what) {
    if (debugAll) {
      bug(name + ": " + what);
    }
  }

  public String toString() {
    return name;
  }

  public String getName() {
    return name;
  }

  /**
   * Binds a point from the input set to a 'final' name. For example, a recognized arrow has an
   * arrow tip. But the arrow is composed of a shaft and two heads, and the shaft primitive might be
   * flipped around. When the arrow is recognized and put together, we would like to remember where
   * the arrow head is, rather than remembering which of the two ends of the shaft the arrow head
   * is. In this case, you would bind "shaft.p2" to "arrowHead".
   * 
   * @param sourceName
   * @param destName
   */
  protected void bindPoint(String sourceName, String destName) {
    pointBindings.put(sourceName, destName);
  }

  /**
   * Gives a map of component identifiers (such as "shaft.p1") to higher-level shape identifiers
   * (like "arrowTip").
   */
  public Map<String, String> getPointBindings() {
    return pointBindings;
  }

  public Map<String, RecognizerConstraint> getConstraints() {
    return constraints;
  }

  protected RecognizerConstraint getConstraint(String name) {
    return constraints.get(name);
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

  public Collection<RecognizedItem> applyTemplate(Collection<RecognizerPrimitive> in) {
    say("Applying template with " + in.size() + " primitive input(s): " + num(in, " "));
    List<RecognizedItem> ret = new ArrayList<RecognizedItem>();
    resetValid();
    setValid(in);
    Stack<String> bindSlot = new Stack<String>();
    Stack<RecognizerPrimitive> bindObj = new Stack<RecognizerPrimitive>();
    fit(0, bindSlot, bindObj, ret);
    Set<RecognizedItem> wrongContext = new HashSet<RecognizedItem>();
    for (RecognizedItem item : ret) {
      Certainty cert = checkContext(item, in);
      if (!RecognizerConstraint.ok(cert)) {
        wrongContext.add(item);
      }
    }
    say("Found " + ret.size() + " candidates, but removing " + wrongContext.size()
        + " that lack context.");
    ret.removeAll(wrongContext);
    return ret;
  }

  @Override
  public RecognizedRawItem applyRaw(Ink ink) throws UnsupportedOperationException {
    throw new UnsupportedOperationException("This recognizer can't do raw ink.");
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
    say("trying slot " + slotIndex + " (" + topSlot + ") with slot candidates: "
        + num(validSlotCandidates.get(topSlot), " "));
    bindSlot.push(topSlot);
    // Each slot might have several possible values. Iterate through them all.
    for (RecognizerPrimitive p : validSlotCandidates.get(topSlot)) {
      // avoid adding the same primitive to the slot binding if it is already present.
      say("1...");
      if (!bindObj.contains(p)) {
        say("2...");
        bindObj.push(p); // add this primitive to the current binding (for slot "slotIndex")
        // Sometimes shapes can be flipped---reversing p1 and p2. If a shape has been flipped
        // it is polite to undo that later, at the end of the fit() function.
        boolean[] revertFixedState = new boolean[bindObj.size()];
        for (int i = 0; i < revertFixedState.length; i++) {
          revertFixedState[i] = !bindObj.get(i).isFlippable();
        }
        Certainty result = evaluate(bindSlot, bindObj); // sees if the current slot binding works.
        say("3...");
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
              RecognizedItem item = makeItem(bindSlot, bindObj);
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
      say("4...");
    }
    bindSlot.pop();
  }

  private Certainty evaluate(Stack<String> bindSlot, Stack<RecognizerPrimitive> bindObj) {
    Certainty ret = Certainty.Unknown;
    String topSlot = bindSlot.peek(); // the most recently added slot
    Set<String> constraintNames = slotsToConstraints.get(topSlot);
    Map<String, Certainty> constraintResults = new HashMap<String, Certainty>();
    if (constraintNames == null) {
      say("No constraints to DQ me.");
      ret = Certainty.Yes; // The shape has no constraints to disqualify this item
    } else {
      // Examine all constraints related to the most recently added slot. See if they can tell 
      // us if we should continue or not by evaluating the ones that have enough info.
      say("Examining constraints related to " + topSlot + ": " + num(constraintNames, " "));
      for (String cName : constraintNames) {
        RecognizerConstraint c = constraints.get(cName);
        List<String> relevantNames = c.getPrimarySlotNames();
        if (bindSlot.containsAll(relevantNames)) { // if related slots are bound...
          RecognizerPrimitive[] arguments = c.makeArguments(bindSlot, bindObj);
          ret = c.check(arguments);
          constraintResults.put(cName, ret);
          if (ret == Certainty.No) { // give up after the first No.
            say("Giving up at " + cName);
            break;
          }
        }
      }
    }
    say("Constraint results for evaluation of top slot: " + topSlot);
    for (Map.Entry<String, Certainty> entry : constraintResults.entrySet()) {
      say(entry.getKey() + " = " + entry.getValue());
    }
    //    for (String cName : constraintResults.keySet()) {
    //      say(cName + " = " + constraintResults.get(cName));
    //    }
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

  public RecognizerPrimitive search(Stack<String> slots, Stack<RecognizerPrimitive> prims,
      String slot) {
    RecognizerPrimitive ret = null;
    for (int i = 0; i < prims.size(); i++) {
      if (slots.get(i).equals(slot)) {
        ret = prims.get(i);
        break;
      }
    }
    return ret;
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

  protected Area getTotalArea(RecognizerPrimitive[] p) {
    ConvexHull hull = new ConvexHull();
    for (RecognizerPrimitive prim : p) {
      hull.addPoints(prim.getP1(), prim.getP2());
    }
    return new Area(hull.getHullShape());
  }

  /**
   * Attempts to resolve a conflict between two recognized items by returning a collection of the
   * items that should be ignored.
   * 
   * @return a collection of recognized items that should be removed from further consideration
   */
  public static Collection<RecognizedItem> resolveConflict(RecognizedItem itemA,
      RecognizedItem itemB) {
    Collection<RecognizedItem> ret = new HashSet<RecognizedItem>();
    String[] types = new String[] {
        itemA.getTemplate().getName(), itemB.getTemplate().getName()
    };
    if (typesAre(types, RightAngleBrace.NAME, SameLengthGesture.NAME)) {
      ret.addAll(RightAngleBrace.resolveConflictSameLengthGesture(itemA, itemB));
    } else if (typesAre(types, SameAngleGesture.NAME, RightAngleBrace.NAME)) {
      ret.addAll(SameAngleGesture.resolveConflictRightAngleGesture(itemA, itemB));
    } else if (typesAre(types, SameAngleGesture.NAME, SameLengthGesture.NAME)) {
      ret.addAll(SameAngleGesture.resolveConflictSameLength(itemA, itemB));
    } else {
      bug("Don't know how to resolve conflict between " + num(types, " and "));
    }
    return ret;
  }

  private static boolean typesAre(String[] in, String nameA, String nameB) {
    return (in[0].equals(nameA) && in[1].equals(nameB))
        || (in[0].equals(nameB) && in[1].equals(nameA));
  }
}