package org.six11.skrui.script;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.six11.skrui.BoundedParameter;
import org.six11.skrui.DrawingBufferRoutines;
import org.six11.skrui.GestureRecognizerDomain;
import org.six11.skrui.SkruiScript;
import org.six11.skrui.shape.LineSegment;
import org.six11.skrui.shape.Primitive;
import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.pen.*;

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
public class GestureRecognizer extends SkruiScript implements SequenceListener, HoverListener {

  GestureRecognizerDomain grDomain;
  Neanderthal data;
  HoverEvent lastHoverEvent;


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
  
  private void hideStructuredInk() {
    if (getSoup().getBuffer("structured") != null) {
      getSoup().getBuffer("structured").setVisible(false);
      main.getDrawingSurface().repaint();
    }
  }

  public static double calcAlpha(double dist) {
    double minAlpha = 0.05;
    double maxAlpha = 0.6;
    double minDist = 10;
    double maxDist = 150;
    double ret = 0;
    if (dist < minDist) {
      ret = maxAlpha;
    } else if (dist > maxDist) {
      ret = minAlpha;
    } else {
      ret = maxAlpha - ((dist - minDist) / (maxDist - minDist)) * (maxAlpha - minAlpha);
    }
    return ret;
  }
  
  private void drawStructuredInk() {
    DrawingBuffer db = new DrawingBuffer();
    Pt pen = lastHoverEvent.getPt();
    // Color debugColor = new Color(1f, 0.3f, 0.3f, 1f);
    for (Pt st : data.getStructurePoints().getPoints()) {
      double dist = st.distance(pen);
      double alpha = calcAlpha(dist);
      Color c = new Color(0.6f, 0.6f, 0.6f, (float) alpha);
      DrawingBufferRoutines.cross(db, st, 3.5, c);
    }
    for (LineSegment line : data.getStructureLines()) {
      double dist = Functions.getDistanceBetweenPointAndLine(pen, line.getGeometryLine());
      double alpha = calcAlpha(dist);
      Color c = new Color(0.6f, 0.6f, 0.6f, (float) alpha);
      DrawingBufferRoutines.screenLine(db, main.getDrawingSurface().getBounds(), line
          .getGeometryLine(), c, 0.7);
    }
    List<Pt> rec = data.getStructurePoints().getRecent(30000, pen.getTime());
    Color veryLightGray = new Color(0.6f, 0.6f, 0.6f, (float) 0.3);
    if (rec.size() > 0) {
      // draw a line from pen to the most recent point
      Pt st = rec.get(0);
      DrawingBufferRoutines.line(db, pen, st, veryLightGray, 0.5);
    }
    for (int i = 0; i < rec.size() - 1; i++) {
      // draw lines connecting each dot to the previous
      Pt here = rec.get(i);
      Pt there = rec.get(i + 1);
      Line ghostLine = new Line(here, there);
      Pt mid = ghostLine.getMidpoint();
      DrawingBufferRoutines.line(db, ghostLine, veryLightGray, 0.5);
      DrawingBufferRoutines.cross(db, mid, 2.3, veryLightGray);
    }
    if (rec.size() > 2) {
      // when there are three points, make a circle and show its center.
      Pt center = Functions.getCircleCenter(rec.get(0), rec.get(1), rec.get(2));
      if (center != null) { // if 0-1-2 are colinear, center is null. avoid NPEs
        double radius = rec.get(0).distance(center);
        DrawingBufferRoutines.dot(db, center, radius, 0.5, veryLightGray, null);
        DrawingBufferRoutines.dot(db, center, 3.0, 0.3, veryLightGray, veryLightGray);
      }
    }
    getSoup().addBuffer("structured", db);
  }

  @Override
  public void initialize() {
    bug("Initializing...");
    data = (Neanderthal) main.getScript("Neanderthal");
    grDomain = new GestureRecognizerDomain(data);
    data.addPrimitiveListener(grDomain);
    data.addSequenceListener(this);
    main.getDrawingSurface().getSoup().addHoverListener(this);
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

  public void handleHoverEvent(HoverEvent hoverEvent) {
    lastHoverEvent = hoverEvent;
    whackStructuredInk();
  }

  
  public static Map<String, BoundedParameter> getDefaultParameters() {
    Map<String, BoundedParameter> defs = new HashMap<String, BoundedParameter>();
    return defs;
  }

  /**
   * When the pen moves around inside the drawing area, we want structural ink to be redrawn as a
   * function of what the user is doing, and where the pen is. Consult 'lastHoverEvent' to know
   * what's going on.
   */
  private void whackStructuredInk() {
    if (data.getStructurePoints().size() > 0) {
      if (lastHoverEvent != null) {
        switch (lastHoverEvent.getType()) {
          case In:
          case Hover:
            drawStructuredInk();
            break;
          case Out:
            hideStructuredInk();
            break;
        }
      }
    }
  }
  public void handleSequenceEvent(SequenceEvent seqEvent) {
    SequenceEvent.Type type = seqEvent.getType();
    Sequence seq = seqEvent.getSeq();
    switch (type) {
      case PROGRESS:
        if (lastHoverEvent != null) {
          lastHoverEvent.setPt(seq.getLast());
          whackStructuredInk();
        }
    }
  }
}
