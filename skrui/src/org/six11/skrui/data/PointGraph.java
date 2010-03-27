package org.six11.skrui.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.Timer;

import org.six11.util.Debug;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

/**
 * This is a datastructure that stores x,y,t points and may be efficiently searched to locate other
 * points based on search criteria.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class PointGraph {

  List<Pt> byX;
  List<Pt> byY;

  // public static void main(String[] args) {
  // Debug.useColor = false;
  // Debug.useTime = false;
  // PointGraph pg = new PointGraph();
  // for (int i = 0; i < 50; i++) {
  // for (int j = 0; j < 50; j++) {
  // pg.add(new Pt(Math.sin(i * i), Math.sin(i * j)));
  // }
  // }
  //
  // Pt origin = new Pt(0, 0);
  // double max = 0.4;
  // long start = System.nanoTime();
  // Set<Pt> nearOrigin = pg.getNear(origin, max);
  // long end = System.nanoTime();
  // System.out.println(end - start);
  // long ms = (end - start) / 1000000;
  // bug("Found " + nearOrigin.size() + " of " + pg.size() + " points within " + max
  // + " units of origin in " + ms + " ms");
  // for (Pt pt : nearOrigin) {
  // double dist = pt.distance(origin);
  // bug("  " + Debug.num(pt) + " is " + Debug.num(dist) + " away.");
  // }
  // }

  public int size() {
    return byX.size();
  }

  /**
   * Returns all the points sorted by their X coordinates (Pt.sortByX semantics). The returned value
   * is one of the backing lists, so if you modify it, the world might likely end.
   */
  public Collection<Pt> getPoints() {
    return byX;
  }

  public PointGraph() {
    this.byX = new ArrayList<Pt>();
    this.byY = new ArrayList<Pt>();
  }

  public void add(Pt pt) {
    int where = Collections.binarySearch(byX, pt, Pt.sortByX);
    if (where < 0) {
      where = (where + 1) * -1;
    }
    byX.add(where, pt);

    where = Collections.binarySearch(byY, pt, Pt.sortByY);
    if (where < 0) {
      where = (where + 1) * -1;
    }
    byY.add(where, pt);
  }

  public void remove(Pt pt) {
    byX.remove(pt);
    byY.remove(pt);
  }

  public Set<Pt> getNear(Pt target, double dist) {
    // first, messily get all points that are in a square around the target.
    Set<Pt> ret = getNearX(target, dist);
    ret.retainAll(getNearY(target, dist));
    // Now remove those that aren't strictly in the circle.
    Set<Pt> doomed = new TreeSet<Pt>();
    for (Pt pt : ret) {
      if (target.distance(pt) > dist) {
        doomed.add(pt);
      }
    }
    ret.removeAll(doomed);
    return ret;
  }

  @SuppressWarnings("unused")
  private String getBugString(Collection<Pt> aList) {
    StringBuilder buf = new StringBuilder();
    buf.append("[");
    for (Pt pt : aList) {
      buf.append(Debug.num(pt) + " ");
    }
    return buf.toString().trim() + "]";
  }

  private Set<Pt> getNearX(Pt target, double dist) {
    Set<Pt> xSet = new TreeSet<Pt>(Pt.sortById);
    Pt x1 = new Pt(target.x - dist / 2, Double.MAX_VALUE);
    Pt x2 = new Pt(target.x + dist / 2, Double.MAX_VALUE);
    int idxA = -(Collections.binarySearch(byX, x1, Pt.sortByX));
    int idxB = -(Collections.binarySearch(byX, x2, Pt.sortByX) + 1);
    for (int i = idxA; i < idxB; i++) {
      xSet.add(byX.get(i));
    }
    return xSet;
  }

  private Set<Pt> getNearY(Pt target, double dist) {
    Set<Pt> ySet = new TreeSet<Pt>(Pt.sortById);
    Pt y1 = new Pt(Double.MAX_VALUE, target.y - dist / 2);
    Pt y2 = new Pt(Double.MAX_VALUE, target.y + dist / 2);
    int idxA = -(Collections.binarySearch(byY, y1, Pt.sortByY));
    int idxB = -(Collections.binarySearch(byY, y2, Pt.sortByY) + 1);
    for (int i = idxA; i < idxB; i++) {
      ySet.add(byY.get(i));
    }
    return ySet;
  }

  private static void bug(String what) {
    Debug.out("PointGraph", what);
  }

  public void addAll(Sequence seq) {
    for (Pt pt : seq) {
      add(pt);
    }
  }

  public Pt getNearest(Pt pt) {
    // This is probably a braindead way of finding the nearest point, but it works. I just wanted
    // to avoid iterating through every point.
    if (byX.size() == 0) {
      return null;
    }
    double dist = 10;
    Set<Pt> near = null;
    while (near == null || near.size() == 0) {
      near = getNear(pt, dist);
      dist = dist + 20;
    }
    double bestDist = Double.MAX_VALUE;
    Pt bestPt = null;
    for (Pt zed : near) {
      double thisDist = zed.distance(pt);
      if (thisDist < bestDist) {
        bestPt = zed;
        bestDist = thisDist;
      }
    }
    return bestPt;
  }

}
