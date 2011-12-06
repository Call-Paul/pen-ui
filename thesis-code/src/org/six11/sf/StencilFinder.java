package org.six11.sf;

import static org.six11.util.Debug.bug;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.six11.util.pen.Pt;
import static org.six11.util.Debug.num;

public class StencilFinder {

  private Set<Stencil> stencils;
  private Map<Pt, Set<Pt>> adjacent;
  private Stack<Pt> newPoints;
  private Set<List<Pt>> paths;

  public StencilFinder(Collection<Segment> newSegs, Set<Segment> allGeometry) {
    this.stencils = new HashSet<Stencil>();
    this.newPoints = new Stack<Pt>();
    this.paths = new HashSet<List<Pt>>();
    for (Segment s : newSegs) {
      if (!newPoints.contains(s.getP1())) {
        newPoints.add(s.getP1());
      }
      if (!newPoints.contains(s.getP2())) {
        newPoints.add(s.getP2());
      }
    }
    makeAdjacency(allGeometry);
    Stack<Pt> initialPath = new Stack<Pt>();
    for (Pt newPt : newPoints) {
      if (adjacent.containsKey(newPt)) {
        initialPath.push(newPt);
        delve(initialPath);
        initialPath.pop();
      }
    }
    Set<List<Pt>> pruned = new HashSet<List<Pt>>();
    for (List<Pt> path : paths) {
      boolean ok = true;
      for (List<Pt> goodPath : pruned) {
        if (goodPath.size() == path.size() && goodPath.containsAll(path)) {
          ok = false;
          break;
        }
      }
      if (ok) {
        pruned.add(path);
      }
    }
    for (List<Pt> path : pruned) {
      List<Segment> segList = Stencil.getSegmentList(path, allGeometry);
      stencils.add(new Stencil(path, segList));
    }
  }

  private void delve(Stack<Pt> path) {
    Pt top = path.peek();
    try {
      for (Pt adj : adjacent.get(top)) {
        if (path.size() > 2 && adj.equals(path.get(0))) {
          path.push(adj);
          paths.add(new ArrayList<Pt>(path));
          path.pop();
        } else if (path.contains(adj)) {
          // avoid this one. do nothing
        } else {
          path.push(adj);
          delve(path);
          path.pop();
        }
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      bug("Caught " + ex.getClass() + ", diagnosing issue...");
      bug("Current path: " + n(path));
      bug("Top: " + n(top));
      bug("Adjacency table:");
      for (Pt pt : adjacent.keySet()) {
        bug("  " + n(pt) + ": " + n(adjacent.get(pt)));
      }
    }

  }

  private final void makeAdjacency(Set<Segment> allGeometry) {
    adjacent = new HashMap<Pt, Set<Pt>>();
    for (Segment s : allGeometry) {
      Pt p1 = s.getP1();
      Pt p2 = s.getP2();
      associate(p1, p2);
      associate(p2, p1);
    }
//    printAdjacencyTable(allGeometry);
  }
  
  private void printAdjacencyTable(Collection<Segment> geom) {
    System.out.println("-----");
    for (Segment seg : geom) {
      System.out.println(seg.getType() + " from " + n(seg.getP1()) + " to " + n(seg.getP2()));
    }
    System.out.println();
    for (Map.Entry<Pt, Set<Pt>> vals : adjacent.entrySet()) {
      System.out.println("  " + n(vals.getKey()) + ": " + n(vals.getValue()));
    }
    System.out.println("-----");
  }

  public static String n(Pt pt) {
    return pt.getString("name");
  }

  public static String n(Collection<Pt> pts) {
    StringBuilder buf = new StringBuilder();
    if (pts == null) {
      buf.append("<null input!>");
    } else {
      for (Pt pt : pts) {
        buf.append(n(pt) + " ");
      }
    }
    return buf.toString();
  }

  private void associate(Pt p1, Pt p2) {
    if (!adjacent.containsKey(p1)) {
      adjacent.put(p1, new HashSet<Pt>());
    }
    adjacent.get(p1).add(p2);
  }

  public Set<Stencil> getNewStencils() {
    return stencils;
  }
}
