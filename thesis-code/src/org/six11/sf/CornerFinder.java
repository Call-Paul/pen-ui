package org.six11.sf;

import java.util.ArrayList;
import java.util.List;
import static java.lang.Math.abs;
import static java.lang.Math.toRadians;
import static java.lang.Math.ceil;
import static org.six11.util.Debug.bug;

import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.Vec;
import org.six11.util.pen.Line;

public class CornerFinder {
  public static final double windowSize = 10;
  public static final double highCurvatureThresholdDegrees = 45;
  public static final String SEGMENTS = "segments"; // List<SEGMENTS> : corners
  public static final double clusterDistanceThreshold = 6;
  public static final String SEGMENT_JUNCTIONS = "junctions"; // List<Integer> : corners
  public static final double minPatchSize = 10;
  public static final double lineErrorThreshold = 1.5;
  public static final double ellipseErrorThreshold = 0.5; // TODO: change
  
  // these are the keyboard-activated layers, mostly for debugging.
  public static final String DB_RECENT_INK = "1";
  public static final String DB_JUNCTION_LAYER = "2";
  public static final String DB_DOT_LAYER = "3";
  public static final String DB_SEGMENT_LAYER = "4";
  public static final String DB_LATCH_LAYER = "5";
  public static final String DB_COMPLETE_LAYER = "6";
  
  private GraphicDebug guibug;
  
  public CornerFinder(GraphicDebug guibug) {
    this.guibug = guibug;
  }

  public void findCorners(Sequence seq) {
    assignCurvature(seq); // put a 'curvature' double attribute at every point
    isolateCorners(seq); // sets the SEGMENT_JUNCTIONS attribute (List<Integer>)
    guibug.drawJunctions(seq);
    makeSegments(seq); // sets the SEGMENTS attrib (list of Segments)
    // TODO: debugThing.drawSegments((List<Segment>) seq.getAttribute(SEGMENTS));
  }

  private void assignCurvature(Sequence seq) {
    int n = seq.size();
    Pt[][] windows = new Pt[n][2];
    for (int i = 0; i < n; i++) {
      windows[i] = Functions.getCurvilinearWindow(seq, i, windowSize);
    }
    for (int i = 0; i < n; i++) {
      Pt me = seq.get(i);
      if (windows[i][0] != null && windows[i][1] != null) {
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
          if (dist + thisDist > target) {
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
  
  private void makeSegments(Sequence seq) {
    List<Integer> juncts = (List<Integer>) seq.getAttribute(SEGMENT_JUNCTIONS);
    List<Segment> segments = new ArrayList<Segment>();
    for (int i = 0; i < juncts.size() - 1; i++) {
      segments.add(identifySegment(seq, juncts.get(i), juncts.get(i + 1)));
      bug("Segment: " + segments.get(segments.size() - 1));
    }
    seq.setAttribute(SEGMENTS, segments);
  }

  private Segment identifySegment(Sequence seq, int i, int j) {
    Segment ret = null;
    double segLength = seq.getPathLength(i, j);
    int numPatches = (int) ceil(segLength / minPatchSize);
    double patchLength = segLength / (double) numPatches;
    List<Pt> patch = Functions.getCurvilinearNormalizedSequence(seq, i, j, patchLength).getPoints();
    // debugThing.drawPoints(patch);
    int a = 0;
    int b = patch.size() - 1;
    Line line = new Line(patch.get(a), patch.get(b));
    double lineError = Functions.getLineError(line, patch, a, b);
    if (lineError < lineErrorThreshold) {
      ret = new LineSegment(patch, i == 0, j == seq.size() - 1);
    } else if (Functions.getEllipseError(patch) < ellipseErrorThreshold) {
      ret = new EllipseArcSegment(patch, i == 0, j == seq.size() - 1);
    } else {
      ret = new CurvySegment(patch, i == 0, j == seq.size() - 1);
    }
    return ret;
  }
}
