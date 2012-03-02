package org.six11.sf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.six11.util.Debug;
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

  public Set<Stencil> findStencils(Collection<Segment> newSegs) {
//    System.out.println("------------------------------------------------------------------------------------- start");
//    bug("Existing geometry: ");
//    System.out.println(model.getMondoDebugString());
//    bug("New segments: ");
    for (Segment s : newSegs) {
      bug(s.typeIdStr());
    }
    Stack<Pt> newPoints = new Stack<Pt>();
    for (Segment s : newSegs) {
      if (s.isClosed()) {
        stencils.add(new Stencil(model, s));
      }
      if (!newPoints.contains(s.getP1())) {
        newPoints.add(s.getP1());
      }
      if (!newPoints.contains(s.getP2())) {
        newPoints.add(s.getP2());
      }
    }
    Stack<Pt> ptPath = new Stack<Pt>();
    Stack<Segment> segPath = new Stack<Segment>();
    while (!newPoints.isEmpty()) {
      explore(newPoints.pop(), ptPath, segPath, 0);
    }
//    System.out.println("------------------------------------------------------------------------------------- stop");
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
  private void explore(Pt cursor, Stack<Pt> ptPath, Stack<Segment> segPath, int depth) {
//    bug(Debug.spaces(depth) + "(" + depth + ") explore starting at " + SketchBook.n(cursor));
//    bug(Debug.spaces(depth) + "-- point path: " + SketchBook.n(ptPath));
//    bug(Debug.spaces(depth) + "-- seg path  : " + SketchBook.ns(segPath));
    if (ptPath.contains(cursor)) {
      maybeAddStencil(cursor, ptPath, segPath);
    } else {
//      bug(Debug.spaces(depth) + "-- push point: " + SketchBook.n(cursor));
      ptPath.push(cursor);
      // get all segments related to the cursor and explore the ones we're not on already.
      Collection<Segment> related = model.findRelatedSegments(cursor);
      //      int before = related.size();
      related.removeAll(segPath);
      List<Segment> relatedList = new ArrayList<Segment>(related);//.toArray(new Segment[related.size()]);
//      bug(Debug.spaces(depth) + "-- on deck: " + SketchBook.ns(relatedList));
      //      int after = related.size();
      //      bug("Excluding " + (before - after) + " paths starting from " + SketchBook.n(cursor));
      for (Segment seg : relatedList) {
//        bug(Debug.spaces(depth) + "-- following segment " + seg.typeIdStr());
        segPath.push(seg);
        Pt nextCursor = seg.getPointOpposite(cursor);
        if (nextCursor != null) {
          explore(nextCursor, ptPath, segPath, ++depth);
        }
        segPath.pop();
      }
      Pt dead = ptPath.pop();
//      bug(Debug.spaces(depth) + "-- popped point: " + SketchBook.n(dead));
    }
//    bug(Debug.spaces(depth) + "-- leaving " + depth);
  }

  private void maybeAddStencil(Pt target, List<Pt> ptPath, List<Segment> segPath) {
    int idx = ptPath.lastIndexOf(target);
    List<Pt> newStencilPath = new ArrayList<Pt>();
    List<Segment> newStencilSegments = new ArrayList<Segment>();
    for (int i = idx; i < ptPath.size(); i++) {
      newStencilPath.add(ptPath.get(i));
      newStencilSegments.add(segPath.get(i));
    }
    boolean isSame = false;
    for (Stencil s : stencils) {
      if (s.hasPath(newStencilSegments)) {
        isSame = true;
        break;
      }
    }
    if (!isSame) {
      stencils.add(new Stencil(model, newStencilPath, newStencilSegments));
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

  public Map<Pt, Set<Pt>> makeAdjacency(Set<Segment> allGeometry) {
    Map<Pt, Set<Pt>> adjacent = new HashMap<Pt, Set<Pt>>();
    for (Segment s : allGeometry) {
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
