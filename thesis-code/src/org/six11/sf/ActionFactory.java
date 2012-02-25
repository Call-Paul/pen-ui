package org.six11.sf;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.six11.sf.constr.ColinearUserConstraint;
import org.six11.sf.constr.UserConstraint;
import org.six11.util.data.Lists;
import org.six11.util.pen.Pt;
import org.six11.util.solve.PointOnLineConstraint;

import static org.six11.util.Debug.bug;

public class ActionFactory {

  private SketchBook model;

  public ActionFactory(SketchBook model) {
    this.model = model;
  }

  public SafeAction addInk(final Ink ink) {
    SafeAction ret = new SafeAction("Add Ink") {
      public void forward() {
        model.addInk(ink);
      }

      public void backward() {
        model.removeInk(ink);
      }
    };
    return ret;
  }

  public SafeAction addSegments(final Collection<Segment> segs) {
    return new SafeAction("Add Segments") {
      public void forward() {
        add(segs);
      }

      public void backward() {
        axe(segs);
      }
    };
  }

  private void axe(Collection<Segment> segs) {
    for (Segment seg : segs) {
      if (!seg.isSingular()) {
        model.getConstraints().removePoint(seg.getP1());//addPoint(model.nextPointName(), seg.getP1());
        model.getConstraints().removePoint(seg.getP2());//addPoint(model.nextPointName(), seg.getP2());
      }
    }
    for (Segment seg : segs) {
      model.removeGeometry(seg);
    }
  }

  private void add(Collection<Segment> segs) {
    for (Segment seg : segs) {
      if (!seg.isSingular()) {
        if (!SketchBook.hasName(seg.getP1())) {
          model.getConstraints().addPoint(model.nextPointName(), seg.getP1());
        } else {
          model.getConstraints().addPoint(seg.getP1());
        }
        if (!SketchBook.hasName(seg.getP2())) {
          model.getConstraints().addPoint(model.nextPointName(), seg.getP2());
        } else {
          model.getConstraints().addPoint(seg.getP2());
        }
      }
    }
    for (Segment seg : segs) {
      model.addGeometry(seg);
    }
  }

  public SafeAction removeSegments(final Collection<Segment> segs) {
    return new SafeAction("Remove Segments") {
      public void forward() {
        axe(segs);
      }

      public void backward() {
        add(segs);
      }
    };
  }

  public SafeAction split(final Segment oldSeg, final Set<Segment> newSegs) {

    SafeAction ret = new SafeAction("Split Segment") {
      public void forward() {
        //        bug("split action!");
        //        bug("remove old seg: " + oldSeg.typeIdPtStr());
        //        bug("new segs:");
        //        for (Segment ns : newSegs) {
        //          bug("-- " + ns.typeIdPtStr());
        //        }
        Set<UserConstraint> allInvolved = model.findUserConstraints(oldSeg, true);
        // for now only look for ColinearUserConstraints. This is 'wrong' because it ignores other
        // constraints like RightAngle and obliges the user to re-make them.
        Set<UserConstraint> colinears = new HashSet<UserConstraint>();
        for (UserConstraint uc : allInvolved) {
          if (uc instanceof ColinearUserConstraint) {
            colinears.add(uc);
          }
        }
        if (colinears.size() > 1) {
          bug("Warning: segment " + oldSeg.typeIdPtStr()
              + " is involved in two different colinear user constraints. Bad.");
        }
        Pt splitPt = null;
        Set<Pt> olds = Lists.makeSet(oldSeg.getP1(), oldSeg.getP2());
        for (Segment news : newSegs) {
          if (!olds.contains(news.getP1())) {
            splitPt = news.getP1();
            break;
          } else if (!olds.contains(news.getP2())) {
            splitPt = news.getP2();
            break;
          }
        }
        
        if (splitPt == null) {
          bug("Warning: could not identify split point!");
        } else {
          bug("Found split point. Adding it to the colinear constraint.");
          
        }
        ColinearUserConstraint colinear = null;
        if (colinears.size() > 0) {
          // found a colinear constraint. get the split point and add another PointOnLine to the colinear constraint.
          colinear = (ColinearUserConstraint) Lists.getOne(colinears);
          bug("Found a colinear constraint for " + oldSeg.typeIdStr());
          colinear.addPoint(splitPt);
        } else {
          // did not find an existing colinear constraint. so make one.
          bug("Didn't find a colinear constraint, so I'm going to make one.");
          colinear = new ColinearUserConstraint(model, oldSeg.getP1(), oldSeg.getP2(), splitPt);
          
        }
        axe(Collections.singleton(oldSeg)); // will remove all user constraints related to it.
        model.addUserConstraint(colinear); // create (or reinstate) the colinear constraint
        add(newSegs);
      }

      public void backward() {
        axe(newSegs);
        add(Collections.singleton(oldSeg));
      }
    };
    return ret;

  }
}
