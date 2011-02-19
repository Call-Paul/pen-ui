package org.six11.skruifab;

import java.awt.Color;
import java.util.Collection;
import java.util.HashSet;

import org.six11.skruifab.spud.CCircle;
import org.six11.skruifab.spud.CDistance;
import org.six11.skruifab.spud.CLine;
import org.six11.skruifab.spud.CPerpendicularLines;
import org.six11.skruifab.spud.CPoint;
import org.six11.skruifab.spud.CPointAlongSegment;
import org.six11.skruifab.spud.CPointOnLine;
import org.six11.skruifab.spud.CPointSet;
import org.six11.skruifab.spud.CRect;
import org.six11.skruifab.spud.ConstraintModel;
import org.six11.skruifab.spud.Geom;
import org.six11.util.Debug;
import org.six11.util.pen.CircleArc;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class SillySpudTest {

  private Main main;
  Collection<Pt> points;

  public SillySpudTest(Main main) {
    this.main = main;
    this.points = new HashSet<Pt>();
  }

  /**
   * This manually creates some points, lines, and the constraints that define their final
   * locations. It is just a test.
   */
  public void tmpMakeConstrainedDrawing() {

    ConstraintModel model = new ConstraintModel();

    CPoint a = new CPoint(new Pt(350, 120));
    a.setName("a");
    CPoint b = new CPoint(new Pt(300, 90));
    b.setName("b");
    CPoint abMid = new CPoint();
    abMid.setName("abMid");
    CPoint c = new CPoint();
    c.setName("c");
    CLine ab = new CLine();
    ab.setName("ab");
    CLine abMidC = new CLine();
    abMidC.setName("abMidC");
    model.addConstraint(new CPointOnLine(ab, a));
    model.addConstraint(new CPointOnLine(ab, b));
    model.addConstraint(new CPointAlongSegment(a, b, abMid, 0.5));
    model.addConstraint(new CPointOnLine(abMidC, abMid));
    model.addConstraint(new CPointOnLine(abMidC, c));
    model.addConstraint(new CDistance(abMid, c, 50.0));
    model.addConstraint(new CPerpendicularLines(ab, abMidC));
    model.solve();

    DrawingBuffer testBuffer = new DrawingBuffer();
    DrawingBufferRoutines.line(testBuffer, new Line(a.getPt(), b.getPt()), Color.BLACK, 1.4);
    DrawingBufferRoutines.dot(testBuffer, a.getPt(), 3, 0.5, Color.BLACK, Color.RED);
    DrawingBufferRoutines.dot(testBuffer, b.getPt(), 3, 0.5, Color.BLACK, Color.GREEN);
    DrawingBufferRoutines.dot(testBuffer, abMid.getPt(), 3, 0.5, Color.BLACK, Color.MAGENTA);
    if (c.isSolved()) {
      DrawingBufferRoutines.dot(testBuffer, c.getPt(), 3, 0.5, Color.BLACK, Color.MAGENTA);
    } else {
      Geom sln = c.getSolutionSpace();
      if (sln instanceof CPointSet) {
        bug("point C could be any of the yellow points (solution space is pointset)");
        for (Pt pt : ((CPointSet) sln).getPoints()) {
          DrawingBufferRoutines.dot(testBuffer, pt, 3, 0.5, Color.BLACK, Color.YELLOW);
        }
      } else if (sln instanceof CCircle) {
        bug("point C is apparently somewhere on the yellow circle");
        CircleArc arc = ((CCircle) sln).getCircle();
        DrawingBufferRoutines.dot(testBuffer, arc.getCenter(), arc.getRadius(), 1, Color.YELLOW, null);
      }
    }
    if (abMidC.isSolved()) {
      bug("abMidC is solved. should paint it.");
      CRect screen = new CRect(main.getDrawingSurface().getVisibleRect());
      Geom intersections = screen.intersect(abMidC);
      if (intersections instanceof CPointSet) {
        CPointSet points = (CPointSet) intersections;
        Pt[] ix = points.getPoints();
        if (ix.length == 2) {
          Line abLineToDraw = new Line(ix[0], ix[1]);
          DrawingBufferRoutines.line(testBuffer, abLineToDraw, Color.YELLOW, 1);
        } else {
          bug("Found " + ix.length + " intersection points but expected 2.");
        }
      } else {
        bug ("Intersection of line and rect unexpectedly gave: " + intersections);
      }
    } else {
      bug("abMidC is not solved. :(");
      
//      abMidC.intersect(new CRect());
    }
    main.getDrawnStuff().addNamedBuffer("2", testBuffer, true);
  }

  private static void bug(String what) {
    Debug.out("SillySpudTest", what);
  }

}
