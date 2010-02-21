package org.six11.skrui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.six11.util.Debug;
import org.six11.util.pen.ConvexHull;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Functions;
import org.six11.util.pen.IntersectionData;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Delaunay {

  class PtGraph {
    // each point can be related to any number of other points
    Map<Pt, Set<Pt>> adjacency;
    boolean dirty;
    Set<Line> lines;

    PtGraph() {
      adjacency = new HashMap<Pt, Set<Pt>>();
    }

    void relate(Pt a, Pt b) {
      getSet(a).add(b);
      getSet(b).add(a);
      dirty = true;
    }

    void unrelate(Pt a, Pt b) {
      getSet(a).remove(b);
      getSet(b).remove(a);
      dirty = true;
    }

    void debug() {
      StringBuilder buf = new StringBuilder();
      for (Pt key : adjacency.keySet()) {
        buf.append(Debug.num(key) + " ==>\n");
        for (Pt val : getSet(key)) {
          buf.append("  " + Debug.num(val) + "\n");
        }
      }
      System.out.println(buf.toString());
    }

    private Set<Pt> getSet(Pt pt) {
      if (!adjacency.containsKey(pt)) {
        adjacency.put(pt, new HashSet<Pt>());
      }
      return adjacency.get(pt);
    }

    /**
     * Gives a set of lines without dupes. In other words, it gives you a->b but not b->a.
     */
    Set<Line> getLines() {
      if (dirty || lines == null) {
        lines = new HashSet<Line>();
        Comparator<Pt> comp = Pt.sortByX();
        for (Pt key : adjacency.keySet()) {
          for (Pt val : getSet(key)) {
            if (comp.compare(key, val) > 0) {
              lines.add(new Line(key, val));
            }
          }
        }
      }
      return lines;
    }

    public boolean intersectsAnything(Pt a, Pt b) {
      boolean ret = false;
      Line line = new Line(a, b);
      for (Line other : getLines()) {
        IntersectionData id = Functions.getIntersectionData(line, other);

        if (id.intersectsStrictlyInsideSegments()) {
          ret = true;
          break;
        }
      }
      return ret;
    }

    public Set<Pt> findThirdPoints(Pt a, Pt b) {
      Set<Pt> ret = new HashSet<Pt>();
      Set<Pt> fromA = getSet(a);
      Set<Pt> fromB = getSet(b);
      if (fromA.contains(b)) {
        for (Pt inB : fromB) {
          if (fromA.contains(inB)) {
            ret.add(inB);
          }
        }
      }
      return ret;
    }

    public boolean hasTriangle(Tri t) {
      Set<Pt> fromA = getSet(t.a);
      Set<Pt> fromB = getSet(t.b);
      Set<Pt> fromC = getSet(t.c);
      return (fromA.contains(t.b) && fromB.contains(t.c) && fromC.contains(t.a));
    }

    public Set<Tri> getNeighbors(Tri t) {
      // return the triangles that are adjacent to t
      Set<Tri> ret = new HashSet<Tri>();
      Set<Pt> options;
      options = findThirdPoints(t.a, t.b);
      for (Pt pt : options) {
        if (pt != t.c) {
          ret.add(new Tri(t.a, t.b, pt));
        }
      }
      options = findThirdPoints(t.b, t.c);
      for (Pt pt : options) {
        if (pt != t.a) {
          ret.add(new Tri(t.b, t.c, pt));
        }
      }
      options = findThirdPoints(t.c, t.a);
      for (Pt pt : options) {
        if (pt != t.b) {
          ret.add(new Tri(t.c, t.a, pt));
        }
      }
      bug("There are " + ret.size() + " neighbors to triangle " + t + ":");
      for (Tri tri : ret) {
        bug("  " + tri);
      }
      return ret;
    }

    public Tri getTriangle(Pt a) {
      // return a triangle involving the given point. it is arbitrary which one is returned.
      Set<Pt> friends = getSet(a);
      Pt b = friends.iterator().next();
      friends = findThirdPoints(a, b);
      Pt c = friends.iterator().next();
      return new Tri(a, b, c);
    }

    public Set<Tri> getTriangles(Tri t) {
      Set<Tri> ret = new HashSet<Tri>();
      Stack<Tri> stack = new Stack<Tri>();
      stack.push(t);
      while (!stack.isEmpty()) {
        Tri cursor = stack.pop();
        ret.add(cursor);
        Set<Tri> friends = getNeighbors(cursor);
        for (Tri f : friends) {
          if (!ret.contains(f)) {
            stack.push(f);
          }
        }
      }
      return ret;
    }

  }

  class Tri implements Comparable<Tri> {
    Pt a, b, c;
    Pt centroid;

    Tri(Pt a, Pt b, Pt c) {
      this.a = a;
      this.b = b;
      this.c = c;
    }

    Pt getCentroid() {
      if (centroid == null) {
        double x = (a.x + b.x + c.x) / 3;
        double y = (a.y + b.y + c.y) / 3;
        centroid = new Pt(x, y);
      }
      return centroid;
    }
    
    public boolean equals(Object other) {
      boolean ret = false;
      if (other instanceof Tri) {
        Tri t = (Tri) other;
        ret = true;
        Set<Pt> myPts = getPoints();
        Set<Pt> otherPts = t.getPoints();
        for (Pt pt : myPts) {
          ret = ret && otherPts.contains(pt);
        }
      }
      return ret;
    }
    
    public int hashCode() {
      return a.hashCode() ^ b.hashCode() ^ c.hashCode();
    }

    public int compareTo(Tri o) {
      int ret = 0;
      Pt otherC = o.getCentroid();
      Pt myC = getCentroid();
      if (otherC.getX() < myC.getX()) {
        ret = 1;
      } else if (otherC.getX() > myC.getX()) {
        ret = -1;
      } else if (otherC.getY() < myC.getY()) { // resort to y component.
        ret = 1;
      } else if (otherC.getY() > myC.getY()) {
        ret = 1;
      }
      return ret;
    }

    public String toString() {
      return "Triangle [" + Debug.num(a) + ", " + Debug.num(b) + ", " + Debug.num(c) + "]";
    }

    public Set<Pt> getPoints() {
      Set<Pt> ret = new HashSet<Pt>();
      ret.add(a);
      ret.add(b);
      ret.add(c);
      return ret;
    }
  }

  PtGraph graph;
  Main main;

  public Delaunay(Main m) {
    this.graph = new PtGraph();
    this.main = m;
  }

  public static void bug(String what) {
    Debug.out("Delaunay", what);
  }

  public void triangulate(Sequence seq) {
    List<Pt> decimated = Functions.getNormalizedSequence(seq.getPoints(), 50);
    Pt prev = null;

    // Connect each point and its neighbor. This gives the shape its definition.
    for (Pt pt : decimated) {
      if (prev != null) {
        graph.relate(prev, pt);
      }
      prev = pt;
    }
    graph.relate(decimated.get(decimated.size() - 1), decimated.get(0));

    for (int i = 0; i < decimated.size() - 1; i++) {
      Pt cursor = decimated.get(i);
      for (int j = i + 1; j < decimated.size(); j++) {
        Pt next = decimated.get(j);
        if (!graph.intersectsAnything(cursor, next)) {
          graph.relate(cursor, next);
        }
      }
    }

    Pt a = decimated.get(0);
    Tri t = graph.getTriangle(a);
    Set<Tri> neighbors = graph.getNeighbors(t);
    Set<Tri> all = graph.getTriangles(t);

    // show it.
    DrawingBuffer db = new DrawingBuffer();
    for (Tri f : all) {
      DrawingBufferRoutines.dot(db, f.getCentroid(), 5.0, 0.5, Color.BLACK, Color.RED);
      DrawingBufferRoutines.line(db, f.a, f.b, Color.GRAY, 0.5);
      DrawingBufferRoutines.line(db, f.b, f.c, Color.GRAY, 0.5);
      DrawingBufferRoutines.line(db, f.c, f.a, Color.GRAY, 0.5);
    }
    

//    Set<Line> lines = graph.getLines();
//    
//    for (Line line : lines) {
//      DrawingBufferRoutines.line(db, line.getStart(), line.getEnd(), Color.BLUE);
//    }
    
    main.getDrawingSurface().getSoup().addBuffer(db);
  }

  public PtGraph getGraph() {
    return graph;
  }
}
