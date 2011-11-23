package org.six11.sf;

import static org.six11.util.Debug.bug;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.six11.util.pen.Pt;

public class StencilFinder {

  private Set<Stencil> stencils;
  private Map<Pt, Set<Pt>> adjacent;
  private Stack<Pt> newPoints;

  public StencilFinder(Collection<Segment> newSegs, Set<Segment> allGeometry) {
    bug("StencilFinder receives input: " + newSegs.size() + " new segments, " + allGeometry.size() + " total segments.");
    this.stencils = new HashSet<Stencil>();
    this.newPoints = new Stack<Pt>();
    for (Segment s : newSegs) {
      if (!newPoints.contains(s.getP1())) {
        newPoints.add(s.getP1());
      }
      if (!newPoints.contains(s.getP2())) {
        newPoints.add(s.getP2());
      }
    }
    bug("Making adjacency table...");
    makeAdjacency(allGeometry);
    bug("Searching for paths...");
    Stack<Pt> path = new Stack<Pt>();
    for (Pt newPt : newPoints) {
      path.push(newPt);
      delve(path);
      path.pop();
    }
  }

  private void delve(Stack<Pt> path) {
    Pt top = path.peek();
    String withOrWithout = adjacent.containsKey(top) ? "with" : "without";
    bug("  Delve: " + n(path) + " (top node: " + n(top) + " " + withOrWithout + " an adjacency table)");
    for (Pt adj : adjacent.get(top)) {
      if (path.contains(adj)) {
        bug("    Avoid " + adj);
      } else if (adj.equals(path.get(0))) {
        bug("    * Found complete tour! Length: " + (path.size() + 1));
      } else {
        path.push(adj);
        delve(path);
        path.pop();
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
    bug("Adjacency table:");
    for (Pt pt : adjacent.keySet()) {
      bug("  " + n(pt) + ": " + n(adjacent.get(pt)));
    }
  }

  private String n(Pt pt) {
    return pt.getString("name");
  }

  private String n(Collection<Pt> pts) {
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
      bug("New point found: " + n(p1));
      adjacent.put(p1, new HashSet<Pt>());
    }
    bug("Associating: " + n(p1) + " with " + n(p2));
    adjacent.get(p1).add(p2);
    bug("Now have entry for " +n(p1) + " = " + n(adjacent.get(p1)));
  }

  public Set<Stencil> getNewStencils() {
    return stencils;
  }
}
