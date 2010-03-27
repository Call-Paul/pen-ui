package org.six11.skrui.script;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.six11.skrui.BoundedParameter;
import org.six11.skrui.DrawingBufferRoutines;
import org.six11.skrui.SkruiScript;
import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.pen.CardinalSpline;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.SequenceEvent;
import org.six11.util.pen.SequenceListener;

/**
 * This is a 'hello world' implementation of DrawingScript. To use it, just mention it on the Main
 * command line.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Splinify extends SkruiScript implements SequenceListener {

  private static final String K_SOME_THING = "some-thing";
  private static final String K_ANOTHER_THING = "another-thing";

  public static Arguments getArgumentSpec() {
    Arguments args = new Arguments();
    args.setProgramName("A Nice Hello World");
    args.setDocumentationProgram("This serves as a template for new scripts. Copy away!");

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
    defs.put(K_SOME_THING, new BoundedParameter.Double(K_SOME_THING, "Some Thing",
        "This is some sort of double-precision floating point parameter.", 0, 1, 0.75));
    defs.put(K_ANOTHER_THING, new BoundedParameter.Double(K_ANOTHER_THING, "Another Thing",
        "This is another parameter.", 1, 5, 2.5));
    return defs;
  }

  private static void bug(String what) {
    Debug.out("Splinify", what);
  }

  // ///
  //
  // * Put member variable declarations here. *
  //
  // //

  @Override
  public void initialize() {
    bug("Splinify is alive!");
    main.getDrawingSurface().getSoup().addSequenceListener(this);
  }

  public void handleSequenceEvent(SequenceEvent seqEvent) {
    if (seqEvent.getType() == SequenceEvent.Type.END) {
      // List<Pt> ctrl = Functions.getNormalizedSequence(seqEvent.getSeq().getPoints(), 30.0);
      List<Pt> ctrl = Functions.getTimeNormalizedSequence(seqEvent.getSeq().getPoints(), 100);
      List<Pt> interp = CardinalSpline.interpolateCardinal(ctrl, 1.0, 1.5);
      DrawingBuffer db = new DrawingBuffer();
      for (Pt pt : ctrl) {
        DrawingBufferRoutines.dot(db, pt, 6.0, 0.5, Color.BLACK, Color.RED);
      }
      DrawingBufferRoutines.lines(db, interp, Color.BLUE, 1.8);
      // The following tests named access to drawing buffers.
      main.getDrawingSurface().getSoup().removeBuffer("splines");
      main.getDrawingSurface().getSoup().addBuffer("splines", db);
    }
  }
  
  public Map<String, BoundedParameter> initializeParameters(Arguments args) {
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
}
