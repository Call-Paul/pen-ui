package org.six11.sf;

import static java.lang.Math.abs;
import static java.lang.Math.toDegrees;
//import static org.six11.util.Debug.num;
import static org.six11.util.Debug.bug;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.six11.sf.EndCap.Intersection;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;

public class ConstraintAnalyzer {

  SketchBook model;

  public ConstraintAnalyzer(SketchBook model) {
    this.model = model;
  }

  public void analyze(Collection<Segment> segs, boolean autolatch) {
    Set<EndCap> caps = getCurrentEndCaps(model.getGeometry()); // set of all endcaps
    Set<EndCap> newCaps = getCurrentEndCaps(segs); // set of recently added endcaps (based on struc)
    Set<EndCap.Intersection> examined = new HashSet<EndCap.Intersection>();
    Set<EndCap.Intersection> success = new HashSet<EndCap.Intersection>();
    // Compare the caps of the new ink with each cap in the model
    for (EndCap c1 : newCaps) {
      for (EndCap c2 : caps) {
        if (!isAlreadyExamined(examined, c1, c2)) {
          EndCap.Intersection ix = c1.intersectInCap(c2);
          if (ix.intersects) {
            success.add(ix);
            //            DrawingBufferRoutines.dot(bugBuf, ix.pt, 4, 0.4, Color.BLACK, Color.BLUE);
          }
          examined.add(ix);
        }
      }
    }

    // iterate through the successful intersections and merge related ones
    Set<EndCap.Group> groups = new HashSet<EndCap.Group>();
    for (Intersection ix : success) {
      groups.add(new EndCap.Group(ix));
    }
    if (autolatch) {
      while (!merged(groups)) {
        ;
      }
      for (EndCap.Group group : groups) {
        Pt spot = group.adjustMembers(); // note: spot does not have time data
        // latch points in this group together at the spot
        for (Pt capPt : group.getPoints()) {
          model.replace(capPt, spot);
        }
      }
    }
    model.getConstraints().wakeUp();
  }

  /**
   * Tries to merge two groups. If it can, it does so and returns true. The 'groups' input param is
   * modified.
   */
  private boolean merged(Set<EndCap.Group> groups) {
    boolean ret = true;
    EndCap.Group doomed = null;
    outer: for (EndCap.Group cursor : groups) {
      for (EndCap.Group g : groups) {
        if ((g != cursor) && cursor.has(g)) {
          cursor.merge(g);
          doomed = g;
          ret = false;
          break outer;
        }
      }
    }
    if (doomed != null) { // avoid concurrent modification exception
      groups.remove(doomed);
    }
    return ret;
  }

  private boolean isAlreadyExamined(Set<Intersection> examined, EndCap c1, EndCap c2) {
    boolean ret = false;
    for (Intersection ix : examined) {
      if (ix.usesSameCaps(c1, c2)) {
        ret = true;
        break;
      }
    }
    return ret;
  }

  private Set<EndCap> getCurrentEndCaps(Collection<Segment> segs) {
    Set<EndCap> ret = new HashSet<EndCap>();
    for (Segment seg : segs) {
      if (seg.hasEndCaps()) {
        ret.addAll(seg.getEndCaps());
      }
    }
    return ret;
  }

  /**
   * This tries to locate junctions among the given input segments, and if two share a fairly
   * oblique angle, it will merge them. For example, if you draw a line but it isn't quite long
   * enough so you extend it by drawing another line, you can merge them into a single line. It also
   * works for other segment types. Splines, for example, can be merged to get rid of the
   * discontinuity at the former junction point.
   * 
   * This will only merge a single pair.
   * 
   * @param segs
   */
  public void mergeSegments(Collection<Segment> segs) {
    Set<Segment> nonsingular = new HashSet<Segment>();
    for (Segment seg : segs) {
      if (!seg.isSingular()) {
        nonsingular.add(seg);
      }
    }
    StencilFinder sf = new StencilFinder(model);
    Map<Pt, Set<Pt>> adj = sf.makeAdjacency(nonsingular);
    StringBuilder buf = new StringBuilder();
    for (Map.Entry<Pt, Set<Pt>> entry : adj.entrySet()) {
      buf.setLength(0);
      if (entry.getValue().size() == 2) {
        List<Segment> pair = new ArrayList<Segment>();
        for (Segment seg : nonsingular) {
          if (seg.involves(entry.getKey())) {
            pair.add(seg);
            buf.append(seg.getType() + "");
          }
        }
        if (pair.size() == 2) {
          Vec dirA, dirB;
          Pt junct = entry.getKey();
          Segment segA = pair.get(0);
          Segment segB = pair.get(1);
          if (segA.getP1() == junct) {
            dirA = segA.getStartDir();
          } else {
            dirA = segA.getEndDir();
          }
          if (segB.getP1() == junct) {
            dirB = segB.getStartDir();
          } else {
            dirB = segB.getEndDir();
          }
          double ang = Functions.getSignedAngleBetween(dirA, dirB);
          double thresh = 40.0;
          if (buf.toString().equals("LineLine")) {
            thresh = 160.0;
          }
          if (abs(toDegrees(ang)) > thresh) {
            if (buf.toString().equals("LineLine")) {
              mergeLines(junct, segA, segB);
            } else if (buf.toString().equals("CurveCurve")) {
              mergeCurves(junct, segA, segB);
            }
          }
        }
      }
    }
  }

  private boolean mergeCurves(Pt junct, Segment segA, Segment segB) {
    boolean ret = false;
    boolean dirA = false;
    boolean dirB = false;
    dirA = segA.getP1() == junct ? false : true;
    dirB = segB.getP1() == junct ? true : false;
    List<Pt> newPoints = new ArrayList<Pt>();
    if (dirA) {
      newPoints.addAll(segA.asPolyline());
    } else {
      List<Pt> tmpA = new ArrayList<Pt>();
      tmpA.addAll(segA.asPolyline());
      Collections.reverse(tmpA);
      newPoints.addAll(tmpA);
    }
    if (dirB) {
      newPoints.addAll(segB.asPolyline());
    } else {
      List<Pt> tmpB = new ArrayList<Pt>();
      tmpB.addAll(segB.asPolyline());
      Collections.reverse(tmpB);
      tmpB.remove(0);
      newPoints.addAll(tmpB);
    }

    Segment newSpline = new Segment(new CurvySegment(newPoints));
    model.replace(segA, newSpline);
    model.replace(segB, newSpline);
    ret = true;

    return ret;
  }

  private boolean mergeLines(Pt junct, Segment segA, Segment segB) {
    boolean ret = false;
    Pt p1 = null;
    Pt p2 = null;
    p1 = segA.getP1() == junct ? segA.getP2() : segA.getP1();
    p2 = segB.getP1() == junct ? segB.getP2() : segB.getP1();
    if ((p1 != null) && (p2 != null)) {
      Segment newLine = new Segment(new LineSegment(p1, p2));
      model.replace(segA, newLine);
      model.replace(segB, newLine);
      ret = true;
    }
    return ret;
  }
}
