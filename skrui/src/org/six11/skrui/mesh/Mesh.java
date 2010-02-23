package org.six11.skrui.mesh;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.six11.skrui.DrawingBufferRoutines;
import org.six11.skrui.script.Animation;
import org.six11.util.Debug;
import org.six11.util.gui.BoundingBox;
import org.six11.util.pen.DrawingBuffer;
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

  Set<Triangle> triangles;
  Animation ani;

  public static HalfEdge he(Pt pt) {
    return (HalfEdge) pt.getAttribute(HALF_EDGE);
  }

  public Mesh(List<Pt> points, double edgeLengthThreshold, Animation ani) {
    long start = System.nanoTime();
    this.ani = ani;
    triangles = new HashSet<Triangle>();

    BoundingBox bb = new BoundingBox();
    for (Pt pt : points) {
      bb.add(pt);
    }
    // DrawingBuffer boundingBuf = new DrawingBuffer();
    bb.grow(2);
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
      addPoint(newVert);
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
                  addPointOnEdge(e, splitLocation);
                  numRepaired++;
                  break;
                }
              }
            }
          }
        }
        animate(ani);
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

  private void animate(Animation ani) {
    if (ani != null) {
      DrawingBuffer db = new DrawingBuffer();
      for (Triangle t : getTriangles()) {
        if (t.getMeshLocation() == Where.Inside) {
          List<Pt> drawUs = t.getPoints();
          drawUs.add(drawUs.get(0));
          DrawingBufferRoutines.lines(db, drawUs, Color.LIGHT_GRAY, 0.5);
          DrawingBufferRoutines.dot(db, t.getCentroid(), 4.0, 0.4, Color.GRAY, Color.LIGHT_GRAY);
        }
      }
      ani.addFrame(db, false);
    }
  }

  // private void animate(DrawingBuffer boundingBuf, BoundingBox bb, Color c) {
  // boundingBuf.setColor(c);
  // boundingBuf.down();
  // boundingBuf.addShape(bb.getRectangle());
  // boundingBuf.up();
  // }

  private void addPoint(Pt newVert) {
    TriangleWhere tw = findTriangle(newVert);
    if (tw.where == Where.Inside) {
      addPointInside(tw.triangle, newVert);
    }
    if (tw.where == Where.Boundary) {
      HalfEdge splitMe = tw.triangle.getEdgeContaining(newVert);
      if (splitMe == null) {
        bug("I would like to insert a point that should be on a boundary, "
            + "but I can't figure out where.");
      } else {
        bug("Adding point on edge...");
        addPointOnEdge(splitMe, newVert);
        bug("If I didn't explode into flames, it might have worked.");
      }
    }
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
    // animate(ani);
    for (Pt pt : y.getPoints()) {
      if (triangles.contains(y) && !isDelaunay(pt, y)) {
        flip(pt, y);
      }
    }
    for (Pt pt : z.getPoints()) {
      if (triangles.contains(z) && !isDelaunay(pt, z)) {
        flip(pt, z);
      }
    }
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

  /**
   * Which edges border this face?
   */
  public Set<HalfEdge> getEdges(Triangle t) {
    return t.getEdges();
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
      Pt a = quad.get(0);
      Pt b = quad.get(1);
      Pt c = quad.get(2);
      Pt d = quad.get(3);
      double det = Functions.getDeterminant(a, b, c, d);
      ret = (det <= 0);
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
