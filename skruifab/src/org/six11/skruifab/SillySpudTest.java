package org.six11.skruifab;

import java.awt.Color;
import java.util.Collection;
import java.util.HashSet;

import org.six11.util.Debug;
import static org.six11.util.Debug.num;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;

import org.six11.skruifab.spud.*;

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
    CPoint b = new CPoint(new Pt(300, 90));
    CPoint c = new CPoint();
    CPoint d = new CPoint();
    CPoint abMid = new CPoint();
    CPoint x = new CPoint();
    
    CLine ab = new CLine(); // that compose co-terminating lines...
    model.addConstraint(new CPointOnLine(ab, a));
    model.addConstraint(new CPointOnLine(ab, b));
    CLine ac = new CLine();
    model.addConstraint(new CPointOnLine(ac, a));
    model.addConstraint(new CPointOnLine(ac, c));
    CLine bd = new CLine();
    model.addConstraint(new CPointOnLine(bd, b));
    model.addConstraint(new CPointOnLine(bd, d));
    CLine ad = new CLine();
    model.addConstraint(new CPointOnLine(ad, a));
    model.addConstraint(new CPointOnLine(ad, d));

    CLine abMidLine = new CLine();
    model.addConstraint(new CPointOnLine(abMidLine, abMid));
    
    model.addConstraint(new CPointAlongSegment(a, b, abMid, 0.5));

    // I need to do some work before the following behaves right...
//    
//    constrainLength(model, ac, 3, ab); // express length of other lines in terms of known one.
//    constrainLength(model, bd, 2.34, ab);
//    constrainLength(model, abMidLine, 50); // make this one 50 long so I can see it.
//
//    constrainParallel(model, ac, bd); // ensure parallel lines
//    constrainPerpendicular(model, bd, ab); // ensure perpendiculars also
//
//    constrainPerpendicular(model, ab, abMidLine);
//    constrainIntersection(model, x, abMidLine, ad);
//
//    updateConstraints(model); // solve and update all drawable geometry with solved values
//
//    DrawingBuffer testBuffer = new DrawingBuffer();
//    DrawingBufferRoutines.line(testBuffer, ab, Color.BLACK, 1.4);
//    DrawingBufferRoutines.line(testBuffer, ac, Color.BLACK, 1.4);
//    DrawingBufferRoutines.line(testBuffer, bd, Color.BLACK, 1.4);
//    DrawingBufferRoutines.line(testBuffer, abMidLine, Color.LIGHT_GRAY, 1.4);
//    DrawingBufferRoutines.dot(testBuffer, a, 6, 1, Color.BLACK, Color.RED);
//    DrawingBufferRoutines.dot(testBuffer, b, 3, 0.5, Color.BLACK, Color.GREEN);
//    DrawingBufferRoutines.dot(testBuffer, c, 3, 0.5, Color.BLACK, Color.BLUE);
//    DrawingBufferRoutines.dot(testBuffer, d, 3, 0.5, Color.BLACK, Color.YELLOW);
//    DrawingBufferRoutines.dot(testBuffer, abMid, 3, 0.5, Color.BLACK, Color.MAGENTA);
//    DrawingBufferRoutines.dot(testBuffer, x, 3, 0.5, Color.BLACK, Color.MAGENTA);
//    DrawingBufferRoutines.text(testBuffer, a.getTranslated(6, 0), "a", Color.BLACK);
//    DrawingBufferRoutines.text(testBuffer, b.getTranslated(6, 0), "b", Color.BLACK);
//    DrawingBufferRoutines.text(testBuffer, c.getTranslated(6, 0), "c", Color.BLACK);
//    DrawingBufferRoutines.text(testBuffer, d.getTranslated(6, 0), "d", Color.BLACK);
//    DrawingBufferRoutines.text(testBuffer, x.getTranslated(6, 0), "x", Color.BLACK);
//    DrawingBufferRoutines.text(testBuffer, abMid.getTranslated(6, 0), "abMid", Color.BLACK);
//    bug("Length of line ab: " + num(ab.getLength()));
//    bug("Length of line ac: " + num(ac.getLength()));
//    bug("Length of line ad: " + num(bd.getLength()));
//    main.getDrawnStuff().addNamedBuffer("2", testBuffer, true);
  }

  public void tmpMakeStairs() {
    // make a series of "stairs" running from the top left downwards and to the right. It then 
    // simulates user interaction by changing the start location.
    ConstraintModel model = new ConstraintModel();

    CPoint[] vertices = new CPoint[];
    for (int i=0 i < vertices.length; i++) {
      vertices[i] = new CPoint();
    }

    CLine[] lines = new CLine[vertices.length - 1];
    for (int i=1; i < vertices.length; i++) {
      lines[i - 1] = new CLine();
      model.addConstraint(new CPointOnLine(lines[i - 1], vertices[i - 1]));
      model.addConstraint(new CPointOnLine(lines[i - 1], vertices[i]));
    }
    for (int i = 1; i < vertices.length; i++) {
      model.addConstraint(new CSomething(...));
    }
    
    for (int i = 1; i < vertices.length; i++) {
      constrainLocationRightOf(model, vertices[i - 1], vertices[i]);
      constrainLocationBelow(model, vertices[i - 1], vertices[i]);
    }
    for (int i = 1; i < lines.length; i++) {
      constrainPerpendicular(model, lines[i - 1], lines[i]);
    }
//    constrainLength(model, lines[0], 20);
//    constrainLength(model, lines[1], 50);
//    for (int i = 2; i < lines.length; i++) {
//      int which = ((i % 2) == 0) ? 0 : 1;
//      bug("constraining length of line " + i + " to be the same as line " + which);
//      constrainLength(model, lines[i], 1.0, lines[which]);
//    }
    for (int i=0; i < lines.length; i++) {
      int len = ((i % 2) == 0) ? 20 : 50;
      constrainLength(model, lines[i], len);
    }
    constrainLocation(model, vertices[0], 30, 30);
    updateConstraints(model); // solve and update all drawable geometry with solved values
    ChocoLogging.flushLogs();
    DrawingBuffer testBuffer = new DrawingBuffer();
    for (int i = 0; i < lines.length; i++) {
      DrawingBufferRoutines.line(testBuffer, lines[i], Color.BLACK, 1.4);
    }
    for (int i = 0; i < vertices.length; i++) {
      DrawingBufferRoutines.dot(testBuffer, vertices[i], 3.0, 0.5, Color.BLACK, Color.RED);
    }
    main.getDrawnStuff().addNamedBuffer("2", testBuffer, true);
  }

  /**
   * Create a Pt object that has Choco variables. These are set in the Pt object's "choco:x" and
   * "choco:y" attributes, and are of type choco.kernel.model.variables.real.RealVariable.
   */
  public Pt makeConstrainedPoint() {
    Pt ret = new Pt();
//    ret.setAttribute("choco:x", new RealVariable("x", -100000, 100000));
//    ret.setAttribute("choco:y", new RealVariable("y", -100000, 100000));
    ret.setAttribute("choco:x", new RealVariable("x", 0, 600));
    ret.setAttribute("choco:y", new RealVariable("y", 0, 600));
    points.add(ret);
    return ret;
  }

  /**
   * Many operations involve the direction of lines (line segments, really). This class implements a
   * vector representation of a line by creating two variables (dx and dy) that are defined as the
   * change in x and y of the line's start and end points.
   * 
   * See the 'getOrMakeVec(Line)' function, which caches and re-uses a ConstrainedVec.
   */
  public class ConstraintVec {
    public RealExpressionVariable dx, dy;

    /**
     * Make a vector, whose x and y components are set in the dx an dy variables.
     */
    ConstraintVec(Line line) {
      RealVariable startX = (RealVariable) line.getStart().getAttribute("choco:x");
      RealVariable startY = (RealVariable) line.getStart().getAttribute("choco:y");
      RealVariable endX = (RealVariable) line.getEnd().getAttribute("choco:x");
      RealVariable endY = (RealVariable) line.getEnd().getAttribute("choco:y");
      this.dx = Choco.minus(endX, startX);
      this.dy = Choco.minus(endY, startY);
    }

    /**
     * Creates an expression representing the squared length of the vector. Choco doesn't have a
     * square root operation so you'll have to use the squared length.
     */
    RealExpressionVariable getLengthSquared() {
      RealVariable lenSq = new RealVariable("lengthSquared", 0, 1000000);
      lenSq.addOptions(Options.V_NO_DECISION);
      return Choco.plus(Choco.power(dx, 2), Choco.power(dy, 2));
    }

  }

  /**
   * Returns a ConstraintVec for the given line. If the Line doesn't have one already, a
   * ConstraintVec is created. Otherwise the old one is returned.
   */
  public ConstraintVec getOrMakeVec(Line line) {
    ConstraintVec ret = (ConstraintVec) line.getAttribute("choco:line");
    if (ret == null) {
      ret = new ConstraintVec(line);
      line.setAttribute("choco:line", ret);
    }
    return ret;
  }

  /**
   * line.length = len
   */
  public void constrainLength(Model model, Line line, double len) {
    ConstraintVec v = getOrMakeVec(line);
    model.addConstraint(Choco.eq(v.getLengthSquared(), len * len));
  }

  /**
   * lineA.length = scaleFactor * lineB.length
   */
  public void constrainLength(Model model, Line lineA, double scaleFactor, Line lineB) {
    ConstraintVec vecA = getOrMakeVec(lineA);
    ConstraintVec vecB = getOrMakeVec(lineB);
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
  public void constrainLocation(Model model, Pt pt, double scale, Line line) {
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

  /**
   * Require a.y <= b.y
   */
  public void constrainLocationBelow(Model model, Pt a, Pt b) {
    RealVariable ay = (RealVariable) a.getAttribute("choco:y");
    RealVariable by = (RealVariable) b.getAttribute("choco:y");
    model.addConstraints(Choco.leq(ay, by));
  }

  /**
   * Require a.x <= b.x
   */
  public void constrainLocationRightOf(Model model, Pt a, Pt b) {
    RealVariable ax = (RealVariable) a.getAttribute("choco:x");
    RealVariable bx = (RealVariable) b.getAttribute("choco:x");
    model.addConstraints(Choco.leq(ax, bx));
  }

  /**
   * Make the two lines parallel. This works by constraining their cross products to zero. It does
   * not make guarantees about which "direction" the lines run, so you'll need additional
   * constraints for that.
   */
  public void constrainParallel(Model model, Line lineA, Line lineB) {
    // need to get the vectors for s1 and s2 and constrain them to be parallel. It does this by
    // using the cross product. The cross product of parallel vectors is 0.
    ConstraintVec a = getOrMakeVec(lineA);
    ConstraintVec b = getOrMakeVec(lineB);
    // a cross b = ax*by - ay*bx = 0
    model.addConstraints(Choco.eq(0, Choco.minus(Choco.mult(a.dx, b.dy), Choco.mult(a.dy, b.dx))));
  }

  /**
   * Make the two lines perpendicular.
   */
  public void constrainPerpendicular(Model model, Line lineA, Line lineB) {
    // need to get the vectors for s1 and s2 and constrain them to be perpendicular. The dot 
    // product of perpendicular vectors is zero.
    ConstraintVec a = getOrMakeVec(lineA);
    ConstraintVec b = getOrMakeVec(lineB);
    // a dot b = (ax * bx) + (ay * by) 
    model.addConstraints(Choco.eq(0, Choco.plus(Choco.mult(a.dx, b.dx), Choco.mult(a.dy, b.dy))));
  }

  /**
   * Constrain the point to be appear on the line.
   */
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

  /**
   * This solves the set of constraints in the given model and updates each point's location. After
   * this method is called, you may call pt.getX() and pt.getY() on vertices.
   */
  public void updateConstraints(Model model) {
    Solver solver = new CPSolver();
    solver.read(model);
    solver.solve();
    for (Pt pt : points) {
      updateLocation(pt, solver);
    }
  }

  private static void bug(String what) {
    Debug.out("SillyChocoTest", what);
  }

  /**
   * Sets the Pt object's x and y location based on its "choco:x" and "choco:y" constraint
   * variables. This only makes sense after running the Model through the Solver.
   */
  private void updateLocation(Pt pt, Solver solver) {
    RealVar vx = solver.getVar((RealVariable) pt.getAttribute("choco:x"));
    RealVar vy = solver.getVar((RealVariable) pt.getAttribute("choco:y"));
    double x = vx.getSup();
    double y = vy.getSup();
    pt.setLocation(x, y);
  }
}
