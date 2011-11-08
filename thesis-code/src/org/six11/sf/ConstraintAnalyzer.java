package org.six11.sf;

import static org.six11.util.Debug.bug;

import java.awt.Color;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.six11.sf.EndCap.Intersection;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;

public class ConstraintAnalyzer {

  SketchBook model;

  public ConstraintAnalyzer(SketchBook model) {
    this.model = model;
  }

  public void analyze(Collection<StructuredInk> struc) {
    // make a current map of the endcap locations for all structured ink

    DrawingBuffer bugBuf = model.getLayers().getLayer(GraphicDebug.DB_LATCH_LAYER);
    Set<EndCap> caps = getCurrentEndCaps(model.ink);
    Set<EndCap> newCaps = getCurrentEndCaps(struc);
    Set<EndCap.Intersection> examined = new HashSet<EndCap.Intersection>();
    Set<EndCap.Intersection> success = new HashSet<EndCap.Intersection>();
    // Compare the caps of the new ink with each cap in the model
    for (EndCap c1 : newCaps) {
      for (EndCap c2 : caps) {
        if (!isAlreadyExamined(examined, c1, c2)) {
          EndCap.Intersection ix = c1.intersectInCap(c2);
          if (ix.intersects) {
            success.add(ix);
            bug("Caps intersect. Marking it with a fat blue dot.");
            DrawingBufferRoutines.dot(bugBuf, ix.pt, 8, 1, Color.BLACK, Color.BLUE);
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
      group.adjustMembers();
    }

    model.getLayers().repaint();
  }

  /**
   * Tries to merge two groups. If it can, it does so and returns true. The 'groups' input param is
   * modified.
   */
  private boolean merged(Set<EndCap.Group> groups) {
    boolean ret = true;
    EndCap.Group doomed = null;
    outer:
    for (EndCap.Group cursor : groups) {
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

  private Set<EndCap> getCurrentEndCaps(Collection<? extends Ink> someInk) {
    Set<EndCap> ret = new HashSet<EndCap>();
    for (Ink k : someInk) {
      if (k instanceof StructuredInk) {
        ret.addAll(((StructuredInk) k).getEndCaps());
      }
    }
    return ret;
  }
}
