package org.six11.sf;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.six11.util.data.Lists;
import org.six11.util.pen.Pt;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

public class StencilFinder {

  private SketchBook model;
  private Set<Stencil> stencils;

  public StencilFinder(SketchBook model) {
    this.model = model;
    this.stencils = new HashSet<Stencil>();
  }

  public Set<Stencil> findStencils(Collection<SegmentDelegate> newSegs) {
    Stack<Pt> newPoints = new Stack<Pt>();
    for (SegmentDelegate s : newSegs) {
      if (!newPoints.contains(s.getP1())) {
        newPoints.add(s.getP1());
      }
      if (!newPoints.contains(s.getP2())) {
        newPoints.add(s.getP2());
      }
    }
    Stack<Pt> ptPath = new Stack<Pt>();
    Stack<SegmentDelegate> segPath = new Stack<SegmentDelegate>();
    while (!newPoints.isEmpty()) {
      explore(newPoints.pop(), ptPath, segPath);
    }
    return stencils;
  }

  /**
   * Beginning with cursor, explore paths.
   * 
   * @param cursor
   *          the current location
   * @param ptPath
   *          the list of all points explored so far
   * @param segPath
   *          the list of paths taken so far
   */
  private void explore(Pt cursor, Stack<Pt> ptPath, Stack<SegmentDelegate> segPath) {
    //    bug("explore starting at " + SketchBook.n(cursor));
    //    bug("\tpoint path: " + SketchBook.n(ptPath));
    //    bug("\tseg path  : " + SketchBook.ns(segPath));
    if (ptPath.contains(cursor)) {
      maybeAddStencil(ptPath, segPath);
    } else {
      ptPath.push(cursor);
      // get all segments related to the cursor and explore the ones we're not on already.
      Collection<SegmentDelegate> related = model.findRelatedSegments(cursor);
      //      int before = related.size();
      related.removeAll(segPath);
      //      int after = related.size();
      //      bug("Excluding " + (before - after) + " paths starting from " + SketchBook.n(cursor));
      for (SegmentDelegate seg : related) {
        segPath.push(seg);
        Pt nextCursor = seg.getPointOpposite(cursor);
        if (nextCursor != null) {
          explore(nextCursor, ptPath, segPath);
        }
        segPath.pop();
      }
      ptPath.pop();
    }
  }

  private void maybeAddStencil(List<Pt> ptPath, List<SegmentDelegate> segPath) {
    boolean isSame = false;
    for (Stencil s : stencils) {
      if (s.hasPath(segPath)) {
        isSame = true;
        break;
      }
    }
    if (!isSame) {
      stencils.add(new Stencil(model, ptPath, segPath));
    }
  }

  public static void merge(Set<Stencil> rest, Set<Stencil> done) {
    if (!rest.isEmpty()) {
      Stencil s = Lists.removeOne(rest);
      Set<Stencil> kids = new HashSet<Stencil>();
      Set<Stencil> all = new HashSet<Stencil>();
      all.addAll(rest);
      all.addAll(done);
      for (Stencil c : all) {
        if (s.surrounds(c)) {
          kids.add(c);
        }
      }
      if (!kids.isEmpty()) {
        rest.removeAll(kids);
        done.removeAll(kids);
        s.add(kids);
      }
      done.add(s);
      merge(rest, done);
    }
  }

  public Map<Pt, Set<Pt>> makeAdjacency(Set<SegmentDelegate> allGeometry) {
    Map<Pt, Set<Pt>> adjacent = new HashMap<Pt, Set<Pt>>();
    for (SegmentDelegate s : allGeometry) {
      Pt p1 = s.getP1();
      Pt p2 = s.getP2();
      associate(adjacent, p1, p2);
      associate(adjacent, p2, p1);
    }
    return adjacent;

  }

  private void associate(Map<Pt, Set<Pt>> adjacent, Pt p1, Pt p2) {
    if (!adjacent.containsKey(p1)) {
      adjacent.put(p1, new HashSet<Pt>());
    }
    adjacent.get(p1).add(p2);
  }
}
