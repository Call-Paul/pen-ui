package org.six11.skrui.constraint;

import java.util.HashMap;
import java.util.Map;

import org.six11.skrui.shape.Primitive;
import org.six11.skrui.script.Neanderthal.Certainty;

/**
 * This represents a particular slot binding for a shape template.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class SlotBinding {

  Constraint constraint;
  Primitive[] binding;
  Certainty certainty;

  public SlotBinding(Constraint c, Primitive[] binding, Certainty cert) {
    this.constraint = c;
    this.binding = binding;
    this.certainty = cert;
  }

  /**
   * Slot bindings are equivalent if they have the same constraint and binding array. Certainty is
   * not used.
   */
  @Override
  public boolean equals(Object other) {
    boolean ret = false;
    if (other instanceof SlotBinding) {
      SlotBinding o = (SlotBinding) other;
      ret = o.binding.equals(binding) && o.constraint.equals(constraint);
    }
    return ret;
  }

  /**
   * Returns the hash code of the constraint mixed with the binding array.
   */
  @Override
  public int hashCode() {
    return binding.hashCode() * 31 + constraint.hashCode();
  }
  
  public Map<String, Primitive> getBindingMap() {
    HashMap<String, Primitive> ret = new HashMap<String, Primitive>();
    for (int i=0; i < constraint.getSlotNames().size(); i++) {
       ret.put(constraint.getSlotNames().get(i), binding[i]);
    }
    return ret;
  }
  
  public Constraint getConstraint() {
    return constraint;
  }

  public Primitive[] getBinding() {
    return binding;
  }

  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(constraint.getName() + " ");
    boolean first = true;
    for (int i = 0; i < binding.length; i++) {
      if (!first) {
        buf.append(", ");
      }
      first = false;
      String slotName = constraint.getSlotNames().get(i);
      buf.append(slotName + "=" + binding[i].getShortStr());
    }
    return buf.toString();
  }

}