package org.six11.skruifab;

import java.awt.Color;
import java.util.Collection;
import java.util.HashSet;

import org.six11.util.Debug;
import static org.six11.util.Debug.num;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;

import choco.Choco;
import choco.Options;
import choco.cp.model.CPModel;
import choco.cp.solver.CPSolver;
import choco.kernel.common.logging.ChocoLogging;
import choco.kernel.model.Model;
import choco.kernel.model.constraints.Constraint;
import choco.kernel.model.variables.real.RealExpressionVariable;
import choco.kernel.model.variables.real.RealVariable;
import choco.kernel.solver.Solver;
import choco.kernel.solver.variables.real.RealVar;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class SillyChocoTest {

  private Main main;
  Collection<Pt> points;

  public SillyChocoTest(Main main) {
    this.main = main;
    this.points = new HashSet<Pt>();
  }

  public void tmpMakeConstrainedDrawing() {
    Model model = new CPModel();

    Pt a = makeConstrainedPoint(); // make points...
    Pt b = makeConstrainedPoint();
    Pt c = makeConstrainedPoint();
    Pt d = makeConstrainedPoint();
    Pt abMid = makeConstrainedPoint();
    Pt x = makeConstrainedPoint();

    Line ab = new Line(a, b); // that compose co-terminating lines...
    Line ac = new Line(a, c);
    Line bd = new Line(b, d);
    Line ad = new Line(a, d);
    
    // there's a line through abMid but we don't need to keep tabs on the other end of that line,
    // so we can make an anonymous constrained point for the endpoint.
    Line abMidLine = new Line(abMid, makeConstrainedPoint());

    constrainLocation(model, a, 350, 120); // say where two of the three points are, 
    constrainLocation(model, b, 300, 90); // which completely specifies one of the lines...

    constrainLocation(model, abMid, 0.5, ab); // abMid is 0.5 along line ab (midpoint!)

    constrainLength(model, ac, 3, ab); // express length of other lines in terms of known one.
    constrainLength(model, bd, 2.34, ab);
    constrainLength(model, abMidLine, 50); // make this one 50 long so I can see it.

    constrainParallel(model, ac, bd); // ensure parallel lines
    constrainPerpendicular(model, bd, ab); // ensure perpendiculars also
    
    constrainPerpendicular(model, ab, abMidLine);
    constrainIntersection(model, x, abMidLine, ad);

    updateConstraints(model); // solve and update all drawable geometry with solved values

    DrawingBuffer testBuffer = new DrawingBuffer();
    DrawingBufferRoutines.line(testBuffer, ab, Color.BLACK, 1.4);
    DrawingBufferRoutines.line(testBuffer, ac, Color.BLACK, 1.4);
    DrawingBufferRoutines.line(testBuffer, bd, Color.BLACK, 1.4);
    DrawingBufferRoutines.line(testBuffer, abMidLine, Color.LIGHT_GRAY, 1.4);
    DrawingBufferRoutines.dot(testBuffer, a, 6, 1, Color.BLACK, Color.RED);
    DrawingBufferRoutines.dot(testBuffer, b, 3, 0.5, Color.BLACK, Color.GREEN);
    DrawingBufferRoutines.dot(testBuffer, c, 3, 0.5, Color.BLACK, Color.BLUE);
    DrawingBufferRoutines.dot(testBuffer, d, 3, 0.5, Color.BLACK, Color.YELLOW);
    DrawingBufferRoutines.dot(testBuffer, abMid, 3, 0.5, Color.BLACK, Color.MAGENTA);
    DrawingBufferRoutines.dot(testBuffer, x, 3, 0.5, Color.BLACK, Color.MAGENTA);
    DrawingBufferRoutines.text(testBuffer, a.getTranslated(6, 0), "a", Color.BLACK);
    DrawingBufferRoutines.text(testBuffer, b.getTranslated(6, 0), "b", Color.BLACK);
    DrawingBufferRoutines.text(testBuffer, c.getTranslated(6, 0), "c", Color.BLACK);
    DrawingBufferRoutines.text(testBuffer, d.getTranslated(6, 0), "d", Color.BLACK);
    DrawingBufferRoutines.text(testBuffer, x.getTranslated(6, 0), "x", Color.BLACK);
    DrawingBufferRoutines.text(testBuffer, abMid.getTranslated(6, 0), "abMid", Color.BLACK);
    bug("Length of line ab: " + num(ab.getLength()));
    bug("Length of line ac: " + num(ac.getLength()));
    bug("Length of line ad: " + num(bd.getLength()));
    main.getDrawnStuff().addNamedBuffer("2", testBuffer, true);
  }

  public Pt makeConstrainedPoint() {
    Pt ret = new Pt();
    ret.setAttribute("choco:x", new RealVariable("x", -100000, 100000));
    ret.setAttribute("choco:y", new RealVariable("y", -100000, 100000));
    points.add(ret);
    return ret;
  }

  private static class Vec {
    RealExpressionVariable dx, dy;

    Vec(Line line) {
      RealVariable startX = (RealVariable) line.getStart().getAttribute("choco:x");
      RealVariable startY = (RealVariable) line.getStart().getAttribute("choco:y");
      RealVariable endX = (RealVariable) line.getEnd().getAttribute("choco:x");
      RealVariable endY = (RealVariable) line.getEnd().getAttribute("choco:y");
      this.dx = Choco.minus(endX, startX);
      this.dy = Choco.minus(endY, startY);
    }

    RealExpressionVariable getLengthSquared() {
      RealVariable lenSq = new RealVariable("lengthSquared", 0, 1000000);
      lenSq.addOptions(Options.V_NO_DECISION);
      return Choco.plus(Choco.power(dx, 2), Choco.power(dy, 2));
    }

  }

  private Vec getOrMakeVec(Line line) {
    Vec ret = (Vec) line.getAttribute("choco:line");
    if (ret == null) {
      ret = new Vec(line);
      line.setAttribute("choco:line", ret);
    }
    return ret;
  }


  /**
   * line.length = len
   */
  private void constrainLength(Model model, Line line, double len) {
    Vec v = getOrMakeVec(line);
    model.addConstraint(Choco.eq(v.getLengthSquared(), len * len));
  }
  
  /**
   * lineA.length = scaleFactor * lineB.length
   */
  public void constrainLength(Model model, Line lineA, double scaleFactor, Line lineB) {
    Vec vecA = getOrMakeVec(lineA);
    Vec vecB = getOrMakeVec(lineB);
    double multiplier = scaleFactor * scaleFactor;
    model.addConstraint(Choco.eq(vecA.getLengthSquared(), Choco.mult(vecB.getLengthSquared(),
        multiplier)));
  }

  /**
   * This creates two constraints for setting x and y to the given values. Pass the result to the
   * Model's addConstraints() method.
   */
  public void constrainLocation(Model model, Pt pt, double locX, double locY) {
    Constraint xConstr = Choco.eq((RealVariable) pt.getAttribute("choco:x"), locX);
    Constraint yConstr = Choco.eq((RealVariable) pt.getAttribute("choco:y"), locY);
    model.addConstraints(xConstr, yConstr);
  }

  /**
   * Creates a constraint such that pt is at line.start + (line.length * scale) in direction of
   * line. For example if you want the midpoint of the line, just use 0.5 for the scale. You can put
   * a point outside the scope of a line by using values less than zero or greater than one.
   */
  private void constrainLocation(Model model, Pt pt, double scale, Line line) {
    RealVariable aX = (RealVariable) line.getStart().getAttribute("choco:x");
    RealVariable aY = (RealVariable) line.getStart().getAttribute("choco:y");
    RealVariable bX = (RealVariable) line.getEnd().getAttribute("choco:x");
    RealVariable bY = (RealVariable) line.getEnd().getAttribute("choco:y");
    RealVariable ptX = (RealVariable) pt.getAttribute("choco:x");
    RealVariable ptY = (RealVariable) pt.getAttribute("choco:y");
    // ptX = aX + ((Bx - Ax) * scale)
    model.addConstraints(Choco.eq(ptX, Choco.plus(aX, Choco.mult(Choco.minus(bX, aX), scale))));
    model.addConstraints(Choco.eq(ptY, Choco.plus(aY, Choco.mult(Choco.minus(bY, aY), scale))));
  }
  

  public void constrainParallel(Model model, Line lineA, Line lineB) {
    // need to get the vectors for s1 and s2 and constrain them to be parallel. It does this by
    // using the cross product. The cross product of parallel vectors is 0.
    Vec a = getOrMakeVec(lineA);
    Vec b = getOrMakeVec(lineB);
    // a cross b = ax*by - ay*bx = 0
    model.addConstraints(Choco.eq(0, Choco.minus(Choco.mult(a.dx, b.dy), Choco.mult(a.dy, b.dx))));
  }

  public void constrainPerpendicular(Model model, Line lineA, Line lineB) {
    // need to get the vectors for s1 and s2 and constrain them to be perpendicular. The dot 
    // product of perpendicular vectors is zero.
    Vec a = getOrMakeVec(lineA);
    Vec b = getOrMakeVec(lineB);
    // a dot b = (ax * bx) + (ay * by) 
    model.addConstraints(Choco.eq(0, Choco.plus(Choco.mult(a.dx, b.dx), Choco.mult(a.dy, b.dy))));
  }

  public void constrainPointOnLine(Model model, Pt pt, Line line) {
    // Make a Line from line.start to pt. Now you have two lines. If pt is on the given line the
    // two lines are parallel (co-linear, even).
    Line fakeLine = new Line(line.getStart(), pt);
    constrainParallel(model, line, fakeLine);
  }
  
  /**
   * pt = intersection of lines A and B
   */
  public void constrainIntersection(Model model, Pt pt, Line lineA, Line lineB) {
    constrainPointOnLine(model, pt, lineA);
    constrainPointOnLine(model, pt, lineB);
  }

  public void updateConstraints(Model model) {
    ChocoLogging.toSolution();
    Solver solver = new CPSolver();
    solver.read(model);
    solver.solve();
    for (Pt pt : points) {
      updateLocation(pt, solver);
    }
  }

  public static void bug(String what) {
    Debug.out("SillyChocoTest", what);
  }

  public void updateLocation(Pt pt, Solver solver) {
    RealVar vx = solver.getVar((RealVariable) pt.getAttribute("choco:x"));
    RealVar vy = solver.getVar((RealVariable) pt.getAttribute("choco:y"));
    double x = vx.getSup();
    double y = vy.getSup();
    pt.setLocation(x, y);
  }
}
