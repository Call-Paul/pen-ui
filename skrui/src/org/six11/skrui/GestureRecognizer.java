package org.six11.skrui;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.six11.skrui.script.Neanderthal;
import org.six11.skrui.shape.Primitive;
import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;

/**
 * This gesture recognizer looks for time-ordered sequences of input that satisfy constraints. The
 * sequence "dot-line-line" is different from "line-dot-line". A ShapeTemplate is applied to
 * determine if a sequence meets the constraints.
 * 
 * Here's an example of how the constraints are used. "dot-line-line" might have constraints that
 * specify (1) the lines must intersect at approximately their midpoints, (2) the dot must be within
 * one line-length of the line/line intersection, and (3) that the lines be shorter than 30 pixels.
 * If the user provides "line-dot-line", this template will never be checked because the input was
 * provided in the wrong order.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class GestureRecognizer extends SkruiScript {

  GestureRecognizerDomain grDomain;

  @SuppressWarnings("unused")
  private String listPrims(Set<Primitive> set) {
    StringBuilder buf = new StringBuilder();
    for (Primitive p : set) {
      buf.append(p + " ");
    }
    return "{" + buf.toString().trim() + "}";
  }

  private static void bug(String what) {
    Debug.out("GestureRecognizer", what);
  }

  @Override
  public void initialize() {
    bug("Initializing...");
    Neanderthal data = (Neanderthal) main.getScript("Neanderthal");
    grDomain = new GestureRecognizerDomain(data);
    data.addPrimitiveListener(grDomain);
    bug("Initialized. Bring it on, world.");
  }

  @Override
  public Map<String, BoundedParameter> initializeParameters(Arguments args) {
    // TODO: why is this not just a part of SkruiScript?
    Map<String, BoundedParameter> params = copyParameters(getDefaultParameters());
    for (String k : params.keySet()) {
      if (args.hasFlag(k)) {
        if (args.hasValue(k)) {
          params.get(k).setValue(args.getValue(k));
          bug("Set " + params.get(k).getHumanReadableName() + " to " + params.get(k).getValueStr());
        } else {
          params.get(k).setValue("true");
          bug("Set " + params.get(k).getHumanReadableName() + " to " + params.get(k).getValueStr());
        }
      }
    }
    return params;
  }

  public static Arguments getArgumentSpec() {
    Arguments args = new Arguments();
    args.setProgramName("Gesture Recognizer: Look for gestures in user input.");
    args.setDocumentationProgram("Looks for gestures (like dot-line-line) that obey temporal"
        + " and geometric constraints.");

    Map<String, BoundedParameter> defs = getDefaultParameters();
    for (String k : defs.keySet()) {
      BoundedParameter p = defs.get(k);
      args.addFlag(p.getKeyName(), ArgType.ARG_OPTIONAL, ValueType.VALUE_REQUIRED, p
          .getDocumentation()
          + " Defaults to " + p.getValueStr() + ". ");
    }
    return args;
  }

  public static Map<String, BoundedParameter> getDefaultParameters() {
    Map<String, BoundedParameter> defs = new HashMap<String, BoundedParameter>();
    return defs;
  }
}
