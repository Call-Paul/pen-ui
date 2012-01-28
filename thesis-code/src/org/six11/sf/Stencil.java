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

import org.six11.sf.Segment.Type;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;
import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

public class Stencil {
  private List<Pt> path;
  private List<Segment> segs;
  private SketchBook model;
  private Set<Stencil> children;
  private static int ID_COUNT = 0;
  private int id = ID_COUNT++;

  public Stencil(SketchBook model, List<Pt> path, List<Segment> segs) {
    this.model = model;
    this.path = new ArrayList<Pt>(path);
    if (path.get(0) != path.get(path.size() - 1)) {
      path.add(path.get(0));
    }
    this.segs = new ArrayList<Segment>(segs);
    this.children = new HashSet<Stencil>();
  }

  public Stencil(SketchBook model, Segment s) {
    if (s.isClosed()) {
      this.model = model;
      this.segs = new ArrayList<Segment>();
      segs.add(s);
      this.path = new ArrayList<Pt>();
      this.children = new HashSet<Stencil>();
    } else {
      bug("Error: created single-segment stencil with non-closed segment: " + s);
    }
  }

  public boolean hasPath(List<Segment> otherSegPath) {
    return segs.containsAll(otherSegPath);
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

  public Shape getShape(boolean needCCW) {
    Path2D shape = new Path2D.Double();
    shape.setWindingRule(Path2D.WIND_NON_ZERO);
    List<Pt> allPoints = getAllPoints();
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

  private boolean isClockwise() {
    List<Pt> turns = getTurnPath();
    Pt c = Functions.getMean(turns);
    double crossProd = 0;
    for (int i = 0; i < turns.size() - 1; i++) {
      Vec a = new Vec(c, turns.get(i));
      Vec b = new Vec(c, turns.get(i + 1));
      crossProd = crossProd + a.cross(b);
    }
    if (Math.abs(crossProd) < 0.01) {
      bug("cross product too close to zero to be meaningful.");
    }
    boolean ret = crossProd > 0;
    return ret;
  }

  private List<Pt> getTurnPath() {
    List<Pt> ret = path;
    if (segs.size() == 2) {
      ret = new ArrayList<Pt>();
      for (int i = 0; i < path.size(); i++) {
        ret.add(path.get(i));
        if (segs.get(i).getType() != Segment.Type.Line) {
          ret.add(segs.get(i).getVisualMidpoint());
        }
      }
    } else if (segs.size() == 1) {
      Segment seg = segs.get(0);
      if (seg.getType() != Segment.Type.Line) {
        List<Pt> source = seg.asPolyline();
        int sz = source.size();
        int idx1 = sz / 3;
        int idx2 = (2 * sz) / 3;
        ret = new ArrayList<Pt>();
        ret.add(source.get(0));
        ret.add(source.get(idx1));
        ret.add(source.get(idx2));
        ret.add(source.get(source.size() - 1));
      }
    }
    return ret;
  }

  public Area intersect(Area area) {
    Area myArea = new Area(getOuterShape());
    myArea.intersect(area);
    return myArea;
  }

  private Shape getOuterShape() {
    Path2D shape = new Path2D.Double();
    // the path list hold segment endpoints only. If there are curved segments, we 
    // also need those curvy bits. That's why we use allPoints and not just path.
    List<Pt> allPoints = getAllPoints();
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

  /**
   * Returns the geometry defining the outside of this stencil.
   * 
   * @return
   */
  private List<Pt> getAllPoints() {
    List<Pt> allPoints = new ArrayList<Pt>();
    if (segs.size() == 1) {
      allPoints.addAll(segs.get(0).getPointList());
    } else {
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
    }
    return allPoints;
  }

  public boolean isValid() {
    boolean ret = true;
    if (!isSingular()) { // single-segment stencils are always valid.
      for (int i = 0; i < path.size() - 1; i++) {
        Pt a = path.get(i);
        Pt b = path.get(i + 1);
        Segment s = model.getSegment(a, b);
        if (s == null || !segs.contains(s)) {
          ret = false;
          break;
        }
      }
      for (Segment s : segs) {
        if (!model.hasSegment(s)) {
          ret = false;
          break;
        }
      }
    } else {
      bug("Stencil " + this + " is singular so it is always valid.");
    }
    bug("Stencil " + this + " valid? " + ret);
    return ret;
  }

  public Collection<Stencil> getChildren() {
    return children;
  }

  /**
   * Replaces the older point with the newer in any children and in this stencil. This only affects
   * the point list and NOT the segment list, which should be updated elsewhere.
   * 
   * @param older
   * @param newer
   */
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

  public List<Pt> getPath() {
    return path;
  }

  public boolean isSame(Stencil other) {
    return hasPath(other.segs);
  }

  public boolean isSuperset(Stencil other) {
    return segs.size() > other.segs.size() && segs.containsAll(other.getSegs());
  }

  public boolean surrounds(Stencil c) {
    boolean ret = false;
    Area childArea = new Area(c.getOuterShape());
    Area myArea = new Area(getOuterShape());
    myArea.intersect(childArea);
    ret = myArea.equals(childArea);
    bug("Does " + this + " surround + " + c + "? " + ret);
    return ret;
  }

  public void add(Set<Stencil> kids) {
    Set<Stencil> no = new HashSet<Stencil>();
    for (Stencil k : kids) {
      for (Stencil c : children) {
        if (k.isSame(c)) {
          no.add(k);
        }
        //        boolean samePath = k.getPath().containsAll(c.getPath());
        //        if (samePath) {
        //          no.add(k);
        //        }
      }
    }
    kids.removeAll(no);
    children.addAll(kids);
  }

  public List<Segment> getSegs() {
    return segs;
  }

  public boolean isSingular() {
    return segs.size() == 1;
  }

  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append("T" + id + ": ");
    for (Segment s : segs) {
      buf.append(s.getType() + "-" + s.getId() + " ");
    }
    return buf.toString().trim();
  }

}
