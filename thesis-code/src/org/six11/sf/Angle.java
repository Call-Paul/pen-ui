package org.six11.sf;

import org.six11.util.pen.Pt;
import org.six11.util.solve.AngleConstraint;
import static org.six11.util.Debug.num;

public class Angle {

  Pt a, f, b;

  public Angle(Pt a, Pt f, Pt b) {
    this.a = a;
    this.f = f;
    this.b = b;
  }

  public Pt getPtA() {
    return a;
  }

  public Pt getPtB() {
    return b;
  }

  public Pt getFulcrum() {
    return f;
  }

  public Pt[] getPointArray() {
    return new Pt[] {
        a, f, b
    };
  }

  public String toString() {
    String nA = SketchBook.n(a);
    String nF = SketchBook.n(f);
    String nB = SketchBook.n(b);
    return "Angle " + nA + "/" + nF + "/" + nB + " ("
        + num(Math.toDegrees(AngleConstraint.measureAngle(a, f, b))) + ")";
  }

}
