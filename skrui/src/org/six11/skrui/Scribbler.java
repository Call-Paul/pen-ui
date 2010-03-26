package org.six11.skrui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.six11.skrui.data.PointGraph;
import org.six11.skrui.mesh.Mesh;
import org.six11.skrui.script.Neanderthal;
import org.six11.util.Debug;
import org.six11.util.data.Statistics;
import org.six11.util.pen.ConvexHull;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.Vec;

public class Scribbler {

  Neanderthal data;
  // PointGraph pg;
  Statistics linelike;
  Statistics timestamps;
  boolean filling;
  Sequence seq;
  int lastExaminedIndex;
  double halfWindowPixels = 10;
  List<Integer> possibleCornerIndexes;
  List<Pt> hull;
  Mesh mesh;
  Pt lastDrag;
  int cornerNumThreshold = 6;

  public Scribbler(Neanderthal data) {
    this.data = data;
    possibleCornerIndexes = new ArrayList<Integer>();
    // pg = new PointGraph();
    timestamps = new Statistics();
    timestamps.setMaximumN(cornerNumThreshold);
    linelike = new Statistics();
    linelike.setMaximumN(cornerNumThreshold);
  }

  public void sendDown(Sequence seq) {
    this.seq = seq;
    hull = null;
    mesh = null;
    lastDrag = null;
    lastExaminedIndex = -1;
    possibleCornerIndexes.clear();
    linelike.clear();
  }

  public List<Pt> getPossibleCorners() {
    List<Pt> ret = new ArrayList<Pt>();
    for (int idx : possibleCornerIndexes) {
      ret.add(seq.get(idx));
    }
    return ret;
  }

  public void sendDrag() {
    if (!filling) {
      examine();
    } else {
      expandHull();
    }
  }

  private void expandHull() {
    // TODO: This hull stuff is OK, but I am testing to see if Mesh works better
    // hull.add(seq.getLast());
    // hull = new ConvexHull(hull).getHullClosed();
    // DrawingBuffer db = new DrawingBuffer();

    // DrawingBufferRoutines.fill(db, hull, 1, color, color);
    // data.main.getDrawingSurface().getSoup().addBuffer("scribble fill", db);
    // long then = System.currentTimeMillis();
    Pt here = seq.getLast();
    if (here.distance(lastDrag) > 10) {
      lastDrag = here;
      try {
        Color color = data.getDrawingSurface().getSoup().getPenColor();
        mesh.addPointPushBoundary(seq.getLast(), false);

        DrawingBuffer db = new DrawingBuffer();
        List<Pt> boundary = mesh.getBoundary();
        boundary.add(boundary.get(boundary.size() - 1));
        DrawingBufferRoutines.fill(db, boundary, 1.0, color, color);
        data.main.getDrawingSurface().getSoup().addBuffer("scribble fill", db);
        // long now = System.currentTimeMillis();
        // bug("expandHull took " + (now - then) + " ms for mesh of " + mesh.size() + " ("
        // + boundary.size() + " boundary points)");
      } catch (Exception ex) {
        // occasionally seq is null, and I'm not sure why though I suspect threading issues.
      }
    }
  }

  private void examine() {
    int examineMe = -1;
    if (lastExaminedIndex < 0) {
      if (seq.length() > halfWindowPixels) {
        for (int i = 0; i < seq.size(); i++) {
          if (seq.get(i).getDouble("path-length", 0) > halfWindowPixels) {
            examineMe = i;
            break;
          }
        }
      }
    } else {
      examineMe = lastExaminedIndex + 1;
    }
    if (examineMe > 0) {
      int leftIdx = look(examineMe, -1);
      int rightIdx = look(examineMe, 1);
      detectCorner(leftIdx, rightIdx, examineMe);
    }
  }

  private void detectCorner(int leftIdx, int rightIdx, int mid) {
    if (leftIdx >= 0 && rightIdx >= 0) {
      Vec v1 = new Vec(seq.get(mid), seq.get(leftIdx));
      Vec v2 = new Vec(seq.get(mid), seq.get(rightIdx));
      if (v1.mag() > (halfWindowPixels * 0.8) && v1.mag() > (halfWindowPixels * 0.8)) {
        double angle = Math.abs(Functions.getAngleBetween(v1, v2));
        if (angle < 1) {
          seq.get(mid).setDouble("scribbler corner value", angle);
          addPossibleCornerIndex(mid);
        }
      }
      lastExaminedIndex = mid;
    }
  }

  private void addPossibleCornerIndex(int idx) {
    // only need to compare it with the most recently added point, if any. If the path length is
    // closer than the halfWindowPixels value, choose the one with the better angle.
    boolean yes = false;
    if (possibleCornerIndexes.size() > 0) {
      int lastIdx = possibleCornerIndexes.size() - 1;
      int mostRecentIdx = possibleCornerIndexes.get(lastIdx);
      if (pl(idx, mostRecentIdx) < halfWindowPixels) {
        Pt mostRecent = seq.get(mostRecentIdx);
        Pt me = seq.get(idx);
        if (mostRecent.getDouble("scribbler corner value") > me.getDouble("scribbler corner value")) {
          possibleCornerIndexes.remove(lastIdx);
          possibleCornerIndexes.add(idx);
        }
      } else {
        // before adding idx, do some housekeeping on the earlier corners.
        if (possibleCornerIndexes.size() >= 2) {
          // the next to last and last points are irrefutably corners, so we can data to
          // the lengths and pointgraph for them.
          int aIdx = possibleCornerIndexes.get(lastIdx - 1);
          int bIdx = possibleCornerIndexes.get(lastIdx);
          Pt a = seq.get(aIdx);
          Pt b = seq.get(bIdx);
          // pg.add(b);
          timestamps.addData(b.getTime() - a.getTime());
          double idealLength = a.distance(b);
          double pathLength = pl(aIdx, bIdx);
          linelike.addData(idealLength / pathLength);
          yes = true;
        }
        // now add the corner at hand.
        possibleCornerIndexes.add(idx);
      }
    } else {
      possibleCornerIndexes.add(idx);
    }

    if (yes && linelike.getN() == cornerNumThreshold && linelike.getMin() >= 0.9) {
      filling = true;
      seq.setAttribute(Neanderthal.SCRAP, true);
      // hull = makeHull();
      // List<Pt> in = Functions.getNormalizedSequence(seq.getPoints(), 18);
      // mesh = new Mesh(new ConvexHull(seq.getPoints()).getHull(), 0, null);
      // mesh = new Mesh(in, 0, null);
      mesh = new Mesh(seq.getPoints(), 0, null);
      lastDrag = seq.getLast();
      bug("** FILLING **");
    }
  }

  // private List<Pt> makeHull() {
  // long then = System.currentTimeMillis();
  // List<Pt> ret = new ConvexHull(seq.getPoints()).getHullClosed();
  // long now = System.currentTimeMillis();
  // bug("Made hull with " + seq.size() + " points in " + (now - then) + " ms");
  // return ret;
  // }

  private int look(int startIdx, int dir) {
    int ret = -1;
    int cursor = startIdx;
    while (cursor >= 0 && cursor < seq.size()) {
      if (pl(startIdx, cursor) > halfWindowPixels) {
        ret = cursor;
        break;
      } else {
        cursor = cursor + dir;
      }
    }
    return ret;
  }

  /**
   * Get absolute value of path length from a to b.
   */
  private double pl(int idxA, int idxB) {
    return Math
        .abs(seq.get(idxA).getDouble("path-length") - seq.get(idxB).getDouble("path-length"));
  }

  public void sendUp() {
    if (filling) {
      DrawingBuffer db = data.main.getDrawingSurface().getSoup().getBuffer("scribble fill");
      if (db != null) {
        data.main.getDrawingSurface().getSoup().addToLayer("fill", db);
      }
    }
    filling = false;
  }

  private static void bug(String what) {
    Debug.out("Scribbler", what);
  }
}
