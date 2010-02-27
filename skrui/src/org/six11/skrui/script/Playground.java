package org.six11.skrui.script;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

import org.six11.skrui.BoundedParameter;
import org.six11.skrui.DrawingBufferRoutines;
import org.six11.skrui.SkruiScript;
import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.SequenceEvent;
import org.six11.util.pen.SequenceListener;
import static java.lang.Math.cos;
import static java.lang.Math.sin;

/**
 * This is a 'hello world' implementation of DrawingScript. To use it, just mention it on the Main
 * command line.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Playground extends SkruiScript implements SequenceListener {

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
    Debug.out("Playground", what);
  }

  // ///
  //
  // * Put member variable declarations here. *
  //
  // //

  @Override
  public void initialize() {
    bug("Playground is alive!");
    main.getDrawingSurface().getSoup().addSequenceListener(this);
  }

  public void handleSequenceEvent(SequenceEvent seqEvent) {
    if (seqEvent.getType() == SequenceEvent.Type.END) {
      bug("Sequence received.");
      Sequence seq = seqEvent.getSeq();
      DrawingBuffer db = new DrawingBuffer();
      placeEllipse(db, seq.getFirst());
      main.getDrawingSurface().getSoup().addBuffer(db);
    }
  }

  private Pt getEllipticalPoint(Pt center, double a, double b, double ellipseRotation, double t) {
    double x = (a * cos(t));
    double y = (b * sin(t));
    double xRot = x * cos(ellipseRotation) + y * sin(ellipseRotation);
    double yRot = -x * sin(ellipseRotation) + y * cos(ellipseRotation);
    return new Pt(xRot + center.x, yRot + center.y);
  }

  private Pt getEllipticalIntersection(Pt center, double a, double b, double ellipseRotation,
      Pt target) {
    double x0 = target.x - center.x;
    double y0 = target.y - center.y;
    double xRot = x0 * cos(-ellipseRotation) + y0 * sin(-ellipseRotation);
    double yRot = -x0 * sin(-ellipseRotation) + y0 * cos(-ellipseRotation);
    double denom = Math.sqrt((a * a * yRot * yRot) + (b * b * xRot * xRot));
    double xTermRot = (a * b * xRot) / denom;
    double yTermRot = (a * b * yRot) / denom;
    double xTerm = xTermRot * cos(ellipseRotation) + yTermRot * sin(ellipseRotation);
    double yTerm = -xTermRot * sin(ellipseRotation) + yTermRot * cos(ellipseRotation);
    Pt xNeg = new Pt(-xTerm + center.x, -yTerm + center.y);
    Pt xPos = new Pt(xTerm + center.x, yTerm + center.y);
    double distNeg = xNeg.distance(target);
    double distPos = xPos.distance(target);
    Pt ret = distNeg < distPos ? xNeg : xPos;
    return ret;
  }

  private void placeEllipse(DrawingBuffer db, Pt measure1) {
    Pt center = new Pt(250, 200);
    double a = 50;
    double b = 120;
    int numSteps = 20;
    float cv = 0;
    double ellipseRotation = Math.PI * 0.25;
    for (int i = 0; i < numSteps; i++) {
      double angle = 2 * Math.PI * (double) i / (double) numSteps;
      Pt edge = getEllipticalPoint(center, a, b, ellipseRotation, angle);
      bug(Debug.num(edge));
      cv = (float) i / (float) numSteps;
      DrawingBufferRoutines.dot(db, edge, 5.0, 1.0, Color.BLACK, new Color(cv, cv, cv));
    }
    // draw the centroid as a huge dot
    DrawingBufferRoutines.dot(db, center, 15.0, 3.0, Color.BLACK, Color.RED);

    // draw a line from the centroid to the points.
    DrawingBufferRoutines.line(db, center, measure1, Color.GREEN, 2.0);
    // DrawingBufferRoutines.line(db, center, measure2, Color.BLUE, 2.0);

    // get the intersection of the ellipse and the line
    Pt spot = getEllipticalIntersection(center, a, b, ellipseRotation, measure1);
    DrawingBufferRoutines.dot(db, spot, 10.0, 2.0, Color.BLACK, Color.GREEN);
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
