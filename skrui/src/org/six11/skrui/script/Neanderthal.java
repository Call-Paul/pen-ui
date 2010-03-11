package org.six11.skrui.script;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.six11.skrui.BoundedParameter;
import org.six11.skrui.DrawingBufferRoutines;
import org.six11.skrui.SkruiScript;
import org.six11.skrui.data.AngleGraph;
import org.six11.skrui.data.LengthGraph;
import org.six11.skrui.data.PointGraph;
import org.six11.skrui.data.TimeGraph;
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
  TimeGraph tg;
  AngleGraph ag;
  PointGraph allPoints;
  PointGraph endPoints;
  LengthGraph lenG;

  @Override
  public void initialize() {
    bug("Neanderthal is alive!");
    allPoints = new PointGraph();
    endPoints = new PointGraph();
    tg = new TimeGraph();
    ag = new AngleGraph();
    lenG = new LengthGraph();
    main.getDrawingSurface().getSoup().addSequenceListener(this);
  }

  @SuppressWarnings("unused")
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

  @SuppressWarnings("unused")
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

  @SuppressWarnings("unused")
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

  @SuppressWarnings("unchecked")
  public void handleSequenceEvent(SequenceEvent seqEvent) {
    if (!animationInitialized && main.getScript("Animation") != null) {
      Animation ani = (Animation) main.getScript("Animation");
      ani.startAnimation(main.getDrawingSurface().getBounds(), "png");
      animationInitialized = true;
    }
    SequenceEvent.Type type = seqEvent.getType();
    Sequence seq = seqEvent.getSeq();
    switch (type) {
      case PROGRESS:
        updatePathLength(seq);
        break;
      case END:
        if (seq.getAttribute(SCRAP) == null) {
          tg.add(seq);
          // explicity map points back to their original sequence so we can find it later.
          for (Pt pt : seq) {
            pt.setSequence(MAIN_SEQUENCE, seq);
          }
          allPoints.addAll(seq);
          // Create a 'primitives' set where dots/ellipses/polyline elements will go.
          seq.setAttribute(PRIMITIVES, new HashSet<Primitive>());
          // All the 'detectXYZ' methods will insert a Primitive into seq's primitives set.
          detectDot(seq);
          detectEllipse(seq);
          detectPolyline(seq);

          for (Primitive prim : (Set<Primitive>) seq.getAttribute(Neanderthal.PRIMITIVES)) {
            endPoints.add(prim.getSeq().get(prim.getStartIdx()));
            endPoints.add(prim.getSeq().get(prim.getEndIdx()));
            if (prim instanceof LineSegment) {
              ag.add((LineSegment) prim);
            }
            lenG.add(prim);
          }

          // DEBUGGING stuff below here. comment out if you want.

          // drawParallelPerpendicular(seq);
          // drawAdjacent(seq);
          drawSimilarLength(seq);
        }
        break;
    }
  }

  @SuppressWarnings("unchecked")
  private void drawSimilarLength(Sequence seq) {
    DrawingBuffer db = new DrawingBuffer();
    boolean dirty = false;
    for (Primitive prim : (Set<Primitive>) seq.getAttribute(Neanderthal.PRIMITIVES)) {
      dirty = true;
      DrawingBufferRoutines.patch(db, seq, prim.getStartIdx(), prim.getEndIdx(), 2.0, Color.RED);
      double pathLength = prim.getSeq().getPathLength(prim.getStartIdx(), prim.getEndIdx());
      Set<Primitive> similar = lenG.getSimilar(pathLength, pathLength * 0.25);
      for (Primitive adj : similar) {
        if ((adj.getSeq() == prim.getSeq() && adj.getStartIdx() == prim.getStartIdx()
            && adj.getEndIdx() == prim.getEndIdx()) == false) {
          DrawingBufferRoutines.patch(db, adj.getSeq(), adj.getStartIdx(), adj.getEndIdx(), 2.0,
              Color.GREEN);
        }
      }
    }
    if (dirty) {
      main.getDrawingSurface().getSoup().addBuffer("similength", db);
    } else {
      main.getDrawingSurface().getSoup().removeBuffer("similength");
    }

  }

  private void drawAdjacent(Sequence seq) {
    DrawingBuffer db = new DrawingBuffer();
    boolean dirty = false;
    for (Primitive prim : (Set<Primitive>) seq.getAttribute(Neanderthal.PRIMITIVES)) {
      dirty = true;
      // DrawingBufferRoutines.patch(db, seq, prim.getStartIdx(), prim.getEndIdx(), 2.0, Color.RED);
      double pathLength = prim.getSeq().getPathLength(prim.getStartIdx(), prim.getEndIdx());
      double radius = pathLength * 0.20;
      Set<Pt> ends = endPoints.getNear(prim.getSeq().get(prim.getStartIdx()), radius);
      ends.addAll(endPoints.getNear(prim.getSeq().get(prim.getEndIdx()), radius));
      Set<Primitive> adjacentPrims = getPrimitives(ends);
      adjacentPrims.remove(prim);
      for (Primitive adj : adjacentPrims) {
        DrawingBufferRoutines.patch(db, adj.getSeq(), adj.getStartIdx(), adj.getEndIdx(), 2.0,
            Color.GREEN);
      }
    }
    if (dirty) {
      main.getDrawingSurface().getSoup().addBuffer("adjacent", db);
    } else {
      main.getDrawingSurface().getSoup().removeBuffer("adjacent");
    }
  }

  @SuppressWarnings("unchecked")
  private void drawParallelPerpendicular(Sequence seq) {
    DrawingBuffer db = new DrawingBuffer();
    boolean dirty = false;
    for (Primitive prim : (Set<Primitive>) seq.getAttribute(Neanderthal.PRIMITIVES)) {
      if (prim instanceof LineSegment) {
        dirty = true;
        DrawingBufferRoutines.patch(db, seq, prim.getStartIdx(), prim.getEndIdx(), 2.0, Color.RED);
        Set<LineSegment> set = ag.getNear((LineSegment) prim, 0, 0.2);
        // bug(set.size() + " are parallel to " + ((LineSegment) prim).getFixedAngle() + ": ");
        for (LineSegment p : set) {
          // bug("  " + p.getFixedAngle());
          DrawingBufferRoutines.patch(db, p.getSeq(), p.getStartIdx(), p.getEndIdx(), 2.0,
              Color.BLUE);
        }
        set = ag.getNear((LineSegment) prim, Math.PI / 2, 0.2);
        // bug(set.size() + " are perpendicular to " + ((LineSegment) prim).getFixedAngle() + ": ");
        for (LineSegment p : set) {
          // bug("  " + p.getFixedAngle());
          DrawingBufferRoutines.patch(db, p.getSeq(), p.getStartIdx(), p.getEndIdx(), 2.0,
              Color.GREEN);
        }
      }
    }
    if (dirty) {
      main.getDrawingSurface().getSoup().addBuffer("paraperp", db);
    } else {
      main.getDrawingSurface().getSoup().removeBuffer("paraperp");
    }
  }

  @SuppressWarnings("unchecked")
  private Set<Primitive> getPrimitives(Set<Pt> points) {
    // each point has a main sequence. that sequence has a list of primitives. a point may be in
    // zero, one, or more of them. Return all primitives related to the input points.
    Set<Primitive> ret = new HashSet<Primitive>();
    for (Pt pt : points) {
      Sequence seq = pt.getSequence(MAIN_SEQUENCE);
      int where = seq.indexOf(pt);
      for (Primitive prim : (Set<Primitive>) seq.getAttribute(Neanderthal.PRIMITIVES)) {
        if (where <= prim.getEndIdx() && where >= prim.getStartIdx()) {
          ret.add(prim);
        }
      }
    }
    return ret;
  }

  private Polyline detectPolyline(Sequence seq) {
    Polyline ret = new Polyline(seq, (Animation) main.getScript("Animation"));
    Set<Segment> segs = ret.getSegments();
    // Establish line and arc certainties for each segment.
    for (Segment seg : segs) {
      double lineLenDivCurviLen = seg.getPathLength() / seg.getLineLength();
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
      if (seg.lineCertainty == Certainty.Yes || seg.lineCertainty == Certainty.Maybe) {
        new LineSegment(seg.seq, seg.start, seg.end, seg.lineCertainty);
      }
      if (seg.arcCertainty == Certainty.Yes || seg.arcCertainty == Certainty.Maybe) {
        new ArcSegment(seg.seq, seg.start, seg.end, seg.arcCertainty);
      }
    }
    return ret;
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
