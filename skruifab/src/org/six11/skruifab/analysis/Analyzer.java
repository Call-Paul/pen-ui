package org.six11.skruifab.analysis;

import java.util.Set;
import java.util.TreeSet;

import org.six11.util.Debug;
import org.six11.util.pen.Antipodal;
import org.six11.util.pen.ConvexHull;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.RotatedEllipse;

public class Analyzer {

  public static final String PRIMITIVES = "primitives";

  /**
   * tg is a TimeGraph that stores Sequences orderd by time.
   */
  TimeGraph tg;

  /**
   * allPoints stores all visible points associated with lines or filled regions. This makes it more
   * efficient to answer questions like 'which points are near X,Y'.
   */
  PointGraph allPoints;

  public Analyzer() {
    tg = new TimeGraph();
    allPoints = new PointGraph();
  }

  public TimeGraph getTimeGraph() {
    return tg;
  }

  public PointGraph getAllPoints() {
    return allPoints;
  }

  public void processFinishedSequence(Stroke seq) {
    getTimeGraph().add(seq);
    // explicity map points back to their original sequence so we can find it later.
    getAllPoints().addAll(seq);
    // Create a 'primitives' set where dots/ellipses/polyline elements will go.
    seq.setAttribute(PRIMITIVES, new TreeSet<Primitive>(Primitive.sortByIndex));
    // All the 'detectXYZ' methods will insert a Primitive into seq's primitives set.
    detectDot(seq);
    detectVeryShortLines(seq);
    detectEllipse(seq);
    detectPolylineSegments(seq);

    //
    //    for (Primitive prim : getPrimitiveSet(seq)) {
    //      recordPrimitive(prim);
    //    }
    //
    //    // complain when points are not part of a primitive
    //    for (Pt pt : seq) {
    //      TreeSet<Primitive> pointPrims = (TreeSet<Primitive>) pt.getAttribute(PRIMITIVES);
    //      if (pointPrims == null || pointPrims.size() == 0) {
    //        bug("Found lonely point: " + pt.getID() + ": " + Debug.num(pt));
    //        pt.setBoolean("buggy", true);
    //      }
    //    }
    //
    //    firePrimitiveEvent(seq);
  }

  /**
   * Analyze a single stroke to find a sequence of line, circle, and spline segments. It depends on
   * MergeCF to find corners, and uses them as segment boundaries.
   * 
   * @param seq
   */
  private void detectPolylineSegments(Stroke seq) {
    if (!seq.hasAttribute(MergeCF.CORNERS_FOUND)) {
      MergeCF.analyze(seq);
    }
    Set<Segment> segs = seq.getSegments();
    // Establish line certainties for each segment.
    for (Segment seg : segs) {
      double lineLenDivCurviLen = seg.getPathLength() / seg.getLineLength();
      if (lineLenDivCurviLen < 1.07) {
        seg.lineCertainty = Certainty.Yes;
      } else if (lineLenDivCurviLen < 1.27) {
        seg.lineCertainty = Certainty.Maybe;
      } else {
        seg.lineCertainty = Certainty.No;
      }
      if (seg.lineCertainty == Certainty.Yes || seg.lineCertainty == Certainty.No) {
        new LineSegment(seg.seq, seg.start, seg.end, seg.lineCertainty);
      }
    }

  }

  @SuppressWarnings("unused")
  private Certainty detectVeryShortLines(Stroke seq) {
    Certainty cert = Certainty.Unknown;
    if (seq.getPathLength(0, seq.size() - 1) <= MergeCF.DUPLICATE_THRESHOLD) {
      double pathLength = seq.getPathLength(0, seq.size() - 1);
      double lineLength = new Line(seq.getFirst(), seq.getLast()).getLength();
      double lineLenDivCurviLen = pathLength / lineLength;
      if (lineLenDivCurviLen < 1.10) {
        cert = Certainty.Yes;
        new LineSegment(seq, 0, seq.size() - 1, cert);
      } else if (lineLenDivCurviLen < 2.2) {
        cert = Certainty.Maybe;
        new LineSegment(seq, 0, seq.size() - 1, cert);
      } else {
        cert = Certainty.No;
      }
    }
    return cert;
  }

  @SuppressWarnings("unused")
  private Certainty detectDot(Stroke seq) {
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
    bug("Dot? " + cert);

    return cert;
  }

  private Certainty detectEllipse(Stroke seq) {
    Certainty ret = Certainty.Unknown;

    // First determine if this is a closed shape.
    Pt start = seq.getFirst();
    RotatedEllipse re = null;
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
        re = new RotatedEllipse(centroid, a, b, angle);
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
      Ellipse ellie = new Ellipse(seq, ret);
      ellie.setRotatedEllipse(re);
    }
    return ret;
  }

  private double updatePathLength(Stroke seq) {
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

  private void bug(String what) {
    Debug.out("Analyzer", what);
  }
}
