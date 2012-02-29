package org.six11.sf.constr;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;
import org.six11.sf.DrawingBufferLayers;
import org.six11.sf.SketchBook;
import org.six11.util.data.Lists;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;
import org.six11.util.solve.AngleConstraint;
import org.six11.util.solve.Constraint;
import org.six11.util.solve.OrientationConstraint;
import org.six11.util.solve.PointOnLineConstraint;

import static org.six11.util.Debug.bug;

public class ColinearUserConstraint extends UserConstraint {

  public static final String NAME = "Colinear";

  public ColinearUserConstraint(SketchBook model, Pt a, Pt b, Pt mid) {
    super(model, NAME, new PointOnLineConstraint(a, b, mid));
  }

  public ColinearUserConstraint(SketchBook model, JSONObject ucObj) throws JSONException {
    super(model, NAME, ucObj);
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

  public void draw(DrawingBuffer buf, Pt hoverPoint) {
    if (hoverPoint != null) {
      Set<Pt> pts = getConstrainedPoints();
      Pt[] anti = getAntipodes(pts);
      Vec dir = new Vec(anti[0], anti[1]).getVectorOfMagnitude(30);
      Vec flip = dir.getFlip();
      Pt a = anti[0].getTranslated(flip);
      Pt b = anti[1].getTranslated(dir);
      Line line = new Line(a, b);
      double dist = Functions.getDistanceBetweenPointAndLine(hoverPoint, line);
      double alpha = DrawingBufferLayers.getAlpha(dist, 5, 40, 0.1);
      Color color = new Color(1, 0, 0, (float) alpha);
      DrawingBufferRoutines.line(buf, a, b, color, 1.4);
    }
  }

  public Set<Pt> getConstrainedPoints() {
    Set<Pt> ret = new HashSet<Pt>();
    for (Constraint c : getConstraints()) {
      PointOnLineConstraint pol = (PointOnLineConstraint) c;
      ret.addAll(Lists.makeSet(pol.getRelatedPoints()));
    }
    return ret;
  }

  public void addPoint(Pt pt) {
    Set<Pt> currentPts = getConstrainedPoints(); //new HashSet<Pt>();
    currentPts.add(pt); // add our new point into the mix
    // now collect a list of all points we have
    //    for (Constraint c : getConstraints()) {
    //      PointOnLineConstraint pol = (PointOnLineConstraint) c;
    //      currentPts.addAll(Lists.makeSet(pol.getRelatedPoints()));
    //    }
    // find the points that are farthest away from each other (the antipodes)
    Pt[] anti = getAntipodes(currentPts);
    bug("Antipodes: " + SketchBook.n(anti[0]) + ", " + SketchBook.n(anti[1]));

    // now start fresh. remove all constraints and create new ones using bestA and bestB as the antipodes.
    removeAllConstraints();
    currentPts.remove(anti[0]);
    currentPts.remove(anti[1]);
    for (Pt current : currentPts) {
      PointOnLineConstraint pol = new PointOnLineConstraint(anti[0], current, anti[1]);
      addConstraint(pol);
    }
  }

  public Pt[] getAntipodes(Set<Pt> sourcePoints) {
    Pt[] points = sourcePoints.toArray(new Pt[0]);
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
    Pt[] best = new Pt[] {
        bestA, bestB
    };
    return best;
  }

}
