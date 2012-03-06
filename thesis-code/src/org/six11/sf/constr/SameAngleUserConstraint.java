package org.six11.sf.constr;

import java.awt.Color;
import java.awt.geom.Arc2D;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.six11.sf.Angle;
import org.six11.sf.DrawingBufferLayers;
import org.six11.sf.Material;
import org.six11.sf.SketchBook;
import org.six11.util.gui.shape.ShapeFactory;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;
import org.six11.util.solve.Constraint;
import org.six11.util.solve.AngleConstraint;
import org.six11.util.solve.MultisourceNumericValue;
import org.six11.util.solve.NumericValue;
import org.six11.util.solve.MultisourceNumericValue.Source;
// import org.six11.util.solve.OrientationConstraint;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

public class SameAngleUserConstraint extends UserConstraint {

  private static final double DRAW_RADIUS = 12.0;
  public static final String NAME = "Same Angle";

  public SameAngleUserConstraint(SketchBook model, Constraint... cs) {
    super(model, Type.SameAngle, cs);
  }

  public SameAngleUserConstraint(SketchBook model, JSONObject ucObj) throws JSONException {
    super(model, Type.SameAngle, ucObj);
    if (ucObj.getBoolean("multi")) {
      MultisourceNumericValue mnv = new MultisourceNumericValue();
      for (Constraint c : getConstraints()) {
        AngleConstraint ac = (AngleConstraint) c;
        Source s = mkSource(new Angle(ac.getPtA(), ac.getPtFulcrum(), ac.getPtB()));
        mnv.addValue(s);
        ac.setValue(mnv);
      }
    }
  }

  @Override
  public void removeInvalid() {
    Set<Constraint> doomed = new HashSet<Constraint>();
    for (Constraint c : getConstraints()) {
      AngleConstraint ac = (AngleConstraint) c;
      if (!model.hasSegment(ac.getPtFulcrum(), ac.getPtA())) {
        doomed.add(c);
      }
      if (!model.hasSegment(ac.getPtFulcrum(), ac.getPtB())) {
        doomed.add(c);
      }
    }
    for (Constraint c : doomed) {
      removeConstraint(c);
    }
  }

  @Override
  public boolean isValid() {
    boolean ret = true;
    if (getConstraints().size() == 0) {
      bug("SAUC invalid due to having zero constraints.");
      ret = false;
    } else if (getConstraints().size() == 1) {
      AngleConstraint ac = getConstraints().toArray(new AngleConstraint[1])[0];
      ret = ac.getValue() instanceof NumericValue
          && !(ac.getValue() instanceof MultisourceNumericValue);
      bug("SAUC has one constraint. But is it valid? " + ret);
    } else {
      ret = true;
      bug("SAUC valid because it has " + getConstraints().size() + " constraints.");
    }
    return ret;
  }

  public void addAngle(Angle anglePoints, NumericValue angleValue) {
    NumericValue nv = getValue();
    if (nv != null) {
      bug("Adding/setting angle. Initial nv angle value = " + nv + "("
          + Math.toDegrees(nv.getValue()) + " deg)");
    }
    if (nv != angleValue) {
      if (nv instanceof MultisourceNumericValue) {
        MultisourceNumericValue mnv = (MultisourceNumericValue) nv;
        if (angleValue instanceof MultisourceNumericValue) {
          MultisourceNumericValue mdist = (MultisourceNumericValue) angleValue;
          for (Source src : mdist.getSources()) {
            mnv.addValue(src);
          }
        } else {
          bug("Case 1 can't handle this. nv is " + nv);
        }
      } else {
        bug("Case 2");
        nv = angleValue;
        bug("Setting nv = " + angleValue);
      }
    }
    bug("Now the nv angle value is " + nv + "(" + Math.toDegrees(nv.getValue()) + " deg)");
    AngleConstraint ac = new AngleConstraint(anglePoints.getPtA(), anglePoints.getFulcrum(),
        anglePoints.getPtB(), nv);
    //    OrientationConstraint oc = new OrientationConstraint(anglePoints.getPtA(),
    //        anglePoints.getFulcrum(), anglePoints.getFulcrum(), anglePoints.getPtB(), nv);
    addConstraint(ac);
  }

  /**
   * Same-angle constraints can either be multi-source (where the angle is determined by a bunch of
   * source values) or single-source (where it is fixed to a number).
   * 
   * @return true if the value is a MultisourceNumericValue
   */
  public boolean isMultiSource() {
    return getConstraints().size() > 0 && (getValue() instanceof MultisourceNumericValue);
  }

  public NumericValue getValue() {
    NumericValue ret = null;
    if (getConstraints().size() > 0) {
      AngleConstraint ac = getConstraints().toArray(new AngleConstraint[1])[0];
      ret = ac.getValue();
      //      OrientationConstraint oc = getConstraints().toArray(new OrientationConstraint[1])[0];
      //    ret = oc.getValue(); 
    } else {
      ret = null;
    }
    return ret;
  }

  public void setValue(NumericValue nv) {
    for (Constraint c : getConstraints()) {
      AngleConstraint ac = (AngleConstraint) c;
      ac.setValue(nv);
      //      OrientationConstraint oc = (OrientationConstraint) c;
      //      oc.setValue(nv);
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

  public void draw(DrawingBuffer buf, Pt hoverPoint) {
    if (hoverPoint != null) {
      double nearest = Double.MAX_VALUE;
      for (Constraint c : getConstraints()) {
        AngleConstraint ac = (AngleConstraint) c;
        Pt f = ac.getPtFulcrum();
        double d = hoverPoint.distance(f);
        nearest = Math.min(d, nearest);
      }
      double alpha = DrawingBufferLayers.getAlpha(nearest, 10, 80, 0.1);
      Color color = new Color(1, 0, 0, (float) alpha);
      for (Constraint c : getConstraints()) {
        AngleConstraint ac = (AngleConstraint) c;
        Pt f = ac.getPtFulcrum();
        Vec vecA = new Vec(f, ac.getPtA());
        Vec vecB = new Vec(f, ac.getPtB());
        Pt a = f.getTranslated(vecA, DRAW_RADIUS);
        Pt b = f.getTranslated(vecB, DRAW_RADIUS);
        Vec vecMid = Vec.sum(vecA, vecB);
        Pt m = f.getTranslated(vecMid, DRAW_RADIUS);
        Arc2D arc = ShapeFactory.makeArc(a, m, b);
        DrawingBufferRoutines.drawShape(buf, arc, color, 2.0);
      }
    }
  }

  public static MultisourceNumericValue.Source mkSource(final Angle angle) {
    return new MultisourceNumericValue.Source() {
      public double getValue() {
        return Math.abs(AngleConstraint.measureAngle(angle.getPtA(), angle.getFulcrum(),
            angle.getPtB()));
      }
    };
  }

}
