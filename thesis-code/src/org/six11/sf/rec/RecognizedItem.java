package org.six11.sf.rec;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import org.six11.sf.Segment;
import org.six11.sf.rec.RecognizerPrimitive.Certainty;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Pt;
import org.six11.util.solve.Constraint;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

public class RecognizedItem {

  private RecognizedItemTemplate template;

  private Map<String, RecognizerPrimitive> subshapes;
  private Map<String, Boolean> flipState;
  private Map<String, Certainty> constraints;
  private Map<String, Pt> featurePoints;
  private Map<String, Object> targets;
  private String debugString;

  /**
   * Instantiate a shape using the given template. This assumes that all the necessary slots are
   * present and bound. On exit, all bindings are copied and stored, and constraint certainties are
   * recorded.
   * 
   * @param template
   *          the source template (e.g. 'Arrow')
   * @param bindSlot
   *          names of slots. each element corresponds to elements of bindObj.
   * @param bindObj
   *          values of slots. each element corresponds to elements in bindSlot.
   */
  public RecognizedItem(RecognizedItemTemplate template, Stack<String> bindSlot,
      Stack<RecognizerPrimitive> bindObj) {
    this.template = template;
    this.subshapes = new HashMap<String, RecognizerPrimitive>();
    this.featurePoints = new HashMap<String, Pt>();
    this.constraints = new HashMap<String, Certainty>();
    this.targets = new HashMap<String, Object>();
    for (String cName : template.getConstraints().keySet()) {
      RecognizerConstraint c = template.getConstraints().get(cName);
      if (c.getNumSlots() > 1) {
        RecognizerPrimitive[] arguments = c.makeArguments(bindSlot, bindObj);
        Certainty result = c.check(arguments);
        constraints.put(cName, result);
      }
    }
    
    
    for (int i = 0; i < bindSlot.size(); i++) {
      String slot = bindSlot.get(i);
      subshapes.put(bindSlot.get(i), bindObj.get(i));

    }

    // make a debugging string.
    StringBuffer buf = new StringBuffer();
    buf.append(subshapes.size() + " shapes: ");
    for (String sName : subshapes.keySet()) {
      buf.append("[" + sName + "=" + subshapes.get(sName).toString() + " <"
          + subshapes.get(sName).getCert() + ">] ");
    }
    buf.append(constraints.size() + " constraints: ");
    for (String cName : constraints.keySet()) {
      buf.append("[" + cName + "=" + constraints.get(cName) + "] ");
    }
    debugString = template.getName() + " " + buf.toString();
  }

  public String toString() {
    return debugString;
  }

  public Collection<RecognizerPrimitive> getSubshapes() {
    return subshapes.values();
  }

  public RecognizerPrimitive getSubshape(String name) {
    return subshapes.get(name);
  }

  public RecognizedItemTemplate getTemplate() {
    return template;
  }

  public boolean containsAll(Stack<RecognizerPrimitive> otherShapes) {
    return subshapes.values().containsAll(otherShapes);
  }

  public Pt getFeaturePoint(String key) {
    return featurePoints.get(key);
  }

  public void setFeaturedPoint(String key, Pt pt) {
    featurePoints.put(key, pt);
  }

  public void addTarget(String targetKey, Segment seg) {
    targets.put(targetKey, seg);
  }
  
  public Segment getSegmentTarget(String targetKey) {
    return (Segment) targets.get(targetKey);
  }  
}
