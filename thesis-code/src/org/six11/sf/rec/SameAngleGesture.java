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
import org.six11.util.data.Lists;
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

  public Certainty checkContext(RecognizedItem item, Collection<RecognizerPrimitive> in) {
    bug("entered checkContext");
    Certainty ret = Certainty.No;

    RecognizerPrimitive line1 = item.getSubshape("line1");
    RecognizerPrimitive line2 = item.getSubshape("line2");
    Set<RecognizerPrimitive> hashes = Lists.makeSet(line1, line2);

    Set<Segment> allSegs = model.getGeometry();
    allSegs = SegmentFilter.makeCohortFilter(in).filter(allSegs); // remove current input from consideration

    Segment[] pair1 = new Segment[0];
    Segment[] pair2 = new Segment[0];
    // loop through all the hashes and try to identify two corners whose angle we will constrain.
    for (RecognizerPrimitive line : hashes) {
      if (line.getType() == RecognizerPrimitive.Type.Line) {
        Pt hotspot = line.getMid();
        bug("Found a line. looking for segments whose endpoints are near the hotspot "
            + num(hotspot));
        Set<Segment> segs = SegmentFilter.makeEndpointRadiusFilter(hotspot, 30).filter(allSegs);
        bug("Found " + segs.size() + " such segments");
        // next see if segs has two (and only two) that share an endpoint ('corner') 
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
      Set<Segment> numberCheck = Lists.makeSet(pair1[0], pair1[1], pair2[0], pair2[1]);
      if (numberCheck.size() > 2) {
        // we're good to go.
        item.addTarget(SameAngleGesture.TARGET_A1, pair1[0]);
        item.addTarget(SameAngleGesture.TARGET_A2, pair1[1]);
        item.addTarget(SameAngleGesture.TARGET_B1, pair2[0]);
        item.addTarget(SameAngleGesture.TARGET_B2, pair2[1]);
        ret = Certainty.Yes;
      }
    }
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
      Set<UserConstraint> ucs = model.getUserConstraints(results);
      merge(ucs); // 'uc' is left null, should have no ill effects. famous last words
    }
    for (Ink eenk : item.getInk()) {
      model.removeRelated(eenk);
    }
    bug("adding user constraint!");
    model.addUserConstraint(uc);
  }

  private void merge(Set<UserConstraint> ucs) {
    // found a few user constraints. merge them into one.
    boolean ok = true;
    for (UserConstraint uc : ucs) {
      if (!(uc instanceof SameAngleUserConstraint)) {
        ok = false;
        bug("Error. Expected user constraint of type SameAngleUserConstraint but found "
            + uc.getClass());
        break;
      }
    }
    if (ok) {
      // determine if there is a fixed value angle here, and set it if there is one.
      Set<SameAngleUserConstraint> saucs = new HashSet<SameAngleUserConstraint>();
      SameAngleUserConstraint fixedSrc = null;
      for (UserConstraint uc : ucs) {
        SameAngleUserConstraint sluc = (SameAngleUserConstraint) uc;
        saucs.add(sluc);
        if (!sluc.isMultiSource()) {
          if (fixedSrc == null) {
            fixedSrc = sluc;
          } else {
            bug("When merging, I found two different fixed length user constraints. Don't know what to do, so I don't do anything.");
            ok = false;
            break;
          }
        }
      }

      if (ok) {
        // two possibilities: all are multisource, or exactly one is fixed.
        if (fixedSrc == null) {
          // handle the 'all are multisource' first
          bug("All merged user constraints are multisource.");
          SameAngleUserConstraint main = saucs.toArray(new SameAngleUserConstraint[1])[0];
          saucs.remove(main);
          for (SameAngleUserConstraint sluc : saucs) {
            Set<Constraint> replace = new HashSet<Constraint>();
            replace.addAll(sluc.getConstraints());
            model.removeUserConstraint(sluc);
            for (Constraint c : replace) {
              AngleConstraint ac = (AngleConstraint) c;
              main.addAngle(new Angle(ac.getPtA(), ac.getPtFulcrum(), ac.getPtB()), ac.getValue());
            }
          }
        } else {
          // exactly one is fixed.
          saucs.remove(fixedSrc);
          for (SameAngleUserConstraint sauc : saucs) {
            Set<Constraint> replace = new HashSet<Constraint>();
            replace.addAll(sauc.getConstraints());
            model.removeUserConstraint(sauc);
            for (Constraint c : replace) {
              AngleConstraint ac = (AngleConstraint) c;
              fixedSrc.addAngle(new Angle(ac.getPtA(), ac.getPtFulcrum(), ac.getPtB()),
                  fixedSrc.getValue());
            }
          }
        }
      }
    }
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

  public static Collection<RecognizedItem> resolveConflictRightAngleGesture(RecognizedItem itemA,
      RecognizedItem itemB) {
    Collection<RecognizedItem> doomed = new HashSet<RecognizedItem>();
    if (itemA.getTemplate().getName().equals(RightAngleBrace.NAME)) {
      doomed.add(itemB);
    } else {
      doomed.add(itemA);
    }
    return doomed;
  }

}
