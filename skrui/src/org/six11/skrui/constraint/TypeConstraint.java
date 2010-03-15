package org.six11.skrui.constraint;

import org.six11.skrui.script.Neanderthal;
import org.six11.skrui.script.Primitive;
import org.six11.skrui.script.Neanderthal.Certainty;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class TypeConstraint extends Constraint {
  Class<? extends Primitive> c;

  public TypeConstraint(String slotName, Neanderthal data, Class<? extends Primitive> c) {
    super(slotName, data, slotName);
    this.c = c;
  }

  public Certainty check(Primitive[] p) {
    Certainty ret = Certainty.No;
    if (p.length == 1 && p[0].getClass() == c) {
      ret = Certainty.Yes;
    }
    return ret;
  }

  @Override
  public String getShortStr() {
    return "Type";
  }


}
