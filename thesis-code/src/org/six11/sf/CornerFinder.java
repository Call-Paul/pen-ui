package org.six11.sf;

import static java.lang.Math.abs;
import static java.lang.Math.ceil;
import static java.lang.Math.toRadians;
import static org.six11.util.Debug.bug;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.six11.sf.rec.RecognizerPrimitive.Certainty;
import org.six11.util.pen.Antipodal;
import org.six11.util.pen.ConvexHull;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.Vec;

public class CornerFinder {
  public static final double windowSize = 10;
  public static final double highCurvatureThresholdDegrees = 45;
  public static final String SEGMENTS = "segments"; // List<Segment> : lines, ellipses, curves
  public static final double clusterDistanceThreshold = 6;
  public static final String SEGMENT_JUNCTIONS = "junctions"; // List<Integer> : corners
  public static final double minPatchSize = 10;
  public static final double lineErrorThreshold = 1.5;
  public static final double ellipseErrorThreshold = 0.5; // TODO: change
  private SketchBook model;

  public CornerFinder(SketchBook model) {
    this.model = model;
  }

  @SuppressWarnings("unchecked")
  public Set<Segment> findCorners(Ink ink) {
    if (ink.seq.size() > 1) {
      assignCurvature(ink.seq); // put a 'curvature' double attribute at every point
      isolateCorners(ink.seq); // sets the SEGMENT_JUNCTIONS attribute (List<Integer>)
      //    guibug.drawJunctions(ink.seq);
      makeSegments(ink); // sets the SEGMENTS attrib (list of Segments)
    }
    Set<Segment> ret = new HashSet<Segment>();
    List<Segment> allSegs = (List<Segment>) ink.seq.getAttribute(SEGMENTS);
    if (allSegs != null) {
      ret.addAll(allSegs);
    }
    return ret;
  }

  private void assignCurvature(Sequence seq) {
    int n = seq.size();
    Pt[][] windows = new Pt[n][2];
    double targetWindowSize = windowSize / model.getCamera().getZoom();
    for (int i = 0; i < n; i++) {
      windows[i] = Functions.getCurvilinearWindow(seq, i, targetWindowSize);
    }
    for (int i = 0; i < n; i++) {
      Pt me = seq.get(i);
      if ((windows[i][0] != null) && (windows[i][1] != null)) {
        Vec a = new Vec(windows[i][0], me);
        Vec b = new Vec(me, windows[i][1]);
        double curvature = Functions.getSignedAngleBetween(a, b);
        me.setDouble("curvature", curvature);
      } else {
        me.setDouble("curvature", 0);
      }
    }
  }

  /**
   * Sets the sequence's SEGMENT_JUNCTIONS attribute, which is a List<Integer> indicating where
   * segment boundaries are. It includes the endpoints of the stroke.
   */
  private void isolateCorners(Sequence seq) {
    // there will be clusters of high curvature. Pick the one in the curvilinear-wise middle.
    int n = seq.size();
    double highCurvatureThreshold = toRadians(highCurvatureThresholdDegrees);
    List<Integer> highCurvature = new ArrayList<Integer>();
    for (int i = 0; i < n; i++) {
      if (abs(seq.get(i).getDouble("curvature")) > highCurvatureThreshold) {
        highCurvature.add(i);
      }
    }
    List<int[]> clusterBoundaries = new ArrayList<int[]>();
    int lastIdx = 0;
    double clusterSize = 0;
    for (int idx : highCurvature) {
      if (clusterBoundaries.size() > 0) {
        double dist = seq.getPathLength(lastIdx, idx);
        if (dist > clusterDistanceThreshold) {
          // we left the last cluster. finish the last one off and begin a new one.
          int[] bounds = clusterBoundaries.get(clusterBoundaries.size() - 1);
          bounds[2] = lastIdx;
          bounds = new int[] {
              idx, idx, idx
          };
          clusterBoundaries.add(bounds);
          clusterSize = 0;
        } else {
          clusterSize += dist;
          int[] bounds = clusterBoundaries.get(clusterBoundaries.size() - 1);
          bounds[2] = idx;
        }
      } else {
        int[] bounds = new int[] {
            idx, idx, idx
        };
        clusterBoundaries.add(bounds);
        clusterSize = 0;
      }
      lastIdx = idx;
    }
    // get the index of the point that is closest to the middle.
    for (int[] bounds : clusterBoundaries) {
      clusterSize = seq.getPathLength(bounds[0], bounds[2]);
      double dist = 0;
      double target = clusterSize / 2;
      Pt prev = null;
      for (int idx = bounds[0]; idx <= bounds[2]; idx++) {
        Pt here = seq.get(idx);
        if (prev != null) {
          double thisDist = here.distance(prev);
          if ((dist + thisDist) > target) {
            // which is closer?
            if (abs(target - dist) < abs(target - (thisDist + dist))) {
              bounds[1] = idx - 1;
            } else {
              bounds[1] = idx;
            }
            break;
          } else {
            dist = dist + thisDist;
          }
        }
        prev = here;
      }
    }

    List<Integer> junctions = new ArrayList<Integer>();
    junctions.add(0);
    for (int i = 0; i < clusterBoundaries.size(); i++) {
      int[] bounds = clusterBoundaries.get(i);
      junctions.add(bounds[1]);
    }
    junctions.add(seq.size() - 1);
    seq.setAttribute(SEGMENT_JUNCTIONS, junctions);
  }

  @SuppressWarnings("unchecked")
  private void makeSegments(Ink ink) {
    List<Integer> juncts = (List<Integer>) ink.seq.getAttribute(SEGMENT_JUNCTIONS);
    List<Segment> segments = new ArrayList<Segment>();
    Dot dot = detectDot(ink);
    if ((dot.getCertainty() == Certainty.Yes) || (dot.getCertainty() == Certainty.Maybe)) {
      segments.add(new Segment(dot));
    } else {
      for (int i = 0; i < (juncts.size() - 1); i++) {
        Segment seg = identifySegment(ink, juncts.get(i), juncts.get(i + 1));
        // when we find a circle/ellipse/spline disallow other segments 
        // for this ink stroke. they are probably hooks.
        if (seg.isClosed()) {
          segments.clear();
          segments.add(seg);
          break;
        }
        segments.add(seg);
      }
    }
    ink.seq.setAttribute(SEGMENTS, segments);
  }

  private Segment identifySegment(Ink ink, int i, int j) {
    Segment ret = null;
    double segLength = ink.seq.getPathLength(i, j);
    double adjustedMinPatchSize = minPatchSize / model.getCamera().getZoom();
    int numPatches = (int) ceil(segLength / adjustedMinPatchSize);
    double patchLength = segLength / numPatches;
    List<Pt> patch = Functions.getCurvilinearNormalizedSequence(ink.seq, i, j, patchLength)
        .getPoints();
    int a = 0;
    int b = patch.size() - 1;
    Line line = new Line(patch.get(a), patch.get(b));
    double lineError = Functions.getLineError(line, patch, a, b);
    if (lineError < lineErrorThreshold) {
      ret = new Segment(new LineSegment(ink, patch, i == 0, j == (ink.seq.size() - 1)));
    } else {
      double closeness = line.getLength() / segLength;
      boolean closed = closeness < 0.1;
      double ellipseError = Functions.getEllipseError(patch);
      if ((patch.size() > 3) && !closed && (ellipseError < ellipseErrorThreshold)) {
        ret = new Segment(new EllipseArcSegment(ink, patch, i == 0, j == (ink.seq.size() - 1)));
      } else if ((patch.size() > 3) && closed && (ellipseError < (ellipseErrorThreshold * 2.0))) {
        EllipseSegment es = new EllipseSegment(ink, patch);
        double ex = es.getEllipse().getEccentricity();
        bug("Ellipse Eccentricity: " + ex);
        if (ex < 0.7) {
          ret = new Segment(new CircleSegment(ink, patch));
        } else {
          ret = new Segment(es);
        }
      } else if (closed) {
        ret = new Segment(new Blob(ink, patch));
      } else {
        ret = new Segment(new CurvySegment(ink, patch, i == 0, j == (ink.seq.size() - 1)));
      }
    }
    return ret;
  }

  private Dot detectDot(Ink ink) {
    long time = ink.seq.getDuration();
    Dot ret = null;
    if (time < 180) {
      ret = new Dot(ink.seq.getFirst(), Certainty.No);
    } else {
      ConvexHull hull = new ConvexHull(ink.seq.getPoints());
      Antipodal antipodes = new Antipodal(hull.getHull());
      double density = ink.seq.size() / antipodes.getArea();
      double areaPerAspect = antipodes.getArea() / antipodes.getAspectRatio();
      Certainty cert = Certainty.Unknown;
      if (areaPerAspect < 58) {
        cert = Certainty.Yes;
      } else if (areaPerAspect < 120) {
        cert = Certainty.Maybe;
      } else if ((areaPerAspect / (0.3 + density)) < 120) {
        cert = Certainty.Maybe;
      } else {
        cert = Certainty.No;
      }
      ret = new Dot(hull.getConvexCentroid(), cert); // will insert the dot into seq's primitives list.
    }
    return ret;
  }

}
