package org.six11.sf.constr;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.six11.sf.DrawingBufferLayers;
import org.six11.sf.Ink;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;
import org.six11.util.solve.OrientationConstraint;

public class RightAngleUserConstraint extends UserConstraint {

  public RightAngleUserConstraint(OrientationConstraint rightAngleConstraint) {
    super("RightAngle", rightAngleConstraint);
  }

  public void draw(DrawingBuffer buf, Pt hoverPoint) {
    if (hoverPoint != null) {
      OrientationConstraint c = getConstraints().toArray(new OrientationConstraint[1])[0];
      Pt fulcrum = null;
      Pt left = null;
      Pt right = null;

      if (c.lineA1 == c.lineB1) {
        fulcrum = c.lineA1;
        left = c.lineA2;
        right = c.lineB2;
      } else if (c.lineA1 == c.lineB2) {
        fulcrum = c.lineA1;
        left = c.lineA2;
        right = c.lineB1;
      } else if (c.lineA2 == c.lineB1) {
        fulcrum = c.lineA2;
        left = c.lineA1;
        right = c.lineB2;
      } else if (c.lineA2 == c.lineB2) {
        fulcrum = c.lineA2;
        left = c.lineA1;
        right = c.lineB1;
      }
      if (fulcrum == null || left == null || right == null) {
        // do nothing
      } else {
        Vec leftV = new Vec(fulcrum, left).getUnitVector();
        Vec rightV = new Vec(fulcrum, right).getUnitVector();
        Vec diagonal = Vec.sum(leftV, rightV).getUnitVector();
        double root2 = Math.sqrt(2);
        double braceLen = 16;
        Pt braceCorner = fulcrum.getTranslated(diagonal, root2 * braceLen);
        Pt braceLeft = fulcrum.getTranslated(leftV, braceLen);
        Pt braceRight = fulcrum.getTranslated(rightV, braceLen);
        List<Pt> points = new ArrayList<Pt>();
        points.add(braceLeft);
        points.add(braceCorner);
        points.add(braceRight);
        double alpha = DrawingBufferLayers.getAlpha(fulcrum.distance(hoverPoint), 10, 80, 0.1);
        Color color = new Color(1, 0, 0, (float) alpha);
        DrawingBufferRoutines.lines(buf, points, color, 1.0);
      }
    }
  }

}
