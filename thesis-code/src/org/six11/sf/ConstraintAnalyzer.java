package org.six11.sf;

import java.awt.Color;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.six11.sf.EndCap.Intersection;
import org.six11.util.math.ClusterThing;
import org.six11.util.math.ClusterThing.Cluster;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Pt;
import org.six11.util.solve.DistanceConstraint;
import org.six11.util.solve.NumericValue;

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
//      DrawingBufferRoutines.dot(bugBuf, spot, 4, 0.4, Color.BLACK, Color.MAGENTA);
      for (Pt capPt : group.getPoints()) {
        model.replace(capPt, spot);
      }
    }

//    // Now the fun part: look at new segment lengths and see if they are similar to other segments.
//    final ClusterThing<Segment> lengthClusters = new ClusterThing<Segment>() {
//      public double query(Segment t) {
//        return t.length();
//      }
//    };
//    for (Segment seg : segs) {
//      lengthClusters.add(seg);
//    }
//    lengthClusters.computeClusters();
//
//    ClusterThing.ClusterFilter<Segment> filter = lengthClusters.getRatioFilter(0.9);
//    List<Cluster<Segment>> similar = lengthClusters.search(filter);
//    for (Cluster<Segment> cluster : similar) {
//      if (cluster.getMembers().size() > 1) {
//        double sum = 0;
//        int n = 0;
//        for (Segment s : cluster.getMembers()) {
//          sum = sum + s.length();
//          n++;
//        }
//        double len = sum / n;
//        for (Segment s : cluster.getMembers()) {
//          DistanceConstraint lengthConstraint = new DistanceConstraint(s.getP1(), s.getP2(),
//              new NumericValue(len));
//          model.getConstraints().addConstraint(lengthConstraint);
//        }
//      }
//    }
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
      ret.addAll(seg.getEndCaps());
    }
    return ret;
  }
}
