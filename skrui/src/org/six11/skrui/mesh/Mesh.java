package org.six11.skrui.mesh;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import org.six11.skrui.script.Animation;
import org.six11.util.Debug;
import org.six11.util.gui.BoundingBox;
import org.six11.util.pen.Functions;
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
  Animation ani;

  public static HalfEdge he(Pt pt) {
    return (HalfEdge) pt.getAttribute(HALF_EDGE);
  }

  public Mesh(List<Pt> points, double edgeLengthThreshold, Animation ani) {
    long start = System.nanoTime();
    this.allPoints = new ArrayList<Pt>();
    this.ani = ani;
    triangles = new HashSet<Triangle>();

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
    triangles.add(root);

    // and then add each point in 'points', fixing the mesh after each one. Not the fastest
    // algorithm, but I'm dealing with relatively tiny meshes.
    for (Pt newVert : points) {
      addPoint(newVert, false);
    }
    boolean ok = true;
    for (Triangle t : triangles) {
      for (Pt pt : t.getPoints()) {
        ok = isDelaunay(pt, t);
      }
      boolean inside = Functions.isPointInRegion(t.getCentroid(), points);
      if (inside) {
        t.setLocation(Where.Inside);
      } else {
        t.setLocation(Where.Outside);
      }
    }

    if (edgeLengthThreshold > 0) {
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
                  addPointOnEdge(e, splitLocation, false);
                  numRepaired++;
                  break;
                }
              }
            }
          }
        }
        // animate(ani);
      } while (numRepaired > 0);
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

  public int size() {
    return triangles.size();
  }

  // private void animate(Animation ani) {
  // if (ani != null) {
  // DrawingBuffer db = new DrawingBuffer();
  // drawToBuffer(db);
  // ani.addFrame(db, false);
  // }
  // }

  // public void drawToBuffer(DrawingBuffer db) {
  // for (Triangle t : getTriangles()) {
  // if (t.getMeshLocation() == Where.Inside) {
  // List<Pt> drawUs = t.getPoints();
  // drawUs.add(drawUs.get(0));
  // DrawingBufferRoutines.lines(db, drawUs, Color.LIGHT_GRAY, 0.5);
  // DrawingBufferRoutines.dot(db, t.getCentroid(), 4.0, 0.4, Color.GRAY, Color.LIGHT_GRAY);
  // }
  // }
  // }

  // private void animate(DrawingBuffer boundingBuf, BoundingBox bb, Color c) {
  // boundingBuf.setColor(c);
  // boundingBuf.down();
  // boundingBuf.addShape(bb.getRectangle());
  // boundingBuf.up();
  // }

  private void addPoint(Pt newVert, boolean insertIfBreakBoundary) {
    if (!allPoints.contains(newVert)) {
      allPoints.add(newVert);
      TriangleWhere tw = findTriangle(newVert);
      if (tw.where == Where.Inside) {
        addPointInside(tw.triangle, newVert, insertIfBreakBoundary);
      }
      if (tw.where == Where.Boundary) {
        HalfEdge splitMe = tw.triangle.getEdgeContaining(newVert);
        if (splitMe == null) {
          bug("I would like to insert a point that should be on a boundary, "
              + "but I can't figure out where.");
        } else {
          addPointOnEdge(splitMe, newVert, insertIfBreakBoundary);
        }
      }
    }
  }

  public List<Pt> getBoundary() {
    List<Pt> ret = new ArrayList<Pt>();
    HalfEdge start = findBoundaryEdge();
    HalfEdge cursor = start;
    do {
      cursor = advanceBoundary(cursor);
      ret.add(cursor.getPoint());
    } while (cursor != start);

    return ret;
  }

  private HalfEdge advanceBoundary(HalfEdge cursor) {
    cursor = cursor.getNext();
    while (!isBoundaryEdge(cursor)) {
      cursor = cursor.getPair().getNext();
    }
    return cursor;
  }

  private HalfEdge findBoundaryEdge() {
    HalfEdge ret = null;
    outer: {
      for (Triangle t : triangles) {
        if (isBoundaryEdge(t.getEdge())) {
          ret = t.getEdge();
          break outer;
        }
        if (isBoundaryEdge(t.getEdge().getNext())) {
          ret = t.getEdge().getNext();
          break outer;
        }
        if (isBoundaryEdge(t.getEdge().getNext().getNext())) {
          ret = t.getEdge().getNext().getNext();
          break outer;
        }

      }
    }
    return ret;
  }

  /**
   * Is this edge on the inside and its pair on the outside?
   */
  private boolean isBoundaryEdge(HalfEdge he) {
    return he.getFace().meshLocation == Where.Inside
        && he.getPair().getFace().meshLocation == Where.Outside;
  }

  /**
   * Add a new point to the mesh and update the boundary data. On occasion this will mean a points
   * that used to be on the inside of the mesh now appears to be on the outside. To prevent existing
   * points from defecting from in to out, use okFlipInToOut=false.
   */
  public void addPointPushBoundary(Pt newVert, boolean okFlipInToOut) {
    boolean shouldAdd = false;
    for (Triangle t : triangles) {
      if (t.whereIsPoint(newVert) == Where.Inside && (t.meshLocation == Where.Outside)) {
        shouldAdd = true;
        break;
      }
    }
    if (shouldAdd) {
      addPoint(newVert, !okFlipInToOut);
      for (Triangle t : triangles) {
        boolean inside = Functions.isPointInRegion(t.getCentroid(), allPoints);
        if (inside) {
          t.setLocation(Where.Inside);
        } else {
          if (t.meshLocation == Where.Unknown || okFlipInToOut) {
            t.setLocation(Where.Outside);
          }
        }
      }
    }
  }

  private void addPointOnEdge(HalfEdge splitMe, Pt newVert, boolean insertIfBreakBoundary) {
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
    Stack<Pt> flipStack = new Stack<Pt>();
    for (Triangle t : newTriangles) {
      if (triangles.contains(t) && !isDelaunay(newVert, t)) {
        flip(newVert, t, insertIfBreakBoundary, flipStack);
      }
    }
  }

  //
  // private void animate(DrawingBuffer db) {
  // if (ani != null) {
  // ani.addFrame(db, true);
  // ani.addFrame(db, false);
  // }
  // }
  //
  // private void animate(DrawingBuffer db, Pt pt, Color c, double r) {
  // if (ani != null) {
  // DrawingBufferRoutines.dot(db, pt, r, r / 10, Color.black, c);
  // }
  // }
  //
  // private void animate(DrawingBuffer db, Triangle face, Color c, double thick) {
  // if (ani != null) {
  // List<Pt> triPoints = new ArrayList<Pt>(face.getPoints());
  // triPoints.add(triPoints.get(triPoints.size() - 1));
  // DrawingBufferRoutines.lines(db, triPoints, c, thick);
  // }
  // }
  //
  // private void animate(DrawingBuffer db, Set<Triangle> tris, Color c, double thick) {
  // if (ani != null) {
  // for (Triangle t : tris) {
  // animate(db, t, c, thick);
  // }
  // }
  // }

  private void addPointInside(Triangle splitMe, Pt newVert, boolean insertIfBreakBoundary) {
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
    // bug("Removing a triangle " + splitMe.meshLocation + " and replacing it with " +
    // t1.meshLocation
    // + ", " + t2.meshLocation + ", " + t3.meshLocation);
    triangles.remove(splitMe);
    triangles.add(t1);
    triangles.add(t2);
    triangles.add(t3);
    Stack flipStack = new Stack<Pt>();
    if (!isDelaunay(newVert, t1)) {
      flip(newVert, t1, insertIfBreakBoundary, flipStack);
    }
    if (triangles.contains(t2) && !isDelaunay(newVert, t2)) {
      flip(newVert, t2, insertIfBreakBoundary, flipStack);
    }
    if (triangles.contains(t3) && !isDelaunay(newVert, t3)) {
      flip(newVert, t3, insertIfBreakBoundary, flipStack);
    }
  }

  private void flip(Pt vert, Triangle t, boolean insertIfBreakBoundary, Stack flipStack) {
    // This unconditionally flips the edge opposite from 'vert' on the given triangle. This has the
    // effect of removing t and it's neighboring triangle from the mesh, and adding two new
    // triangles. At the end of this function, all half-edges have been updated to reflect the new
    // world order.
    // if (flipStack.contains(vert)) {
    // bug("Flipping on a point that is already being examined...");
    // }
    // flipStack.push(vert);
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
    if (insertIfBreakBoundary && shareBoundary(w, x)) {
      // w and x are on opposite sides of the fence, and the user wants to make sure that boundary
      // remains meaningful. So instead of flipping, insert a vertex on the boundary.
      // bug("Avoiding border screwiness. Inserting a point along the border instead.");
      Pt intersection = Functions.getIntersectionPoint(new Line(c.getPoint(), d.getPoint()),
          new Line(a.getPoint(), e.getPoint()));
      addPointOnEdge(b, intersection, insertIfBreakBoundary);
    } else {
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
      // animate(ani);
      for (Pt pt : y.getPoints()) {
        if (triangles.contains(y) && !isDelaunay(pt, y)) {
          // bug("(1) Flip triangle with verts " + y.getVertIds() + " using vert " + pt.getID()
          // + ". Input triangle and vert: " + t.getVertIds() + ", " + vert.getID());
          flip(pt, y, insertIfBreakBoundary, flipStack);
        }
      }
      for (Pt pt : z.getPoints()) {
        if (triangles.contains(z) && !isDelaunay(pt, z)) {
          // bug("(2) Flip triangle with verts " + z.getVertIds() + " using vert " + pt.getID()
          // + ". Input triangle and vert: " + t.getVertIds() + ", " + vert.getID());
          flip(pt, z, insertIfBreakBoundary, flipStack);
        }
      }
    }
    // flipStack.pop(); // TODO: remove the flipstack. probably not needed.
  }

  /**
   * returns true if one triangle is Inside, and the other is Outside.
   */
  private boolean shareBoundary(Triangle w, Triangle x) {
    return (w.meshLocation == Where.Inside && x.meshLocation == Where.Outside)
        || (x.meshLocation == Where.Inside && w.meshLocation == Where.Outside);
  }

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

  private TriangleWhere findTriangle(Pt pt) {
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

  private class TriangleWhere {
    Triangle triangle;
    Where where;
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

  // /**
  // * Which edges border this face?
  // */
  // public Set<HalfEdge> getEdges(Triangle t) {
  // return t.getEdges();
  // }

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
      // Pt a = quad.get(0);
      // Pt b = quad.get(1);
      // Pt c = quad.get(2);
      // Pt d = quad.get(3);
      // double det = Functions.getDeterminant(a, b, c, d);
      Pt a = quad.get(3);
      Pt b = quad.get(2);
      Pt c = quad.get(1);

      Pt d = quad.get(0);
      double det = Functions.getDeterminant(a, b, c, d);
      // bug("det(" + Debug.num(a) + ", " + Debug.num(b) + ", " + Debug.num(c) + ", " + Debug.num(d)
      // + " = " + Debug.num(det));
      boolean dInside = det > 0;
      ret = !dInside;
    }
    return ret;
  }

  // private void animate(Pt pt, List<Pt> quad, String what, boolean isD) {
  // if (ani != null) {
  // DrawingBuffer buf = new DrawingBuffer();
  // buf.moveTo(20, 20);
  // buf.down();
  // buf.addText(what, Color.BLACK);
  // buf.up();
  // DrawingBufferRoutines.dots(buf, quad, 6.0, 1.0, Color.BLACK, Color.RED);
  // DrawingBufferRoutines.dot(buf, pt, 9.0, 1.4, Color.BLACK, Color.BLUE);
  // DrawingBufferRoutines.line(buf, quad.get(0), quad.get(1), Color.BLACK, 2.0);
  // DrawingBufferRoutines.line(buf, quad.get(1), quad.get(2), Color.BLACK, 2.0);
  // DrawingBufferRoutines.line(buf, quad.get(2), quad.get(3), Color.BLACK, 2.0);
  // DrawingBufferRoutines.line(buf, quad.get(3), quad.get(0), Color.BLACK, 2.0);
  // if (isD) {
  // DrawingBufferRoutines.line(buf, quad.get(1), quad.get(3), Color.GREEN, 2.0);
  // DrawingBufferRoutines.line(buf, quad.get(0), quad.get(2), Color.RED, 0.5);
  // } else {
  // DrawingBufferRoutines.line(buf, quad.get(0), quad.get(2), Color.GREEN, 2.0);
  // DrawingBufferRoutines.line(buf, quad.get(1), quad.get(3), Color.RED, 0.5);
  // }
  // ani.addFrame(buf, false);
  // }
  // }

}
