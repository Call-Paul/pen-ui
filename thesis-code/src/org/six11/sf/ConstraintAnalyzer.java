package org.six11.sf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.six11.sf.EndCap.Intersection;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;
import static org.six11.sf.StencilFinder.n;
import static java.lang.Math.toDegrees;
import static java.lang.Math.abs;

public class ConstraintAnalyzer {

  SketchBook model;

  public ConstraintAnalyzer(SketchBook model) {
    this.model = model;
  }

  public void analyze(Collection<Segment> segs) {
    //    DrawingBuffer bugBuf = model.getLayers().getLayer(GraphicDebug.DB_LATCH_LAYER);
    //    bugBuf.clear();
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
    while (!merged(groups))
      ;
    for (EndCap.Group group : groups) {
      Pt spot = group.adjustMembers(); // note: spot does not have time data
      // latch points in this group together at the spot
      for (Pt capPt : group.getPoints()) {
        model.replace(capPt, spot);
      }
    }
    model.getConstraints().wakeUp();
    model.getLayers().repaint();
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
        if (g != cursor && cursor.has(g)) {
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
    StencilFinder sf = new StencilFinder();
    Map<Pt, Set<Pt>> adj = sf.makeAdjacency(nonsingular);
    sf.printAdjacencyTable();
    StringBuilder buf = new StringBuilder();
    for (Map.Entry<Pt, Set<Pt>> entry : adj.entrySet()) {
      buf.setLength(0);
      if (entry.getValue().size() == 2) {
        bug("Found a junction at " + n(entry.getKey()));
        List<Segment> pair = new ArrayList<Segment>();
        for (Segment seg : nonsingular) {
          if (seg.involves(entry.getKey())) {
            pair.add(seg);
            buf.append(seg.getType() + "");
          }
        }
        bug("Num. segments related to that point: " + pair.size() + ", combined type string: "
            + buf.toString());
        if (pair.size() == 2) {
          boolean result = false;
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
          bug("dirA: " + num(dirA));
          bug("dirB: " + num(dirB));
          double ang = Functions.getSignedAngleBetween(dirA, dirB);
          bug("Ang: " + num(toDegrees(ang)));
          if (abs(toDegrees(ang)) > 160.0) {
            bug("Angle ok. Segments are of type: " + segA.getType() + " and "
                + segB.getType());
            if (buf.toString().equals("LineLine")) {
              result = mergeLines(junct, segA, segB);
            } else if (buf.toString().equals("CurveCurve")) {
              result = mergeCurves(junct, segA, segB);
            }
          }
          if (result) {
            bug("Merged segments " + pair.get(0) + " and " + pair.get(1) + "!");
            break;
          }
        }
      }
    }
  }

  private boolean mergeCurves(Pt junct, Segment segA, Segment segB) {
    boolean ret = false;
    bug("Implement me!");
    return ret;
  }
  private boolean mergeLines(Pt junct, Segment segA, Segment segB) {
    boolean ret = false;
    bug("Implmement me!");
    return ret;
  }
}
