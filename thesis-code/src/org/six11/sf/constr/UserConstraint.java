package org.six11.sf.constr;

import java.awt.Shape;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.six11.sf.Ink;
import org.six11.sf.SketchBook;
import org.six11.util.Debug;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Pt;
import org.six11.util.solve.AngleConstraint;
import org.six11.util.solve.Constraint;
import org.six11.util.solve.DistanceConstraint;
import org.six11.util.solve.LocationConstraint;
import org.six11.util.solve.OrientationConstraint;
import org.six11.util.solve.PointAsLineParamConstraint;
import org.six11.util.solve.PointOnLineConstraint;

import static org.six11.util.Debug.bug;

/**
 * A UserConstraint is a place to retain references to several related constraints, and it usually
 * encapsulates an individual user request such as "keep lines A, B, and C the same length." Such a
 * request involves three Constraint objects, but it was made in a single request. Later, if the
 * user also requests that lines C and D should be the same length, the Constraint object can be
 * added to this one, so it involves A, B, C, and D.
 */
public abstract class UserConstraint {

  protected String name;
  protected Collection<Constraint> constraints;
  protected SketchBook model;
  protected Type type;
  protected Map<Constraint, Shape> visualRegions;
  protected List<Pt> distanceSpots;

  public enum Type {
    Colinear, SameAngle, SameLength, RightAngle, Unknown
  };

  public UserConstraint(SketchBook model, Type type, Constraint... cs) {
    init(model, type, cs);
  }

  public UserConstraint(SketchBook model, Type type, JSONObject json) throws JSONException {
    init(model, type);
    JSONArray constraintIDs = json.getJSONArray("constraints");
    for (int i = 0; i < constraintIDs.length(); i++) {
      int cID = constraintIDs.getInt(i);
      Constraint c = model.getConstraints().getVars().getConstraintWithID(cID);
      if (c != null) {
        constraints.add(c);
      } else {
        bug("Warning: user constraint " + name + " can not find primitive constraint " + cID);
      }
    }
  }

  public Type getType() {
    return type;
  }

  protected void init(SketchBook model, Type type, Constraint... cs) {
    this.model = model;
    this.name = type.toString();
    this.type = type;
    this.constraints = new HashSet<Constraint>();
    for (Constraint c : cs) {
      constraints.add(c);
    }
    this.visualRegions = new HashMap<Constraint, Shape>();
    this.distanceSpots = new ArrayList<Pt>();
  }

  public void setDistanceSpots(Pt... pts) {
    distanceSpots.clear();
    for (Pt pt : pts) {
      distanceSpots.add(pt);
    }
  }

  /**
   * Returns the minimum distance between pt and any of the 'distance spots'.
   */
  public double getDistance(Pt pt) {
    double min = Double.MAX_VALUE;
    if (pt != null) {
      for (Pt other : distanceSpots) {
        min = Math.min(other.distance(pt), min);
      }
    }
    return min;
  }

  public String getName() {
    return name;
  }

  public Collection<Constraint> getConstraints() {
    return constraints;
  }

  public void addConstraint(Constraint c) {
    constraints.add(c);
    model.getConstraints().addConstraint(c);
  }

  public void removeConstraint(Constraint c) {
    constraints.remove(c); // remove from my local list
    model.getConstraints().removeConstraint(c); // and remove from constraint engine's list.
  }

  public void removeAllConstraints() {
    for (Constraint c : constraints) {
      model.getConstraints().removeConstraint(c);
    }
    constraints.clear();
  }

  public boolean involves(Pt pt) {
    boolean ret = false;
    for (Constraint c : constraints) {
      if (c.involves(pt)) {
        ret = true;
        break;
      }
    }
    return ret;
  }

  public String toString() {
    return name;
  }

  public abstract void removeInvalid();

  public abstract boolean isValid();

  //  public abstract void claimOnSplit(Segment original, Set<Segment> parts);

  public JSONObject toJson() throws JSONException {
    JSONObject ret = new JSONObject();
    ret.put("type", getName());
    JSONArray constrArr = new JSONArray();
    for (Constraint c : constraints) {
      constrArr.put(c.getID());
    }
    ret.put("constraints", constrArr);
    return ret;
  }

  public static UserConstraint fromJson(SketchBook model, JSONObject ucObj) throws JSONException {
    Type type = mkType(ucObj.getString("type"));
    UserConstraint ret = null;
    switch (type) {
      case Colinear:
        ret = new ColinearUserConstraint(model, ucObj);
        break;
      case RightAngle:
        ret = new RightAngleUserConstraint(model, ucObj);
        break;
      case SameAngle:
        ret = new SameAngleUserConstraint(model, ucObj);
        break;
      case SameLength:
        ret = new SameLengthUserConstraint(model, ucObj);
        break;
      case Unknown:
        break;

    }
    return ret;
  }

  public static Type mkType(String n) {
    Type ret = Type.Unknown;
    if (n.equals(Type.Colinear.toString())) {
      ret = Type.Colinear;
    }
    if (n.equals(Type.RightAngle.toString())) {
      ret = Type.RightAngle;
    }
    if (n.equals(Type.SameLength.toString())) {
      ret = Type.SameLength;
    }
    if (n.equals(Type.SameAngle.toString())) {
      ret = Type.SameAngle;
    }
    return ret;
  }

}
