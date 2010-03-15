package org.six11.skrui.constraint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.six11.skrui.script.Neanderthal;
import org.six11.skrui.script.Primitive;
import org.six11.skrui.script.Neanderthal.Certainty;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public abstract class Constraint {

  List<String> slotNames;
  String name;
  Neanderthal data;

  public Constraint(String name, Neanderthal data, String... sNames) {
    this.name = name;
    this.data = data;
    slotNames = new ArrayList<String>();
    for (String n : sNames) {
      slotNames.add(n);
    }
  }

  public abstract Certainty check(Primitive[] p);

  public List<String> getSlotNames() {
    return slotNames;
  }

  public String getName() {
    return name;
  }
  
  public abstract String getShortStr();

  public ConstraintSolver getSolver() {
    return new ConstraintSolver(this);
  }

  public int getNumSlots() {
    return slotNames.size();
  }
  


}
