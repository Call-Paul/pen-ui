package org.six11.sf;

import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

import org.six11.util.gui.shape.Circle;
import org.six11.util.pen.Functions;
import org.six11.util.pen.IntersectionData;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;

public class EndCap {

  public static enum WhichEnd {
    Start, End
  }

  private StructuredInk ink;
  private Pt pt;
  private Vec dir;
  private Area area;
  private Path2D shape;
  private Rectangle2D bounds;
  private Line line;
  private WhichEnd end;

  public EndCap(StructuredInk ink, WhichEnd end) {
    this.ink = ink;
    this.end = end;
    Segment s = ink.getSegment();
    this.pt = (end == WhichEnd.Start ? s.getP1() : s.getP2());
    this.dir = (end == WhichEnd.Start ? s.getStartDir() : s.getEndDir()).getFlip();
    double dist = s.getP1().distance(s.getP2());
    double rad = dist / 20;
    this.area = new Area();
    area.add(new Area(new Circle(pt, rad)));
    area.add(makeTriangle(pt, dir, rad, rad * 6));
    this.shape = new Path2D.Double();
    shape.append(area.getPathIterator(null), false);
    this.bounds = area.getBounds2D();
    this.line = new Line(pt, dir);
  }

  private Area makeTriangle(Pt end, Vec dir, double halfHeight, double width) {
    Vec norm1 = dir.getNormal().getVectorOfMagnitude(halfHeight);
    Vec norm2 = norm1.getFlip();
    Pt p1 = norm1.add(end);
    Pt p2 = norm2.add(end);
    Pt p3 = dir.getVectorOfMagnitude(width).add(end);
    Path2D retPath = new Path2D.Double();
    retPath.moveTo(p1.getX(), p1.getY());
    retPath.lineTo(p2.getX(), p2.getY());
    retPath.lineTo(p3.getX(), p3.getY());
    retPath.closePath();
    Area ret = new Area(retPath);
    return ret;
  }

  public StructuredInk getInk() {
    return ink;
  }

  public Pt getPt() {
    return pt;
  }

  public Vec getDir() {
    return dir;
  }

  public Area getArea() {
    return area;
  }

  public Path2D getShape() {
    return shape;
  }

  public Rectangle2D getBounds() {
    return bounds;
  }

  public Line getLine() {
    return line;
  }

  public WhichEnd getEnd() {
    return end;
  }
  
  public IntersectionData intersect(EndCap other) {
    return Functions.getIntersectionData(getLine(), other.getLine());
  }

  /**
   * Returns true if these two endcaps have the same ink and end (using getInk() and getEnd()).
   */
  public boolean same(EndCap other) {
    return ink == other.getInk() && end == other.getEnd();
  }
  
  public String toString() {
    return getInk().getSegment().toString() + " " + end;
  }
}
