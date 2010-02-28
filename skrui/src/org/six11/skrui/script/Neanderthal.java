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
import org.six11.util.pen.Antipodal;
import org.six11.util.pen.ConvexHull;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Pt;
import org.six11.util.pen.RotatedEllipse;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.SequenceEvent;
import org.six11.util.pen.SequenceListener;

/**
 * This is a 'hello world' implementation of DrawingScript. To use it, just mention it on the Main
 * command line.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Neanderthal extends SkruiScript implements SequenceListener {

  private static final String K_SOME_THING = "some-thing";
  private static final String K_ANOTHER_THING = "another-thing";
  private static final String SCRAP = "Sequence already dealt with";

  enum Certainty {
    Yes, No, Maybe, Unknown
  }

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
    Debug.out("Neanderthal", what);
  }

  // ///
  //
  // * Put member variable declarations here. *
  //
  // //

  @Override
  public void initialize() {
    bug("Neanderthal is alive!");
    main.getDrawingSurface().getSoup().addSequenceListener(this);
  }

  private double updatePathLength(Sequence seq) {
    int cursor = seq.size() - 1;
    while (cursor > 0 && !seq.get(cursor).hasAttribute("path-length")) {
      cursor--;
    }
    if (cursor == 0) {
      seq.get(cursor).setDouble("path-length", 0);
    }
    for (int i = cursor; i < seq.size() - 1; i++) {
      double dist = seq.get(cursor).distance(seq.get(cursor + 1));
      seq.get(cursor + 1).setDouble("path-length", seq.get(cursor).getDouble("path-length") + dist);
    }
    return seq.get(seq.size() - 1).getDouble("path-length");
  }

  private int sumCertainty(Certainty... certainties) {
    int sum = 0;
    for (Certainty c : certainties) {
      switch (c) {
        case Maybe:
        case Yes:
          sum += 1;
      }
    }
    return sum;
  }

  public void handleSequenceEvent(SequenceEvent seqEvent) {
    SequenceEvent.Type type = seqEvent.getType();
    Sequence seq = seqEvent.getSeq();
    Certainty dotCertainty = Certainty.Unknown;
    Certainty ellipseCertainty = Certainty.Unknown;
    switch (type) {
      case PROGRESS:
        updatePathLength(seq);
        break;
      case END:
        if (seq.getAttribute(SCRAP) == null) {
          dotCertainty = detectDot(seq);
          ellipseCertainty = detectEllipse(seq);
          DrawingBuffer db = new DrawingBuffer();
          Pt cursor = seq.getLast();
          cursor = new Pt(cursor.x + 20, cursor.y + 20);
          cursor = maybeOutputDebugText(db, "Dot", dotCertainty, cursor);
          cursor = maybeOutputDebugText(db, "Ellipse", ellipseCertainty, cursor);
          if (sumCertainty(dotCertainty, ellipseCertainty) > 0) {
            main.getDrawingSurface().getSoup().addBuffer("debug", db);
          } else {
            main.getDrawingSurface().getSoup().removeBuffer("debug");
          }
        }
        break;
    }
  }

  private Pt maybeOutputDebugText(DrawingBuffer db, String msg, Certainty c, Pt endPoint) {
    Pt where = new Pt(endPoint.x, endPoint.y);
    if (c == Certainty.Maybe || c == Certainty.Yes) {

      Color color = c == Certainty.Maybe ? Color.yellow.darker() : Color.green.darker();
      DrawingBufferRoutines.text(db, where, msg + ": " + c, color);
      where = new Pt(where.x, where.y + 20);
    }
    return where;
  }

  private Certainty detectEllipse(Sequence seq) {
    Certainty ret = Certainty.Unknown;

    // First determine if this is a closed shape.
    Pt start = seq.getFirst();
    double totalLength = seq.getLast().getDouble("path-length");
    if (totalLength < 50) {
      ret = Certainty.No;
    } else {
      double lengthThreshold = totalLength * 0.5;

      double endpointDistThreshold = totalLength * 0.30;
      double closestDist = Double.MAX_VALUE;
      Pt closestPoint = null;
      for (Pt pt : seq) {
        double toThisPoint = pt.getDouble("path-length");
        if (toThisPoint > lengthThreshold) {
          double thisDist = start.distance(pt);
          if (thisDist < endpointDistThreshold && thisDist < closestDist) {
            closestPoint = pt;
            closestDist = thisDist;
          }
        }
      }

      if (closestPoint != null) {
        ConvexHull hull = new ConvexHull(seq.getPoints());
        Antipodal antipodes = new Antipodal(hull.getHull());

        Pt centroid = antipodes.getCentroid();
        double angle = antipodes.getAngle();
        double a = antipodes.getFirstDimension() / 2;
        double b = antipodes.getSecondDimension() / 2;
        RotatedEllipse re = new RotatedEllipse(centroid, a, b, angle);
        double errorSum = 0;
        for (Pt pt : seq) {
          Pt intersect = re.getCentroidIntersect(pt);
          double intersectDist = intersect.distance(pt);
          errorSum += intersectDist * intersectDist;
        }
        double normalizedError = errorSum / totalLength;
        double area = re.getArea();
        double areaError = errorSum / area;
        boolean punishLazyPen = closestDist > (endpointDistThreshold / 2);
        if (areaError < 0.4) {
          if (punishLazyPen) {
            ret = Certainty.Maybe;
          } else {
            ret = Certainty.Yes;
          }
        } else if (normalizedError < 9) {
          if (punishLazyPen) {
            ret = Certainty.Maybe;
          } else {
            ret = Certainty.Yes;
          }
        } else if (areaError < 0.9) {
          ret = Certainty.Maybe;
        } else if (normalizedError < 20) {
          ret = Certainty.Maybe;
        } else {
          ret = Certainty.No;
        }
      } else {
        ret = Certainty.No;
      }
    }
    return ret;
  }

  private Certainty detectDot(Sequence seq) {

    ConvexHull hull = new ConvexHull(seq.getPoints());
    Antipodal antipodes = new Antipodal(hull.getHull());
    double density = (double) seq.size() / antipodes.getArea();
    double areaPerAspect = antipodes.getArea() / antipodes.getAspectRatio();
    Certainty ret = Certainty.Unknown;

    if (areaPerAspect < 58) {
      ret = Certainty.Yes;
    } else if (areaPerAspect < 120) {
      ret = Certainty.Maybe;
    } else if (areaPerAspect / (0.3 + density) < 120) {
      ret = Certainty.Maybe;
    } else {
      ret = Certainty.No;
    }

    return ret;
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
