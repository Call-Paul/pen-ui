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

import org.six11.util.data.Lists;
import org.six11.util.pen.Pt;
import static org.six11.util.Debug.num;

public class StencilFinder {

  private static final int MIN_NUM_SEGS = 3;
  private static int ID_COUNTER = 1;
  private Set<Stencil> stencils;
  private Map<Pt, Set<Pt>> adjacent;
  private Stack<Pt> newPoints;
  private Set<List<Pt>> paths;
  private final int id = ID_COUNTER++;
  private SketchBook model;
  
  public StencilFinder(SketchBook model) {
    this.model = model;
    this.stencils = new HashSet<Stencil>();
    this.newPoints = new Stack<Pt>();
    this.paths = new HashSet<List<Pt>>();
  }

  public Set<Stencil> findStencils(Collection<Segment> newSegs, Set<Segment> allGeometry) {
    for (Segment s : newSegs) {
      if (!newPoints.contains(s.getP1())) {
        newPoints.add(s.getP1());
      }
      if (!newPoints.contains(s.getP2())) {
        newPoints.add(s.getP2());
      }
    }
    makeAdjacency(allGeometry);
    //    printAdjacencyTable(allGeometry);
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
      stencils.add(new Stencil(model, path));
    }
    
    return stencils;
  }

  public static void merge(Set<Stencil> rest, Set<Stencil> done) {
    bug("Merging. rest has " + rest.size() + ", done has " + done.size());
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

  private void delve(Stack<Pt> path) {
    bug("Delve: path(" + path.size() + "): " + n(path));
    Pt top = path.peek();
    try {
      for (Pt adj : adjacent.get(top)) {
        if (path.size() > MIN_NUM_SEGS && adj.equals(path.get(0))) {
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

  public Map<Pt, Set<Pt>> makeAdjacency(Set<Segment> allGeometry) {
    adjacent = new HashMap<Pt, Set<Pt>>();
    for (Segment s : allGeometry) {
      Pt p1 = s.getP1();
      Pt p2 = s.getP2();
      associate(p1, p2);
      associate(p2, p1);
    }
    return adjacent;
  }

  private String ident() {
    return "(adjacency table for finder #" + id + ")";
  }

  private void printAdjacencyTable(Collection<Segment> geom) {
    System.out.println("-- table based on segment geometry --- " + ident());
    for (Segment seg : geom) {
      System.out.println(seg.getType() + " from " + n(seg.getP1()) + " to " + n(seg.getP2()));
    }
    System.out.println();
    for (Map.Entry<Pt, Set<Pt>> vals : adjacent.entrySet()) {
      System.out.println("  " + n(vals.getKey()) + ": " + n(vals.getValue()));
    }
  }

  public void printAdjacencyTable() {
    System.out.println("-- adjacency table --- " + ident());
    for (Map.Entry<Pt, Set<Pt>> vals : adjacent.entrySet()) {
      System.out.println("  " + n(vals.getKey()) + ": " + n(vals.getValue()));
    }
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
    //    bug("Just associated " + n(p1) + " => " + n(p2) + ". Table is currently:");
    //    printAdjacencyTable();
  }

  public Set<Stencil> getNewStencils() {
    return stencils;
  }
}
