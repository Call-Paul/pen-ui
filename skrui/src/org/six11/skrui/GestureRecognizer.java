package org.six11.skrui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.six11.skrui.domain.Domain;
import org.six11.skrui.domain.GestureCross;
import org.six11.skrui.domain.GestureShapeTemplate;
import org.six11.skrui.domain.GestureTap;
import org.six11.skrui.domain.Shape;
import org.six11.skrui.domain.ShapeTemplate;
import org.six11.skrui.script.Neanderthal;
import org.six11.skrui.script.Primitive;
import org.six11.util.Debug;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Sequence;

/**
 * This gesture recognizer looks for time-ordered sequences of input that satisfy constraints. The
 * sequence "dot-line-line" is different from "line-dot-line". A ShapeTemplate is applied to
 * determine if a sequence meets the constraints.
 * 
 * Here's an example of how the constraints are used. "dot-line-line" might have constraints that
 * specify (1) the lines must intersect at approximately their midpoints, (2) the dot must be within
 * one line-length of the line/line intersection, and (3) that the lines be shorter than 30 pixels.
 * If the user provides "line-dot-line", this template will never be checked because the input was
 * provided in the wrong order.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class GestureRecognizer extends Domain {

  int largestChain = 0;
  List<Set<Primitive>> primitives;

  public GestureRecognizer(Neanderthal data) {
    super("Gesture Recognizer", data);
    primitives = new ArrayList<Set<Primitive>>();
    GestureCross cross = new GestureCross(this); 
    addShapeTemplate(cross);
    GestureTap tap = new GestureTap(this);
    addShapeTemplate(tap);
  }

  public void addShapeTemplate(ShapeTemplate st) {
    templates.add(st);
    largestChain = Math.max(largestChain, st.getSlotTypes().size());
  }

  public void add(Sequence seq) {
    SortedSet<Primitive> all = Neanderthal.getPrimitiveSet(seq);
    SortedSet<Primitive> cotemp = new TreeSet<Primitive>(Primitive.sortByIndex);
    Primitive recent = null;
    for (Primitive caveman : all) {
      if (recent == null) {
        cotemp.add(caveman);
      } else if (recent.getStartIdx() == caveman.getStartIdx()) {
        cotemp.add(caveman);
      } else {
        primitives.add(cotemp);
        cotemp = new TreeSet<Primitive>(Primitive.sortByIndex);
        cotemp.add(caveman);
      }
      recent = caveman;
    }
    primitives.add(cotemp);
    while (primitives.size() > largestChain) {
      primitives.remove(0);
    }
    for (ShapeTemplate st : templates) {
      detect((GestureShapeTemplate) st);
    }
  }

  private void detect(GestureShapeTemplate st) {
    if (st.getSlotTypes().size() <= primitives.size()) {
      int offset = primitives.size() - st.getSlotTypes().size();
      List<Primitive> matches = new ArrayList<Primitive>();
      for (int i = 0; i < st.getSlotTypes().size(); i++) {
        Primitive p = match(st.getSlotTypes().get(i), primitives.get(offset + i));
        if (p == null) {
          break;
        } else {
          matches.add(p);
        }
      }
      if (matches.size() == st.getSlotTypes().size()) {
        List<Shape> results = st.apply(new HashSet<Primitive>(matches));
        if (results.size() > 0) {
          for (Shape s : results) {
            bug(" --> " + s);
            st.trigger(data, s, matches);
          }
        }
      }
    }
  }

  private String listPrims(Set<Primitive> set) {
    StringBuilder buf = new StringBuilder();
    for (Primitive p : set) {
      buf.append(p + " ");
    }
    return "{" + buf.toString().trim() + "}";
  }

  /**
   * Try to find a primitive of type st.getSlotTypes(i) in the primitives list in position i.
   */
  private Primitive match(Class<? extends Primitive> type, Set<Primitive> options) {
    Primitive ret = null;
    for (Primitive p : options) {
      if (p.getClass() == type) {
        ret = p;
        break;
      }
    }
    return ret;
  }

  private static void bug(String what) {
    Debug.out("GestureRecognizer", what);
  }
}
