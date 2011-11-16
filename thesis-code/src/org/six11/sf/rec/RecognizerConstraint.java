package org.six11.sf.rec;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.six11.sf.rec.RecognizerPrimitive.Certainty;
import static org.six11.util.Debug.num;
import static org.six11.util.Debug.bug;

public abstract class RecognizerConstraint {

  private List<String> slotNames;
  private List<String> primarySlotNames;
  private String name;
  private boolean debugging;

  public RecognizerConstraint(String name, String... sNames) {
    this.name = name;
    this.slotNames = new ArrayList<String>();
    this.primarySlotNames = new ArrayList<String>();
    for (String n : sNames) {
      this.slotNames.add(n);
      primarySlotNames.add(primary(n));
    }
  }
  
  public void say(String what) {
    if (debugging) {
      bug(what);
    }
  }

  public abstract Certainty check(RecognizerPrimitive... p);

  public List<String> getSlotNames() {
    return slotNames;
  }

  public List<String> getPrimarySlotNames() {
    return primarySlotNames;
  }

  public static String primary(String slotName) {
    // lineA.p1 --> lineA
    // lineA --> lineA
    String ret = slotName;
    if (slotName.indexOf('.') > 0) {
      ret = slotName.substring(0, slotName.indexOf('.'));
    }
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
  
  public static boolean ok(Certainty certainty) {
    return (certainty == Certainty.Yes || certainty == Certainty.Maybe);
  }


  /**
   * Create an array that can be used for the check() method. It does this by using the names the
   * constraint knows about and pulling the values from the input structures.
   * 
   * @param slots
   *          the names of the currently bound slots
   * @param primitives
   *          the primitives corresponding to the currently bound slots
   * @return An array of primitives that are in the same order that the check() method expects.
   */
  public RecognizerPrimitive[] makeArguments(Stack<String> slots,
      Stack<RecognizerPrimitive> primitives) {
    RecognizerPrimitive[] ret = new RecognizerPrimitive[slotNames.size()];
    for (int i = 0; i < primarySlotNames.size(); i++) {
      String requiredName = primarySlotNames.get(i);
      int where = slots.indexOf(requiredName);
      ret[i] = primitives.get(where);
    }
    return ret;
  }

  public int getNumSlots() {
    return slotNames.size();
  }

  public String getName() {
    return name;
  }

  public void setDebugging(boolean v) {
    debugging = v;
  }
}
