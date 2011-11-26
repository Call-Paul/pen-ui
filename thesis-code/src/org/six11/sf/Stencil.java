package org.six11.sf;

import static org.six11.util.Debug.bug;

import java.awt.Shape;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.six11.util.pen.Pt;

public class Stencil {

  private List<Pt> path;
  private List<Segment> segs;

  public Stencil(List<Pt> path, List<Segment> segs) {
    this.path = path;
    this.segs = segs;
    bug("Stencil has " + path.size() + " path points and " + segs.size() + " segments.");
  }

  public boolean isSame(Stencil other) {
    return path.containsAll(other.path) && path.size() == other.path.size();
  }

  public Shape getShape() {
    Path2D shape = new Path2D.Double();
    List<Pt> allPoints = new ArrayList<Pt>();
    for (int i = 0; i < segs.size(); i++) {
      Pt p = path.get(i);
      Segment seg = segs.get(i);
      List<Pt> nextPoints = seg.getPointList();
      if (seg.getP2().equals(p)) {
        Collections.reverse(nextPoints);
      }
      allPoints.addAll(nextPoints);
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
  public static List<Segment> getSegmentList(List<Pt> path, Set<Segment> allGeometry) {
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
    bug("getSegmentList gets this path: " + StencilFinder.n(path));
    bug("and returns the following list of segments:");
    if (ret == null) {
      bug(" <null return value>");
    } else {
      for (Segment s : ret) {
        bug(s.toString());
      }
    }
    return ret;
  }

}
