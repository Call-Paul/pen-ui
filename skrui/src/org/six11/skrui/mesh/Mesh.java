package org.six11.skrui.mesh;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.six11.util.Debug;
import org.six11.util.gui.BoundingBox;
import org.six11.util.pen.ConvexHull;
import org.six11.util.pen.Functions;
import org.six11.util.pen.IntersectionData;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Mesh {

  public static final String HALF_EDGE = "half-edge";

  List<Pt> allPoints;
  Set<Triangle> triangles;
  List<Pt> rootPoints;

  public static HalfEdge he(Pt pt) {
    return (HalfEdge) pt.getAttribute(HALF_EDGE);
  }

  public Mesh(List<Pt> points, double edgeLengthThreshold) {
    long start = System.nanoTime();
    this.allPoints = new ArrayList<Pt>();
    triangles = new HashSet<Triangle>();

    if (points.size() > 2) {
      BoundingBox bb = new BoundingBox();
      for (Pt pt : points) {
        bb.add(pt);
      }
      // DrawingBuffer boundingBuf = new DrawingBuffer();
      bb.grow(2000);
      Rectangle2D boundingRect = bb.getRectangle();
      List<Pt> box = new ArrayList<Pt>();
      box.add(new Pt(boundingRect.getMinX(), boundingRect.getMinY())); // p0
      box.add(new Pt(boundingRect.getMaxX(), boundingRect.getMinY())); // p1
      box.add(new Pt(boundingRect.getMaxX(), boundingRect.getMaxY())); // p2
      box.add(new Pt(boundingRect.getMinX(), boundingRect.getMaxY())); // p3

      Line top = new Line(box.get(0), box.get(1));
      Line side = new Line(box.get(2), box.get(1));
      Vec sideVec = new Vec(side);
      Line bot = new Line(box.get(2), box.get(3));
      Pt topMid = top.getMidpoint();
      Pt vertA = new Pt(topMid.x - sideVec.getX(), topMid.y - sideVec.getY());
      Line toB = new Line(vertA, box.get(0));
      Line toC = new Line(vertA, box.get(1));
      Pt vertBHalf = Functions.getIntersectionPoint(toB, bot);
      Pt vertCHalf = Functions.getIntersectionPoint(toC, bot);
      if (vertBHalf == null) {
        bug("vertBHalf is null. Box is: " + box);
      }
      if (vertCHalf == null) {
        bug("vertCHalf is null. I will fail now.");
      }
      Pt vertB = Functions.getEndPoint(vertBHalf, new Vec(vertA, vertBHalf));
      Pt vertC = Functions.getEndPoint(vertCHalf, new Vec(vertA, vertCHalf));
      Triangle root = new Triangle(vertA, vertB, vertC);
      rootPoints = new ArrayList<Pt>(root.getPoints());
      triangles.add(root);
    } else if (points.size() > 0) {
      Pt happy = points.get(0);
      Triangle root = new Triangle(happy.getTranslated(0, -2000), happy.getTranslated(-2000, 2000), happy.getTranslated(2000, 2000));
      rootPoints = new ArrayList<Pt>(root.getPoints());
      triangles.add(root);
    }
    // and then add each point in 'points', fixing the mesh after each one. Not the fastest
    // algorithm, but I'm dealing with relatively tiny meshes.
    for (Pt newVert : points) {
      addPoint(newVert);
    }
    boolean ok = true;
    for (Triangle t : triangles) {
      for (Pt pt : t.getPoints()) {
        ok = isDelaunay(pt, t);
      }
      boolean inside = isPointInRegion(t.getCentroid(), points);
      if (inside) {
        t.setLocation(Where.Inside);
      } else {
        t.setLocation(Where.Outside);
      }
    }

    if (edgeLengthThreshold > 0) {
      fixMesh(edgeLengthThreshold);
    }
    ok = true;
    for (Triangle t : triangles) {
      for (Pt pt : t.getPoints()) {
        ok = isDelaunay(pt, t);
      }
    }
    long end = System.nanoTime();
    bug("Mesh OK? " + ok + " (took " + ((end - start) / 1000) + " microseconds to establish "
        + triangles.size() + " triangles)");
  }

  public long getTime() {
    return allPoints.get(allPoints.size() - 1).getTime();
  }

  public void fixMesh(double edgeLengthThreshold) {
    int numRepaired = 0;
    do {
      numRepaired = 0;
      Set<Triangle> batch = new HashSet<Triangle>(triangles);
      for (Triangle t : batch) {
        if (triangles.contains(t)) {
          if (t.getMeshLocation() == Where.Inside) {
            List<Pt> tp = t.getPoints();

            for (int i = 0; i < 3; i++) {
              if (tp.get(i).distance(tp.get((i + 1) % 3)) > edgeLengthThreshold) {
                HalfEdge e = t.getEdge();
                while (e.getPoint() != tp.get((i + 1) % 3)) {
                  e = e.getNext();
                }
                Pt splitLocation = Functions.getMean(tp.get(i), tp.get((i + 1) % 3));
                addPointOnEdge(e, splitLocation);
                numRepaired++;
                break;
              }
            }
          }
        }
      }
    } while (numRepaired > 0);
  }

  public boolean expand(Pt here) {
    boolean ret = addPoint(here);
    if (ret) {
      computeInsideOutside();
    }
    return ret;
  }

  public void computeInsideOutside() {
    for (Triangle t : triangles) {
      boolean inside = isPointInRegion(t.getCentroid(), allPoints);
      if (inside) {
        t.setLocation(Where.Inside);
      } else {
        t.setLocation(Where.Outside);
      }
    }
  }

  public boolean isPointInRegion(Pt pt, List<Pt> closedRegion) {
    Line up = new Line(pt, pt.getTranslated(0, 1));
    Line right = new Line(pt, pt.getTranslated(1, 0));
    int ixRight = 0;
    int ixUp = 0;
    for (int i = 0; i < closedRegion.size() - 1; i++) {
      Line line = new Line(closedRegion.get(i), closedRegion.get(i + 1));
      IntersectionData id = Functions.getIntersectionData(right, line);
      double param1 = id.getLineOneParam();
      double param2 = id.getLineTwoParam();
      if (param1 > 0 && param2 >= 0 && param2 < 1) {
        ixRight = ixRight + 1;
      }
      id = Functions.getIntersectionData(up, line);
      param1 = id.getLineOneParam();
      param2 = id.getLineTwoParam();
      if (param1 > 0 && param2 >= 0 && param2 < 1) {
        ixUp = ixUp + 1;
      }
    }
    boolean okRight = (ixRight % 2) == 1;
    boolean okUp = (ixUp % 2) == 1;
    
    return okRight || okUp;
  }

  public int size() {
    return triangles.size();
  }

  public List<Pt> getPoints() {
    return allPoints;
  }
  
  public ConvexHull getHull() {
    return new ConvexHull(allPoints);
  }

  public boolean addPoint(Pt newVert) {
    boolean ret = false;
    if (!allPoints.contains(newVert)) {
      allPoints.add(newVert);
      TriangleWhere tw = findTriangle(newVert);
      if (tw.where == Where.Inside) {
        addPointInside(tw.triangle, newVert);
        ret = true;
      } else if (tw.where == Where.Boundary) {
        HalfEdge splitMe = tw.triangle.getEdgeContaining(newVert);
        if (splitMe == null) {
          bug("I would like to insert a point that should be on a boundary, "
              + "but I can't figure out where.");
        } else {
          addPointOnEdge(splitMe, newVert);
          ret = true;
        }
      } else {
        bug("Can't find a triangle for new vertex: " + Debug.num(newVert));
      }
    }
    return ret;
  }

  private void addPointOnEdge(HalfEdge splitMe, Pt newVert) {
    HalfEdge a, b, c, d, e, f, g, h, i, j, k, l, m, n;
    Triangle u, v, w, x, y, z;
    b = splitMe;
    c = b.getNext();
    a = c.getNext();
    e = b.getPair();
    d = e.getNext();
    f = d.getNext();
    x = a.getFace();
    w = d.getFace();
    u = new Triangle(a);
    v = new Triangle(c);
    y = new Triangle(d);
    z = new Triangle(f);
    g = new HalfEdge(newVert, y);
    h = new HalfEdge(d.getPoint(), z);
    i = new HalfEdge(newVert, v);
    j = new HalfEdge(c.getPoint(), u);
    k = new HalfEdge(newVert, u);
    l = new HalfEdge(f.getPoint(), v);
    m = new HalfEdge(newVert, z);
    n = new HalfEdge(a.getPoint(), y);
    a.setNext(k);
    a.setFace(u);
    c.setNext(i);
    c.setFace(v);
    d.setNext(g);
    d.setFace(y);
    f.setNext(m);
    f.setFace(z);
    g.setNext(n);
    g.setPair(h);
    h.setNext(f);
    i.setNext(l);
    i.setPair(j);
    j.setNext(a);
    k.setNext(j);
    k.setPair(n);
    l.setNext(c);
    l.setPair(m);
    m.setNext(h);
    n.setNext(d);
    // bug("Removing triangles " + x.meshLocation + ", " + w.meshLocation
    // + " and replacing them with " + u.meshLocation + ", " + v.meshLocation + ", "
    // + y.meshLocation + ", " + z.meshLocation);
    triangles.remove(x);
    triangles.remove(w);
    Set<Triangle> newTriangles = new HashSet<Triangle>();
    newTriangles.add(u);
    newTriangles.add(v);
    newTriangles.add(y);
    newTriangles.add(z);
    triangles.addAll(newTriangles);
    for (Triangle t : newTriangles) {
      if (triangles.contains(t) && !isDelaunay(newVert, t)) {
        flip(newVert, t);
      }
    }
  }

  private void addPointInside(Triangle splitMe, Pt newVert) {
    HalfEdge e1, e2, e3;
    HalfEdge n1, n2, n3, n1p, n2p, n3p;
    Triangle t1, t2, t3;
    e1 = splitMe.getEdge();
    e2 = e1.getNext();
    e3 = e2.getNext();
    t1 = new Triangle(e1);
    t2 = new Triangle(e2);
    t3 = new Triangle(e3);
    n1 = new HalfEdge(newVert, t1);
    n2 = new HalfEdge(newVert, t2);
    n3 = new HalfEdge(newVert, t3);
    e1.setNext(n1);
    e1.setFace(t1);
    e2.setNext(n2);
    e2.setFace(t2);
    e3.setNext(n3);
    e3.setFace(t3);
    n1p = new HalfEdge(e1.getPoint(), t2);
    n2p = new HalfEdge(e2.getPoint(), t3);
    n3p = new HalfEdge(e3.getPoint(), t1);
    n1p.setNext(e2);
    n2p.setNext(e3);
    n3p.setNext(e1);
    n1p.setPair(n1);
    n2p.setPair(n2);
    n3p.setPair(n3);
    n1.setNext(n3p);
    n2.setNext(n1p);
    n3.setNext(n2p);
    newVert.setAttribute(HALF_EDGE, n1);
    triangles.remove(splitMe);
    triangles.add(t1);
    triangles.add(t2);
    triangles.add(t3);
    if (!isDelaunay(newVert, t1)) {
      flip(newVert, t1);
    }
    if (triangles.contains(t2) && !isDelaunay(newVert, t2)) {
      flip(newVert, t2);
    }
    if (triangles.contains(t3) && !isDelaunay(newVert, t3)) {
      flip(newVert, t3);
    }
  }

  private void flip(Pt vert, Triangle t) {
    // This unconditionally flips the edge opposite from 'vert' on the given triangle. This has the
    // effect of removing t and it's neighboring triangle from the mesh, and adding two new
    // triangles. At the end of this function, all half-edges have been updated to reflect the new
    // world order.

    HalfEdge a, b, c, d, e, f, g, h;
    Triangle w, x, y, z;
    HalfEdge cursor = t.getEdge();
    cursor = advance(cursor, vert);

    c = cursor;
    a = c.getNext();
    b = a.getNext();
    f = b.getPair();
    d = f.getNext();
    e = d.getNext();
    w = d.getFace();
    x = a.getFace();

    y = new Triangle(a);
    z = new Triangle(c);
    g = new HalfEdge(c.getPoint(), y);
    h = new HalfEdge(d.getPoint(), z);
    g.setPair(h);
    g.setNext(a);
    h.setNext(e);
    a.setNext(d);
    a.setFace(y);
    c.setNext(h);
    c.setFace(z);
    d.setNext(g);
    d.setFace(y);
    e.setNext(c);
    e.setFace(z);
    triangles.remove(w);
    triangles.remove(x);
    triangles.add(y);
    triangles.add(z);

    for (Pt pt : y.getPoints()) {
      if (triangles.contains(y) && !isDelaunay(pt, y)) {
        // bug("(1) Flip triangle with verts " + y.getVertIds() + " using vert " + pt.getID()
        // + ". Input triangle and vert: " + t.getVertIds() + ", " + vert.getID());
        flip(pt, y);
      }
    }
    for (Pt pt : z.getPoints()) {
      if (triangles.contains(z) && !isDelaunay(pt, z)) {
        // bug("(2) Flip triangle with verts " + z.getVertIds() + " using vert " + pt.getID()
        // + ". Input triangle and vert: " + t.getVertIds() + ", " + vert.getID());
        flip(pt, z);
      }
    }
  }

  // /**
  // * returns true if one triangle is Inside, and the other is Outside.
  // */
  // private boolean shareBoundary(Triangle w, Triangle x) {
  // return (w.meshLocation == Where.Inside && x.meshLocation == Where.Outside)
  // || (x.meshLocation == Where.Inside && w.meshLocation == Where.Outside);
  // }

  private HalfEdge advance(HalfEdge cursor, Pt vert) {
    int count = 0;
    while (cursor.getPoint() != vert) {
      cursor = cursor.getNext();
      count++;
      if (count > 2) {
        bug("vertex " + vert + " is not contained in this triangle! Bailing.");
        break;
      }
    }
    return cursor;
  }

  public TriangleWhere findTriangle(Pt pt) {
    TriangleWhere ret = null;
    for (Triangle t : triangles) {
      Where where = t.whereIsPoint(pt);
      switch (where) {
        case Inside:
        case Boundary:
        case Coincidental:
          ret = new TriangleWhere();
          ret.triangle = t;
          ret.where = where;
          break;
      }
    }
    if (ret == null) {
      bug("Warning: can't find triangle for point: " + pt);
    }
    return ret;
  }

  public class TriangleWhere {
    public Triangle triangle;
    public Where where;
  }

  /**
   * Locate the triangles and edges associated with this point, using the semantics defined by
   * getTriangles(Pt) and getEdges(Pt).
   * 
   * @param pt
   *          the point of interest
   * @param triangles
   *          a set to store Triangle instances in (may be null if you don't need them)
   * @param edges
   *          a set to store HalfEdge instances in (may be null if you don't need them)
   */
  public void findTrianglesAndEdges(Pt pt, Set<Triangle> triangles, Set<HalfEdge> edges) {
    HalfEdge startEdge = he(pt);
    HalfEdge cursor = startEdge;
    boolean nulled = false; // see below
    // move 'left' until we get to a no-pair edge or the beginning.
    // if a no-pair is encountered, move 'right' until we get to a no-pair edge.
    do {
      if (cursor == null) {
        break;
      }
      if (cursor.getPoint() != pt) {
        bug("Error: in getTriangles(" + Debug.num(pt) + "), cursor points at wrong point: "
            + Debug.num(cursor.getPoint()));
      }
      if (triangles != null) {
        triangles.add(cursor.getFace());
      }
      if (edges != null) {
        edges.add(cursor);
      }
      if (cursor.getPair() == null) {
        if (nulled) {
          break;
        }
        cursor = startEdge.getNext().getPair();
        if (cursor == null) {
          break;
        }
        nulled = true;
      } else {
        cursor = cursor.getPair().getNext().getNext();
      }
    } while (cursor != startEdge);
  }

  /**
   * Which triangles use this vertex?
   */
  public Set<Triangle> getTriangles(Pt pt) {
    Set<Triangle> ret = new HashSet<Triangle>();
    findTrianglesAndEdges(pt, ret, null);
    return ret;
  }

  /**
   * Which edges use this vertex?
   */
  public Set<HalfEdge> getEdges(Pt pt) {
    Set<HalfEdge> ret = new HashSet<HalfEdge>();
    findTrianglesAndEdges(pt, null, ret);
    return ret;
  }

  /**
   * Which faces border this edge?
   */
  public Set<Triangle> getTriangles(HalfEdge edge) {
    return edge.getTriangles();
  }

  /**
   * Which faces are adjacent to this face?
   */
  public Set<Triangle> getTriangles(Triangle t) {
    return t.getAdjacentTriangles();
  }

  private static void bug(String what) {
    Debug.out("Mesh", what);
  }

  public Set<Triangle> getTriangles() {
    return triangles;
  }

  public boolean isDelaunay(Pt pt, Triangle t) {
    boolean ret = true;
    List<Pt> quad = t.getQuadrangle(pt);
    if (quad != null && quad.size() == 4) {
      Pt a = quad.get(3);
      Pt b = quad.get(2);
      Pt c = quad.get(1);

      Pt d = quad.get(0);
      double det = Functions.getDeterminant(a, b, c, d);
      ;
      boolean dInside = det > 0;
      ret = !dInside;
    }
    return ret;
  }

  public Set<Triangle> getInsideTriangles() {
    Set<Triangle> inside = new HashSet<Triangle>();
    for (Triangle t : triangles) {
      if (t.getMeshLocation() == Where.Inside) {
        inside.add(t);
      }
    }
    return inside;
  }

  public Set<Triangle> getLegitimateTriangles() {
    Set<Triangle> legit = new HashSet<Triangle>();
    for (Triangle t : triangles) {
      if (!t.involvesPoints(rootPoints)) {
        legit.add(t);
      }
    }
    return legit;
  }

}
