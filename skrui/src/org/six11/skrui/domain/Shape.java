package org.six11.skrui.domain;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import org.six11.skrui.constraint.Constraint;
import org.six11.skrui.shape.Primitive;
import org.six11.skrui.script.Neanderthal.Certainty;
import org.six11.util.Debug;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Shape {

  ShapeTemplate template;

  Map<String, Primitive> subshapes;
  Map<String, Certainty> constraints;
  String debugString;

  public Shape(ShapeTemplate shapeTemplate, Stack<String> bindSlot, Stack<Primitive> bindObj) {
    this.template = shapeTemplate;
    subshapes = new HashMap<String, Primitive>();
    for (int i = 0; i < bindSlot.size(); i++) {
      subshapes.put(bindSlot.get(i), bindObj.get(i));
    }
    this.constraints = new HashMap<String, Certainty>();
    for (String cName : template.constraints.keySet()) {
      Constraint c = template.constraints.get(cName);
      if (c.getNumSlots() > 1) {
        Primitive[] args = c.makeArguments(bindSlot, bindObj);
        Certainty result = c.check(args);
        constraints.put(cName, result);
      }
    }
    StringBuffer buf = new StringBuffer();
    for (String sName : subshapes.keySet()) {
      buf.append(sName + "/" + subshapes.get(sName).getShortStr() + "/"
          + subshapes.get(sName).getCert() + " ");
    }
    for (String cName : constraints.keySet()) {
      buf.append(cName + "/" + constraints.get(cName) + " ");
    }
    debugString = template.name + " " + buf.toString();
  }

  public Collection<Primitive> getSubshapes() {
    return subshapes.values();
  }
  
  @SuppressWarnings("unused")
  private static void bug(String what) {
    Debug.out("Shape", what);
  }

  public String toString() {
    return debugString;
  }
  
  public boolean equals(Object other) {
    boolean ret = false;
    if (other instanceof Shape) {
      Shape o = (Shape) other;
      ret = o.template.equals(template) && o.containsAllShapes(subshapes.values());
    }
    return ret;
  }
  
  public boolean containsAllShapes(Collection<Primitive> otherShapes) {
    return subshapes.values().containsAll(otherShapes);
  }

  public String getName() {
    return template.name;
  }
}
