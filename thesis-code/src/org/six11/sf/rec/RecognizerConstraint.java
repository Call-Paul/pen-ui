package org.six11.sf.rec;

import java.util.ArrayList;
import java.util.List;

import org.six11.sf.rec.RecognizerPrimitive.Certainty;

public abstract class RecognizerConstraint {

  private List<String> slotNames;
  private List<String> primarySlotNames;
  private String name;
  
  public RecognizerConstraint(String name, String...sNames) {
    this.name = name;
    this.slotNames = new ArrayList<String>();
    this.primarySlotNames = new ArrayList<String>();
    for (String n : sNames) {
      this.slotNames.add(n);
      primarySlotNames.add(primary(n));
    }
  }

  public abstract Certainty check(RecognizerPrimitive... p);
  
  public List<String> getSlotNames() {
    return slotNames;
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


}
