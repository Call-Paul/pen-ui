package org.six11.sf.constr;

import static org.six11.util.Debug.bug;

import java.util.HashSet;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;
import org.six11.sf.Segment;
import org.six11.sf.SketchBook;
import org.six11.util.pen.Pt;
import org.six11.util.solve.Constraint;
import org.six11.util.solve.DistanceConstraint;
import org.six11.util.solve.MultisourceNumericValue;
import org.six11.util.solve.MultisourceNumericValue.Source;
import org.six11.util.solve.NumericValue;

public class SameLengthUserConstraint extends UserConstraint {

  public SameLengthUserConstraint(SketchBook model, Constraint... cs) {
    super(model, Type.SameLength, cs);
  }

  public SameLengthUserConstraint(SketchBook model, JSONObject ucObj) throws JSONException {
    super(model, Type.SameLength, ucObj);
    if (ucObj.getBoolean("multi")) {
      MultisourceNumericValue mnv = new MultisourceNumericValue();
      for (Constraint c : getConstraints()) {
        DistanceConstraint dc = (DistanceConstraint) c;
        Segment seg = model.getSegment(dc.getP1(), dc.getP2());
        Source s = mkSource(seg);
        mnv.addValue(s);
        dc.setValue(mnv);
      }
    }
  }
  public static MultisourceNumericValue.Source mkSource(final Segment seg) {
    return new MultisourceNumericValue.Source() {
      public double getValue() {
        return seg.length();
      }
    };
  }
  
  /**
   * Same-length constraints can either be multi-source (where the length is determined by a bunch
   * of source values) or single-source (where the length is fixed to a number).
   * 
   * @return true if the value is a MultisourceNumericValue
   */
  public boolean isMultiSource() {
    return (getConstraints().size() > 0) && (getValue() instanceof MultisourceNumericValue);
  }

  @Override
  public boolean isValid() {
    boolean ret = true;
    if (getConstraints().size() == 0) {
      bug("SLUC invalid due to having zero constraints.");
      ret = false;
    } else if (getConstraints().size() == 1) {
      DistanceConstraint dc = getConstraints().toArray(new DistanceConstraint[1])[0];
      ret = (dc.getValue() instanceof NumericValue)
          && !(dc.getValue() instanceof MultisourceNumericValue);
      bug("SLUC has one constraint. But is it valid? " + ret);
    } else {
      ret = true;
      bug("SLUC valid because it has " + getConstraints().size() + " constraints.");
    }
    return ret;
  }

  public void removeInvalid() {
    Set<Constraint> doomed = new HashSet<Constraint>();
    for (Constraint c : getConstraints()) {
      DistanceConstraint dc = (DistanceConstraint) c;
      if (!model.hasSegment(dc.a, dc.b)) {
        doomed.add(c);
      }
    }
    for (Constraint c : doomed) {
      removeConstraint(c);
    }
  }

  public void addDist(Pt p1, Pt p2, NumericValue dist) {
    NumericValue nv = getValue();
    bug("Initial nv value = " + nv);
    if (nv != dist) {
      if (nv instanceof MultisourceNumericValue) {
        MultisourceNumericValue mnv = (MultisourceNumericValue) nv;
        if (dist instanceof MultisourceNumericValue) {
          MultisourceNumericValue mdist = (MultisourceNumericValue) dist;
          for (Source src : mdist.getSources()) {
            mnv.addValue(src);
          }
        } else {
          bug("Case 1 can't handle this. nv is " + nv);
        }
      } else {
        bug("Case 2");
        nv = dist;
        bug("Setting nv = " + dist);
      }
    }
    addConstraint(new DistanceConstraint(p1, p2, nv));
  }

  public NumericValue getValue() {
    NumericValue ret = null;
    if (getConstraints().size() > 0) {
      DistanceConstraint dc = getConstraints().toArray(new DistanceConstraint[1])[0];
      ret = dc.getValue();
    } else {
      ret = null;
    }
    return ret;
  }

  public void setValue(NumericValue nv) {
    for (Constraint c : getConstraints()) {
      DistanceConstraint dc = (DistanceConstraint) c;
      dc.setValue(nv);
    }
  }
  
  public JSONObject toJson() throws JSONException {
    JSONObject ret = super.toJson();
    if (isMultiSource()) {
      ret.put("multi", true);
    } else {
      ret.put("multi", false);
    }
    return ret;
  }

}
