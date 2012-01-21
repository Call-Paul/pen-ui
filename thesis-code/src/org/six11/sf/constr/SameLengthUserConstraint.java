package org.six11.sf.constr;

import static org.six11.util.Debug.num;

import java.awt.Color;
import java.util.Collection;

import org.six11.sf.DrawingBufferLayers;
import org.six11.sf.Ink;
import org.six11.sf.SketchBook;
import org.six11.sf.Material;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;
import org.six11.util.solve.Constraint;
import org.six11.util.solve.DistanceConstraint;
import org.six11.util.solve.MultisourceNumericValue;

public class SameLengthUserConstraint extends UserConstraint {

  SketchBook model;
  
  public SameLengthUserConstraint(SketchBook model, Constraint... cs) {
    super("Same Length", cs);
    this.model = model;
  }

  public void draw(DrawingBuffer buf, Pt hoverPoint) {
    if (hoverPoint != null) {
      double nearest = Double.MAX_VALUE;
      for (Constraint c : getConstraints()) {
        DistanceConstraint dc = (DistanceConstraint) c;
        Line line = dc.getCurrentSegment();
        double d = Functions.getDistanceBetweenPointAndSegment(hoverPoint, line);
        nearest = Math.min(d, nearest);
      }
      if (nearest < 50) {
        double alpha = DrawingBufferLayers.getAlpha(nearest, 10, 80, 0.1);
        Color color = new Color(1, 0, 0, (float) alpha);
        for (Constraint c : getConstraints()) {
          DistanceConstraint dc = (DistanceConstraint) c;
          Vec segDir = new Vec(dc.getP1(), dc.getP2());
          Pt mid = Functions.getMean(dc.getP1(), dc.getP2());
          if (dc.getValue() instanceof MultisourceNumericValue) {
            DrawingBufferRoutines.acuteHash(buf, mid, segDir, 12, 1.0, color);
          } else {
            Vec segDirNorm = segDir.getNormal();
            Pt textLoc = mid.getTranslated(segDirNorm, 8);
            Material.Units units = model.getMasterUnits();
            double asUnits = Material.fromPixels(units, dc.getValue().getValue());
            DrawingBufferRoutines.text(buf, textLoc, num(asUnits), color);
          }
        }
      }
    }
  }

}
