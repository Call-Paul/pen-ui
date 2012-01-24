package org.six11.sf.constr;

import static org.six11.util.Debug.bug;

import org.six11.sf.SketchBook;
import org.six11.util.pen.Pt;
import org.six11.util.solve.Constraint;
import org.six11.util.solve.DistanceConstraint;
import org.six11.util.solve.NumericValue;

public class SpecificLengthConstraint extends UserConstraint {

  public SpecificLengthConstraint(SketchBook model, Pt p1, Pt p2, NumericValue numericValue) {
    super(model, "Specific Length", new DistanceConstraint(p1, p2, numericValue));
  }

  @Override
  public boolean isValid() {
    boolean ret = getConstraints().size() == 1;
    return ret;
  }

  public void removeInvalid() {
    DistanceConstraint ds = getDistConstraint();
    if (ds != null && !model.hasSegment(ds.a, ds.b)) {
      getConstraints().remove(ds);
    }
  }

  private DistanceConstraint getDistConstraint() {
    DistanceConstraint ret = null;
    if (getConstraints().size() > 0) {
      ret = (DistanceConstraint) getConstraints().toArray(new Constraint[1])[0];
    }
    return ret;
  }

}
