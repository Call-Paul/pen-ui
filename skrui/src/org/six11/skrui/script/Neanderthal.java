package org.six11.skrui.script;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Neanderthal extends SkruiScript implements SequenceListener {

  private static int SEG_ID = 1;
  private static final String K_SOME_THING = "some-thing";
  private static final String K_ANOTHER_THING = "another-thing";
  private static final String SCRAP = "Sequence already dealt with";
  private static final String MAIN_SEQUENCE = "main sequence";
  static final String PRIMITIVES = "primitives";

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
  boolean animationInitialized = false;

  @Override
  public void initialize() {
    bug("Neanderthal is alive!");
    output("id", "le", "les", "ae", "aes", "pathLength", "nPoints", "lesDivLength", "aesDivLength",
        "lesDivNPoints", "aesDivNPoints", "aesDivNPointsSquared", "lesDivNPointsSquared");
    // output("id", "lineLenDivCurviLen", "isProbablyLine");
    main.getDrawingSurface().getSoup().addSequenceListener(this);

  }

  private void output(String... stuff) {
    boolean first = true;
    for (String v : stuff) {
      if (!first) {
        System.out.print(", ");
      }
      first = false;
      System.out.print(v);
    }
    System.out.print("\n");
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
      double dist = seq.get(i).distance(seq.get(i + 1));
      double v = seq.get(i).getDouble("path-length") + dist;
      seq.get(i + 1).setDouble("path-length", v);
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
    if (!animationInitialized && main.getScript("Animation") != null) {
      Animation ani = (Animation) main.getScript("Animation");
      ani.startAnimation(main.getDrawingSurface().getBounds(), "png");
      animationInitialized = true;
    }
    SequenceEvent.Type type = seqEvent.getType();
    Sequence seq = seqEvent.getSeq();
    Certainty dotCertainty = Certainty.Unknown;
    Certainty ellipseCertainty = Certainty.Unknown;
    Polyline polyline;
    switch (type) {
      case PROGRESS:
        updatePathLength(seq);
        break;
      case END:
        if (seq.getAttribute(SCRAP) == null) {
          // explicity map points back to their original sequence so we can find it later.
          for (Pt pt : seq) {
            pt.setSequence(MAIN_SEQUENCE, seq);
          }
          // Create a 'primitives' set where dots/ellipses/polyline elements will go.
          seq.setAttribute(PRIMITIVES, new HashSet<Primitive>());
          // All the 'detectXYZ' methods will insert a Primitive into seq's primitives set.
          dotCertainty = detectDot(seq);
          ellipseCertainty = detectEllipse(seq);
          polyline = detectPolyline(seq);
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

          // Now see what the haul was:
          for (Primitive prim : (Set<Primitive>) seq.getAttribute(Neanderthal.PRIMITIVES)) {
            bug(prim.toString());
          }
        }
        break;
    }
  }

  private Polyline detectPolyline(Sequence seq) {
    Polyline ret = new Polyline(seq, (Animation) main.getScript("Animation"));
    DrawingBuffer db = new DrawingBuffer();
    Set<Segment> segs = ret.getSegments();
    Set<Pt> junctions = new HashSet<Pt>();
    // Establish line and arc certainties for each segment.
    for (Segment seg : segs) {
      int segID = SEG_ID++;
      // double le = seg.getLineError();
      // double les = seg.getLineErrorSum();
      // double ae = seg.getArcError();
      // double aes = seg.getArcErrorSum();
      // double pathLength = seg.getLength();
      // double nPoints = seg.getNumPoints();
      // double lesDivLength = les / pathLength;
      // double aesDivLength = aes / pathLength;
      // double lesDivNPoints = les / nPoints;
      // double aesDivNPoints = aes / nPoints;
      // double lesDivNPointsSquared = les / (nPoints * nPoints);
      // double aesDivNPointsSquared = aes / (nPoints * nPoints);
      double lineLenDivCurviLen = seg.getPathLength() / seg.getLineLength();
      // output((double) segID, le, les, ae, aes, pathLength, nPoints, lesDivLength, aesDivLength,
      // lesDivNPoints, aesDivNPoints, aesDivNPointsSquared, lesDivNPointsSquared);
      int midIdx = (seg.start + seg.end) / 2;
      Pt m = new Pt(seq.get(midIdx).x - 30, seq.get(midIdx).y);
      DrawingBufferRoutines.text(db, m, "Segment " + segID, Color.RED.darker().darker());
      if (lineLenDivCurviLen < 1.07) {
        seg.lineCertainty = Certainty.Yes;
        seg.arcCertainty = Certainty.Maybe;
      } else if (lineLenDivCurviLen < 1.27) {
        seg.lineCertainty = Certainty.Maybe;
        seg.arcCertainty = Certainty.Maybe;
      } else {
        seg.lineCertainty = Certainty.No;
        seg.arcCertainty = Certainty.Yes;
      }
    }
    for (Segment seg : segs) {
      Pt a = seg.getStartPoint();
      Pt b = seg.getEndPoint();
      junctions.add(a);
      junctions.add(b);
      Pt m = seq.get((seg.end + seg.start) / 2);
      m = new Pt(m.x, m.y + 20);
      m = maybeOutputDebugText(db, "Line", seg.lineCertainty, m);
      m = maybeOutputDebugText(db, "Arc", seg.arcCertainty, m);
    }
    for (Pt pt : junctions) {
      DrawingBufferRoutines.dot(db, pt, 6.0, 0.6, Color.BLACK, Color.GREEN);
    }
    main.getDrawingSurface().getSoup().addBuffer(db);
    for (Segment seg : segs) {
      if (seg.lineCertainty == Certainty.Yes || seg.lineCertainty == Certainty.Maybe) {
        new LineSegment(seg.seq, seg.start, seg.end, seg.lineCertainty);
      }
      if (seg.arcCertainty == Certainty.Yes || seg.arcCertainty == Certainty.Maybe) {
        new ArcSegment(seg.seq, seg.start, seg.end, seg.arcCertainty);
      }
    }
    return ret;
  }

  private void output(double... stuff) {
    boolean first = true;
    for (double v : stuff) {
      if (!first) {
        System.out.print(", ");
      }
      first = false;
      System.out.print(Debug.num(v));
    }
    System.out.print("\n");
  }

  private Pt maybeOutputDebugText(DrawingBuffer db, String msg, Certainty c, Pt pt) {
    Pt where = new Pt(pt.x, pt.y);
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
    updatePathLength(seq);
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

    if (ret == Certainty.Yes || ret == Certainty.Maybe) {
      new Ellipse(seq, ret);
    }
    return ret;
  }

  private Certainty detectDot(Sequence seq) {

    ConvexHull hull = new ConvexHull(seq.getPoints());
    Antipodal antipodes = new Antipodal(hull.getHull());
    double density = (double) seq.size() / antipodes.getArea();
    double areaPerAspect = antipodes.getArea() / antipodes.getAspectRatio();
    Certainty cert = Certainty.Unknown;

    if (areaPerAspect < 58) {
      cert = Certainty.Yes;
    } else if (areaPerAspect < 120) {
      cert = Certainty.Maybe;
    } else if (areaPerAspect / (0.3 + density) < 120) {
      cert = Certainty.Maybe;
    } else {
      cert = Certainty.No;
    }
    if (cert == Certainty.Yes || cert == Certainty.Maybe) {
      new Dot(seq, cert); // will insert the dot into seq's primitives list.
    }

    return cert;
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
