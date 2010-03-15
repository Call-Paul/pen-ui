package org.six11.skrui.constraint;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.six11.skrui.script.Neanderthal;
import org.six11.skrui.script.Primitive;
import org.six11.skrui.script.Neanderthal.Certainty;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Adjacent extends Constraint {

  public Adjacent(String name, Neanderthal data, String slotA, String slotB) {
    super(name, data, slotA, slotB);
  }

  @Override
  public Certainty check(Primitive[] p) {
    double min = Double.MAX_VALUE;
    min = Math.min(min, p[0].getStartPt().distance(p[1].getStartPt()));
    min = Math.min(min, p[0].getStartPt().distance(p[1].getEndPt()));
    min = Math.min(min, p[0].getEndPt().distance(p[1].getEndPt()));
    min = Math.min(min, p[0].getEndPt().distance(p[1].getStartPt()));
    Certainty ret = Certainty.No;
    if (min < 30) {
      ret = Certainty.Maybe;
    }
    if (min < 20) {
      ret = Certainty.Yes;
    }
    return ret;
  }

  @Override
  public String getShortStr() {
    return "Adj";
  }

}
