package org.six11.sf.constr;

import java.util.HashSet;
import java.util.Set;

import org.six11.sf.SketchBook;
import org.six11.util.data.Lists;
import org.six11.util.pen.Pt;
import org.six11.util.solve.Constraint;
import org.six11.util.solve.PointOnLineConstraint;

import static org.six11.util.Debug.bug;

public class ColinearUserConstraint extends UserConstraint {

  public ColinearUserConstraint(SketchBook model, Pt a, Pt b, Pt mid) {
    super(model, "Colinear", new PointOnLineConstraint(a, b, mid));
  }

  @Override
  public void removeInvalid() {
    bug("implement removeInvalid");
  }

  @Override
  public boolean isValid() {
    bug("implement isValid");
    return true;
  }

  public void addPoint(Pt pt) {
    Set<Pt> currentPts = new HashSet<Pt>();
    currentPts.add(pt); // add our new point into the mix
    // now collect a list of all points we have
    for (Constraint c : getConstraints()) {
      PointOnLineConstraint pol = (PointOnLineConstraint) c;
      currentPts.addAll(Lists.makeSet(pol.getRelatedPoints()));
    }
    // find the points that are farthest away from each other (the antipodes)
    Pt[] points = currentPts.toArray(new Pt[0]);
    Pt bestA = null;
    Pt bestB = null;
    double bestDist = 0;
    for (int i = 0; i < points.length; i++) {
      Pt a = points[i];
      for (int j = i + 1; j < points.length; j++) {
        Pt b = points[j];
        double thisDist = a.distance(b);
        if (thisDist > bestDist) {
          bestDist = thisDist;
          bestA = a;
          bestB = b;
        }
      }
    }
    bug("Antipodes: " + SketchBook.n(bestA) + ", " + SketchBook.n(bestB));

    // now start fresh. remove all constraints and create new ones using bestA and bestB as the antipodes.
    removeAllConstraints();
    currentPts.remove(bestA);
    currentPts.remove(bestB);
    for (Pt current : currentPts) {
      PointOnLineConstraint pol = new PointOnLineConstraint(bestA, current, bestB);
      addConstraint(pol);
    }
  }

}
