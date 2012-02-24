package org.six11.sf.rec;

import java.awt.Color;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import org.six11.sf.Angle;
import org.six11.sf.DrawingBufferLayers;
import org.six11.sf.Ink;
import org.six11.sf.Material;
import org.six11.sf.Segment;
import org.six11.sf.SegmentFilter;
import org.six11.sf.SketchBook;
// import org.six11.sf.constr.SameAngleUserConstraint;
import org.six11.sf.constr.SameAngleUserConstraint;
import org.six11.sf.constr.UserConstraint;
import org.six11.sf.rec.RecognizerPrimitive.Certainty;
import org.six11.util.math.Interval;
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
import org.six11.util.solve.VariableBank;
import org.six11.util.solve.VariableBank.ConstraintFilter;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

public class SameAngleGesture extends RecognizedItemTemplate {

  private static final String TARGET_A1 = "targetA1";
  private static final String TARGET_A2 = "targetA2";
  private static final String TARGET_B1 = "targetB1";
  private static final String TARGET_B2 = "targetB2";
  public static final String NAME = "SameAngleGesture";

  public SameAngleGesture(SketchBook model) {
    super(model, NAME);
    addPrimitive("line1", RecognizerPrimitive.Type.Line);
    addPrimitive("line2", RecognizerPrimitive.Type.Line);
    Interval yesInterval = new Interval(0, 40);
    Interval maybeInterval = new Interval(0, 60);
    addConstraint(new LineLengthConstraint("c1", yesInterval, maybeInterval, "line1"));
    addConstraint(new LineLengthConstraint("c2", yesInterval, maybeInterval, "line2"));
    addConstraint(new EqualLength("c3", "line1", "line2"));
  }

  @Override
  public RecognizedItem makeItem(Stack<String> slots, Stack<RecognizerPrimitive> prims) {
    RecognizedItem item = new RecognizedItem(this, slots, prims);
    // nothing interesting required.
    return item;
  }

  @Override
  public Certainty checkContext(RecognizedItem item, Collection<RecognizerPrimitive> in) {
    bug("entered checkContext");
    Certainty ret = Certainty.No;
    Set<Segment> allSegs = model.getGeometry();
    Set<Segment> nearSegs = SegmentFilter.makeCohortFilter(in).filter(allSegs);
    Segment[] pair1 = new Segment[0];
    Segment[] pair2 = new Segment[0];
    for (RecognizerPrimitive line : in) {
      if (line.getType() == RecognizerPrimitive.Type.Line) {
        Pt hotspot = line.getMid();
        Set<Segment> segs = SegmentFilter.makeEndpointRadiusFilter(hotspot, 30).filter(nearSegs);
        // TODO: next see if segs has two (and only two) that share an endpoint ('corner') 
        // near the hotspot if we find two distinct corners, we are in business.
        if (segs.size() == 2) {
          if (pair1.length == 0) {
            pair1 = segs.toArray(pair1);
          } else if (pair2.length == 0) {
            pair2 = segs.toArray(pair2);
          } else {
            bug("found pair, uh, more than 2. this might be a bug.");
          }
        }
      }
    }
    // if both pairs are found...
    if (pair1.length == 2 && pair2.length == 2) {
      // make sure there are more than two segments involved, 
      // in case the user double-hashed the same angle
      Set<Segment> numberCheck = new HashSet<Segment>();
      numberCheck.add(pair1[0]);
      numberCheck.add(pair1[1]);
      numberCheck.add(pair2[0]);
      numberCheck.add(pair2[1]);
      if (numberCheck.size() > 2) {
        // we're good to go.
        bug("Good to go! constrain angles formed by " + pair1[0].getId() + "/" + pair1[1].getId()
            + " and " + pair2[0].getId() + "/" + pair2[1].getId() + ".");
        item.addTarget(SameAngleGesture.TARGET_A1, pair1[0]);
        item.addTarget(SameAngleGesture.TARGET_A2, pair1[1]);
        item.addTarget(SameAngleGesture.TARGET_B1, pair2[0]);
        item.addTarget(SameAngleGesture.TARGET_B2, pair2[1]);
        ret = Certainty.Yes;
      }
    }
    //    RecognizerPrimitive line1 = item.getSubshape("line1");
    //    RecognizerPrimitive line2 = item.getSubshape("line2");

    //    // 1) use a filter that only selects lines that are the sole intersecter of line1/line2.
    //    // (so if line1 intersects more than one thing, nothing passes. it must intersect exactly one thing.)
    //    Set<Segment> segs1 = SegmentFilter.makeIntersectFilter(line1).filter(allSegs);
    //    Set<Segment> segs2 = SegmentFilter.makeIntersectFilter(line2).filter(allSegs);
    //    if (segs1.size() == 1 && segs2.size() == 1) {
    //      // 2) use a filter that only selects lines whose midpoint is near line1 or line2's midpoint
    //      segs1 = SegmentFilter.makeMidpointFilter(line1, 0.3).filter(segs1);
    //      segs2 = SegmentFilter.makeMidpointFilter(line2, 0.3).filter(segs2);
    //    }
    //    if (segs1.size() == 1 && segs2.size() == 1) {
    //      Segment[] seg1 = segs1.toArray(new Segment[1]);
    //      Segment[] seg2 = segs2.toArray(new Segment[1]);
    //      if (seg1[0] != seg2[0]) {
    //        item.addTarget(SameAngleGesture.TARGET_A, seg1[0]);
    //        item.addTarget(SameAngleGesture.TARGET_B, seg2[0]);
    //        ret = Certainty.Yes;
    //      } else {
    //        bug("Not going to make a line same length as itself, dawg");
    //      }
    //    }
    bug("exited checkContext");
    return ret;
  }

  private Angle getFulcrum(Segment[] pair) {
    Angle ret = null;
    if (pair.length == 2) {
      if (pair[0].getP1() == pair[1].getP1()) {
        ret = new Angle(pair[0].getP2(), pair[0].getP1(), pair[1].getP2());
      } else if (pair[0].getP1() == pair[1].getP2()) {
        ret = new Angle(pair[0].getP2(), pair[0].getP1(), pair[1].getP1());
      } else if (pair[0].getP2() == pair[1].getP1()) {
        ret = new Angle(pair[0].getP1(), pair[0].getP2(), pair[1].getP2());
      } else if (pair[0].getP2() == pair[1].getP2()) {
        ret = new Angle(pair[0].getP1(), pair[0].getP2(), pair[1].getP1());
      }
    }
    return ret;
  }

  @Override
  public void create(RecognizedItem item, SketchBook model) {
    Segment[] pairA = new Segment[2];
    Segment[] pairB = new Segment[2];
    pairA[0] = item.getSegmentTarget(TARGET_A1);
    pairA[1] = item.getSegmentTarget(TARGET_A2);
    Angle angleA = getFulcrum(pairA);
    pairB[0] = item.getSegmentTarget(TARGET_B1);
    pairB[1] = item.getSegmentTarget(TARGET_B2);
    Angle angleB = getFulcrum(pairB);
    if (angleA == null || angleB == null) {
      bug("Unable to form angle. Fail.");
      return;
    } else {
      bug("Made angle for both pairs.");
      bug("Angle A: " + angleA);
      bug("Angle B: " + angleB);
    }

    // see if either there is an angle constraint for pairA or pairB already.
    Set<ConstraintFilter> filters = new HashSet<ConstraintFilter>();
    filters.add(VariableBank.getTypeFilter(AngleConstraint.class));
    filters.add(ConstraintFilters.getInvolvesFilter(angleA.getPointArray(), //
        angleB.getPointArray()));
    // 'results' will hold any angle constraints already present
    Set<Constraint> results = model.getConstraints().getVars().searchConstraints(filters);
    Set<Constraint> addUs = new HashSet<Constraint>();
    UserConstraint uc = null; // populate this and add it to the model
    bug("Number of current constraints involving those angles: " + results.size());
    if (results.size() == 0) {
      // No existing angle constraints. so make one of your own and assign it to uc.
      MultisourceNumericValue angle = new MultisourceNumericValue(mkSource(angleA),
          mkSource(angleB));
      SameAngleUserConstraint sauc = new SameAngleUserConstraint(model);
      sauc.addAngle(angleA, angle);
      sauc.addAngle(angleB, angle);
      uc = sauc;
    } else if (results.size() == 1) {
      bug("************** Results.size: " + results.size());
      bug("Adding to the existing angle constraint.");
      // use existing numeric value
      AngleConstraint angleConst = (AngleConstraint) results.toArray(new Constraint[1])[0];
      uc = model.getUserConstraint(angleConst);
      NumericValue numeric = angleConst.getValue();
      if (numeric instanceof MultisourceNumericValue) {
        bug("It is a vague angle constraint.");
        MultisourceNumericValue val = (MultisourceNumericValue) numeric;
        if (angleConst.involvesAll(pairA[0].getP1(), pairA[0].getP2(), pairA[1].getP1(),
            pairA[1].getP2())) {
          // pairA already constrained. incorporate pairB's angle and give it constraint
          Angle angle = getFulcrum(pairB);
          val.addValue(mkSource(angle));
          AngleConstraint ac = new AngleConstraint(angle.getPtA(), angle.getFulcrum(),
              angle.getPtB(), val);
          addUs.add(ac);
        } else {
          // pairB already constrained. incorporate pairA's angle and give it constraint
          Angle angle = getFulcrum(pairA);
          val.addValue(mkSource(angle));
          AngleConstraint ac = new AngleConstraint(angle.getPtA(), angle.getFulcrum(),
              angle.getPtB(), val);
          addUs.add(ac);
        }
      } else {
        bug("The existing angle constraint is a constant numeric. Just copy the numeric value into the new one.");
        if (angleConst.involvesAll(pairA[0].getP1(), pairA[0].getP2(), pairA[1].getP1(),
            pairA[1].getP2())) {
          Angle angle = getFulcrum(pairB);
          addUs.add(new AngleConstraint(angle.getPtA(), angle.getFulcrum(), //
              angle.getPtB(), numeric));
        } else {
          Angle angle = getFulcrum(pairA);
          addUs.add(new AngleConstraint(angle.getPtA(), angle.getFulcrum(), //
              angle.getPtB(), numeric));
        }
      }
      for (Constraint addMe : addUs) {
        uc.addConstraint(addMe);
      }
    } else if (results.size() > 1) {
      bug("************** Results.size: " + results.size());
      //      Set<UserConstraint> ucs = model.getUserConstraints(results);
      //      merge(ucs); // 'uc' is left null, should have no ill effects. famous last words
    }
    for (Ink eenk : item.getInk()) {
      model.removeRelated(eenk);
    }
    bug("adding user constraint!");
    model.addUserConstraint(uc);
  }

  private void merge(Set<UserConstraint> ucs) {
    //    // found a few user constraints. merge them into one.
    //    boolean ok = true;
    //    for (UserConstraint uc : ucs) {
    //      if (!(uc instanceof SameLengthUserConstraint)) {
    //        ok = false;
    //        bug("Error. Expected user constraint of type SameLengthUserConstraint but found "
    //            + uc.getClass());
    //        break;
    //      }
    //    }
    //    if (ok) {
    //      Set<SameLengthUserConstraint> slucs = new HashSet<SameLengthUserConstraint>();
    //      SameLengthUserConstraint fixedSrc = null;
    //      for (UserConstraint uc : ucs) {
    //        SameLengthUserConstraint sluc = (SameLengthUserConstraint) uc;
    //        slucs.add(sluc);
    //        if (!sluc.isMultiSource()) {
    //          if (fixedSrc == null) {
    //            fixedSrc = sluc;
    //          } else {
    //            bug("When merging, I found two different fixed length user constraints. Don't know what to do, so I don't do anything.");
    //            ok = false;
    //            break;
    //          }
    //        }
    //      }
    //      if (ok) {
    //        // two possibilities: all are multisource, or exactly one is fixed.
    //        if (fixedSrc == null) {
    //          // handle the 'all are multisource' first
    //          bug("All merged user constraints are multisource.");
    //          SameLengthUserConstraint main = slucs.toArray(new SameLengthUserConstraint[1])[0];
    //          slucs.remove(main);
    //          for (SameLengthUserConstraint sluc : slucs) {
    //            Set<Constraint> replace = new HashSet<Constraint>();
    //            replace.addAll(sluc.getConstraints());
    //            model.removeUserConstraint(sluc);
    //            for (Constraint c : replace) {
    //              DistanceConstraint dc = (DistanceConstraint) c;
    //              main.addDist(dc.a, dc.b, dc.getValue());
    //            }
    //          }
    //        } else {
    //          // exactly one is fixed.
    //          slucs.remove(fixedSrc);
    //          for (SameLengthUserConstraint sluc : slucs) {
    //            Set<Constraint> replace = new HashSet<Constraint>();
    //            replace.addAll(sluc.getConstraints());
    //            model.removeUserConstraint(sluc);
    //            for (Constraint c : replace) {
    //              DistanceConstraint dc = (DistanceConstraint) c;
    //              fixedSrc.addDist(dc.a, dc.b, fixedSrc.getValue());
    //            }            
    //          }
    //        }
    //      }
    //    }
    //    //    UserConstraint mainVague = ucs.toArray(new UserConstraint[1])[0];
    //    //    if (mainVague instanceof SameLengthUserConstraint) {
    //    //      SameLengthUserConstraint main = (SameLengthUserConstraint) mainVague;
    //    //      ucs.remove(main);
    //    //      for (UserConstraint other : ucs) {
    //    //        Set<Constraint> replace = new HashSet<Constraint>();
    //    //        replace.addAll(other.getConstraints());
    //    //        model.removeUserConstraint(other);
    //    //        for (Constraint c : replace) {
    //    //          DistanceConstraint dc = (DistanceConstraint) c;
    //    //          main.addDist(dc.a, dc.b, dc.getValue());
    //    //        }
    //    //      }
    //    //    } else {
    //    //      bug("this user constraint is the wrong type! expected SameLengthUserConstraint but got "
    //    //          + mainVague.getClass());
    //    //    }
  }

  public static UserConstraint makeUserConstraint(final SketchBook model, RecognizedItem item,
      Set<Constraint> addUs) {
    //    UserConstraint ret = new SameLengthUserConstraint(model, addUs.toArray(new Constraint[0]));
    //    return ret;
    return null;
  }

  private MultisourceNumericValue.Source mkSource(final Angle angle) {
    return new MultisourceNumericValue.Source() {
      public double getValue() {
        return Math.abs(AngleConstraint.measureAngle(angle.getPtA(), angle.getFulcrum(),
            angle.getPtB()));
      }
    };
  }

  //  public static Collection<RecognizedItem> resolveConflictSameAngleGesture(
  //      RecognizedItem itemA, RecognizedItem itemB) {
  //    bug("OK, I have these two same-angle things to resolve:");
  //    bug("  1: " + itemA);
  //    bug("  2: " + itemB);
  //    return new HashSet<RecognizedItem>();
  //  }
}
