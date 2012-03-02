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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.six11.sf.Segment.Type;
import org.six11.util.Debug;
import org.six11.util.data.Lists;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;
import org.six11.util.solve.VariableBank;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

public class Stencil {
  private List<Pt> path;
  private List<Segment> segs;
  private SketchBook model;
  private Set<Stencil> children;
  private static int ID_COUNT = 0;
  private final int id;

  public Stencil(SketchBook model, List<Pt> path, List<Segment> segs) {
    this.id = ID_COUNT++;
    this.model = model;
    this.path = new ArrayList<Pt>(path);
    if (path.get(0) != path.get(path.size() - 1)) {
      path.add(path.get(0));
    }
    this.segs = new ArrayList<Segment>(segs);
    this.children = new HashSet<Stencil>();
    //    bug("Made multi-segment stencil: " + this + ", valid=" + isValid());
  }

  public Stencil(SketchBook model, Segment s) {
    this.id = ID_COUNT++;
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

  public Stencil(SketchBook model, JSONObject json) throws JSONException {
    // "id"       : int, my ID
    // "path"     : JSONArray of point names
    // "segs"     : JSONArray of segment IDs
    // "children" : JSONArray of JSONObjecs, each of which is a stencil produced from this toJson() method
    this.model = model;

    this.id = json.getInt("id");
    JSONArray pathArr = json.getJSONArray("path");
    JSONArray segsArr = json.getJSONArray("segs");
    JSONArray childrenArr = json.getJSONArray("children");

    this.path = new ArrayList<Pt>();
    VariableBank vars = model.getConstraints().getVars();
    for (int i = 0; i < pathArr.length(); i++) {
      String ptName = pathArr.getString(i);
      Pt pt = vars.getPointWithName(ptName);
      path.add(pt);
    }

    this.segs = new ArrayList<Segment>();
    for (int i = 0; i < segsArr.length(); i++) {
      int segID = segsArr.getInt(i);
      Segment seg = model.getSegment(segID);
      segs.add(seg);
    }

    this.children = new HashSet<Stencil>();
    for (int i = 0; i < childrenArr.length(); i++) {
      JSONObject childObj = childrenArr.getJSONObject(i);
      Stencil childStencil = new Stencil(model, childObj);
      children.add(childStencil);
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
    boolean ret = crossProd > 0;
    return ret;
  }

  private List<Pt> getTurnPath() {
    List<Pt> ret = path;
    if (segs.size() == 2) {
      ret = new ArrayList<Pt>();
      for (int i = 0; i < segs.size(); i++) {
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
  public List<Pt> getAllPoints() {
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
      for (int i = 0; i < path.size(); i++) {
        Pt a = path.get(i);
        Pt b = path.get((i + 1) % path.size());
        Debug.errorOnNull(model, "model");
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
    //    bug("Does " + this + " surround + " + c + "? " + ret);
    return ret;
  }

  public void add(Set<Stencil> kids) {
    bug("Attempting to add " + kids.size() + " children to my list.");
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

  public int getId() {
    return id;
  }

  public JSONObject toJson() throws JSONException {
    // "id"       : int, my ID
    // "path"     : JSONArray of point names
    // "segs"     : JSONArray of segment IDs
    // "children" : JSONArray of JSONObjecs, each of which is a stencil produced from this toJson() method

    JSONObject ret = new JSONObject();
    ret.put("id", getId());

    JSONArray pathArr = new JSONArray();
    for (Pt pt : path) {
      pathArr.put(SketchBook.n(pt));
    }
    ret.put("path", pathArr);

    JSONArray segsArr = new JSONArray();
    for (Segment seg : segs) {
      segsArr.put(seg.getId());
    }
    ret.put("segs", segsArr);

    JSONArray childrenArr = new JSONArray();
    for (Stencil c : children) {
      childrenArr.put(c.toJson());
    }
    ret.put("children", childrenArr);

    return ret;

  }

  public void removeInvalidChildren(Set<Stencil> parents) {
    parents.add(this);
    Set<Segment> allSegs = new HashSet<Segment>();
    for (Stencil p : parents) {
      allSegs.addAll(p.segs);
    }
    Set<Stencil> invaid = new HashSet<Stencil>();
    for (Stencil c : children) {
      c.removeInvalidChildren(parents);
      if (!c.isValid()) {
        invaid.add(c);
      } else if (Lists.intersect(allSegs, c.segs).size() > 0) {
        invaid.add(c);
      }
    }
    children.removeAll(invaid);
    parents.remove(this);
  }

}
