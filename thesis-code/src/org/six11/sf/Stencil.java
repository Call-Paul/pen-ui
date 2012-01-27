package org.six11.sf;

import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.six11.util.gui.BoundingBox;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;

//import static org.six11.util.Debug.bug;
//import static org.six11.util.Debug.num;

public class Stencil {

  private static int ID_COUNTER = 0;
  private final int id = ID_COUNTER++;
  private List<Pt> path;
  private List<Segment> segs;
  private Set<Stencil> children;
  private SketchBook model;

  public Stencil(SketchBook model, List<Pt> path, List<Segment> segs) {
    this.model = model;
    this.path = path;
    this.segs = segs;
    this.children = new HashSet<Stencil>();
  }

  public boolean isSame(Stencil other) {
    return path.containsAll(other.path) && path.size() == other.path.size();
  }

  public List<Pt> getPath() {
    return path;
  }

  public boolean isClockwise() {
    Pt c = Functions.getMean(path);
    double crossProd = 0;
    for (int i = 0; i < path.size() - 1; i++) {
      Vec a = new Vec(c, path.get(i));
      Vec b = new Vec(c, path.get(i + 1));
      crossProd = crossProd + a.cross(b);
    }
    boolean ret = crossProd > 0;
    return ret;
  }

  public Shape getOuterShape() {
    Path2D shape = new Path2D.Double();
    // the path list hold segment endpoints only. If there are curved segments, we 
    // also need those curvy bits. That's why we use allPoints and not just path.
    List<Pt> allPoints = new ArrayList<Pt>();
    for (int i = 0; i < segs.size(); i++) {
      Pt p = path.get(i);
      Segment seg = segs.get(i);
      List<Pt> nextPoints = seg.getPointList();
      if (seg.getP2().equals(p)) {
        Collections.reverse(nextPoints);
      }
      for (Pt np : nextPoints) {
        if (allPoints.isEmpty() || allPoints.get(allPoints.size() - 1) != np) {
          allPoints.add(np);
        }
      }
    }
    for (int i = 0; i < allPoints.size(); i++) {
      Pt pt = allPoints.get(i);
      if (i == 0) {
        shape.moveTo(pt.getX(), pt.getY());
      } else {
        shape.lineTo(pt.getX(), pt.getY());
      }
    }
    return shape;
  }

  public Shape getShape(boolean needCCW) {
    Path2D shape = new Path2D.Double();
    shape.setWindingRule(Path2D.WIND_NON_ZERO);
    List<Pt> allPoints = new ArrayList<Pt>();
    for (int i = 0; i < segs.size(); i++) {
      Pt p = path.get(i);
      Segment seg = segs.get(i);
      List<Pt> nextPoints = seg.getPointList();
      if (seg.getP2().equals(p)) {
        Collections.reverse(nextPoints);
      }
      for (Pt np : nextPoints) {
        if (allPoints.isEmpty() || allPoints.get(allPoints.size() - 1) != np) {
          allPoints.add(np);
        }
      }
    }
    boolean cw = isClockwise();
    if ((cw && needCCW) || (!cw && !needCCW)) {
      Collections.reverse(allPoints);
    }
    for (int i = 0; i < allPoints.size(); i++) {
      Pt pt = allPoints.get(i);
      if (i == 0) {
        shape.moveTo(pt.getX(), pt.getY());
      } else {
        shape.lineTo(pt.getX(), pt.getY());
      }
    }
    for (Stencil kid : children) {
      shape.append(kid.getShape(!needCCW), false);
    }
    return shape;
  }

  public void replacePoint(Pt older, Pt newer) {
    for (Stencil c : children) {
      c.replacePoint(older, newer);
    }
    for (int i = 0; i < path.size(); i++) {
      if (path.get(i).equals(older)) {
        path.remove(i);
        path.add(i, newer);
      }
    }
  }

  /**
   * Returns an ordered list of segments based on the given path and segment set. This can be used
   * to construct new Stencils.
   * 
   * @param path
   *          the list of vertices of a new stencil, in order. the first element should be repeated
   *          if you want a closed stencil.
   * @param allGeometry
   *          a set of possible segments. Each leg of the path from path[i] to path[i+1] corresponds
   *          to exactly one segment in the segment set.
   * @return an ordered list of segments such that return[0] is for path[0] to path[1], and so on.
   */
  public static List<Segment> getSegmentList(List<Pt> path, Collection<Segment> allGeometry) {
    List<Segment> ret = new ArrayList<Segment>();
    for (int i = 0; i < path.size() - 1; i++) {
      Pt a = path.get(i);
      Pt b = path.get(i + 1);
      boolean ok = false;
      for (Segment seg : allGeometry) {
        if ((seg.getP1() == a && seg.getP2() == b) || (seg.getP1() == b && seg.getP2() == a)) {
          ret.add(seg);
          ok = true;
          break;
        }
      }
      if (!ok) {
        ret = null;
        break;
      }
    }
    return ret;
  }

  public Area intersect(Area area) {
    Area myArea = new Area(getOuterShape());
    myArea.intersect(area);
    return myArea;
  }

  public boolean isSuperset(Stencil other) {
    return path.size() > other.path.size() && path.containsAll(other.getPath());
  }

  public String toString() {
    StringBuilder buf = new StringBuilder();
    for (Stencil c : children) {
      buf.append(c.toString() + " ");
    }
    if (buf.length() > 0) {
      buf.insert(0, "[");
      buf.deleteCharAt(buf.length() - 1);
      buf.append("]");
    }
    return "T" + id + "_" + (isClockwise() ? "cw" : "ccw") + buf.toString();//StencilFinder.n(path);
  }

  public BoundingBox getBoundingBox() {
    return new BoundingBox(path);
  }

  //  public Stencil getAnonymousCopy() {
  //    List<Pt> anonPts = new ArrayList<Pt>();
  //    List<Segment> anonSegs = new ArrayList<Segment>();
  //    for (Pt pt : path) {
  //      anonPts.add(pt.copyXYT());
  //    }
  //    for (Segment seg : segs) {
  //      anonSegs.add(seg.copy());
  //    }
  //    return new Stencil(anonPts, anonSegs);
  //  }

  public boolean involves(Segment seg) {
    boolean ret = false;
    for (Stencil c : children) {
      if (c.involves(seg)) {
        ret = true;
        break;
      }
    }
    if (!ret) {
      ret = segs.contains(seg);
    }
    return ret;
  }

  public boolean surrounds(Stencil c) {
    boolean ret = false;
    Area childArea = new Area(c.getOuterShape());
    Area myArea = new Area(getOuterShape());
    myArea.intersect(childArea);
    ret = myArea.equals(childArea);
    return ret;
  }

  public void add(Set<Stencil> kids) {
    Set<Stencil> no = new HashSet<Stencil>();
    for (Stencil k : kids) {
      for (Stencil c : children) {
        boolean samePath = k.getPath().containsAll(c.getPath());
        if (samePath) {
          no.add(k);
        }
      }
    }
    kids.removeAll(no);
    children.addAll(kids);
  }

  public void removeGeometry(Segment seg) {
    if (segs.contains(seg)) {
      segs.remove(seg);
    } else {
      Set<Stencil> doomed = new HashSet<Stencil>();
      for (Stencil c : children) {
        c.removeGeometry(seg);
        if (!c.isValid()) {
          doomed.add(c);
        }
      }
      children.removeAll(doomed);
    }
  }

  public boolean isValid() {
    boolean ret = true;
    for (int i=0; i < path.size() - 1; i++) {
      Pt a = path.get(i);
      Pt b = path.get(i+1);
      if (!model.hasSegment(a, b)) {
        ret = false;
        break;
      }
    }
    return ret;
  }

  public Set<Stencil> getChildren() {
    return children;
  }

}
