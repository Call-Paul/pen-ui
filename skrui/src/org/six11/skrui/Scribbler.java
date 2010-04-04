package org.six11.skrui;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import javax.swing.Timer;

import org.six11.skrui.mesh.Mesh;
import org.six11.skrui.mesh.Triangle;
import org.six11.skrui.mesh.Where;
import org.six11.skrui.mesh.Mesh.TriangleWhere;
import org.six11.skrui.script.Animation;
import org.six11.skrui.script.Neanderthal;
import org.six11.util.Debug;
import org.six11.util.data.Statistics;
import org.six11.util.pen.ConvexHull;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.Vec;

public class Scribbler {

  Neanderthal data;
  Statistics linelike;
  Statistics timestamps;
  boolean filling;
  Sequence seq;
  int lastExaminedIndex;
  double halfWindowPixels = 10;
  List<Integer> possibleCornerIndexes;
  ConvexHull hull;
  List<Pt> penPath;
  Mesh mesh;
  boolean meshDrawn = false;
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
    // first see if this is a continuation.
    boolean shouldFill = false;
    Pt nub = seq.getLast().getTranslated(0.5, 0.5);
    if (mesh != null && (nub.getTime() - mesh.getTime()) < 4000) {
      ConvexHull h = mesh.getHull();
      if (Functions.isPointInRegion(nub, h.getHullClosed())) {
        shouldFill = true;
      }
    }
    this.seq = seq;
    hull = null;
    penPath = null;
    mesh = null;
    lastDrag = null;
    lastExaminedIndex = -1;
    possibleCornerIndexes.clear();
    linelike.clear();

    if (shouldFill) {
      beginFill();
    }
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

  private void draw(boolean done) {
    DrawingBuffer db = new DrawingBuffer();
    if (db.isVisible() && penPath.size() > 2) {
      Set<Triangle> inside = seekInside();
      DrawingBufferRoutines.triangles(db, inside, data.getSoup().getPenColor());
      if (done) {
        data.addMesh(new Mesh(mesh.getPoints(), 0));
      }
      data.main.getDrawingSurface().getSoup().addBuffer("scribble fill", db);
    }
  }

  private Set<Triangle> seekInside() {
    Set<Triangle> ret = new HashSet<Triangle>();

    // Now get a convex hull for everything, including the original scribble hull and the pen path.
    List<Pt> bigHullPoints = new ArrayList<Pt>(hull.getHull());
    bigHullPoints.addAll(penPath);
    ConvexHull bigHull = new ConvexHull(bigHullPoints);
    bigHullPoints = bigHull.getHull();
    for (Triangle t : mesh.getTriangles()) {
      if (Functions.isPointInRegion(t.getCentroid(), bigHullPoints)) {
        ret.add(t);
      }
    }
    return ret;
  }

  private void beginFill() {
    filling = true;
    seq.setAttribute(Neanderthal.SCRAP, true);
    // hull = new ConvexHull(getPossibleCorners());
    hull = new ConvexHull(seq.getPoints());
    penPath = new ArrayList<Pt>();
    mesh = new Mesh(hull.getHull(), 0);
    draw(false);
    lastDrag = seq.getLast();
    bug("** FILLING **");
  }

  private void expandHull() {
    if (seq.size() > 3) {
      Pt here = seq.getLast();
      if (!Functions.isPointInRegion(here, hull.getHullClosed())) {
        if (here.distance(lastDrag) > 6) {
          lastDrag = here;
          try {
            // Color color = data.getDrawingSurface().getSoup().getPenColor();
            if (mesh.expand(here)) {
              penPath.add(here);
              draw(false);
            } else {
              bug("I did not expand (maybe it contained that point already?");
            }
          } catch (Exception ex) {
            // occasionally seq is null, and I'm not sure why though I suspect threading issues.
            ex.printStackTrace();
          }
        }
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
          seq.get(lastIdx).setBoolean("scribble-actual-corner", false);
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
        seq.get(idx).setBoolean("scribble-possible-corner", true);
        seq.get(idx).setBoolean("scribble-actual-corner", true);
      }
    } else {
      possibleCornerIndexes.add(idx);
    }

    if (yes && linelike.getN() == cornerNumThreshold && linelike.getMin() >= 0.9) {
      beginFill();
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
      draw(true);
      bug("** Finished doing mondo draw.");
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
