package org.six11.sf;

import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

import org.six11.util.pen.Pt;

import static org.six11.util.Debug.num;
import static org.six11.util.Debug.bug;

public class StructuredInk extends Ink {

  Segment seg;
  Path2D path;

  public StructuredInk(Segment seg) {
    super(Type.Structured);
    this.seg = seg;
  }

  @Override
  public Path2D getPath() {
    if (path == null) {
      switch (seg.type) {
        case Line:
          path = new GeneralPath(seg.asLine());
          break;
        case Curve:
          path = new GeneralPath(seg.asSpline());
          break;
        case EllipticalArc:
          path = new GeneralPath(seg.asSpline());
          break;
        case Unknown:
          bug("Not sure what to do with segment type Unknown");
      }
    }
    return path;
  }

  public Area getArea() {
    if (area == null) {
      Path2D p = getPath();
      area = new Area(p.getBounds2D());
    }
    return area;
  }

  public Rectangle2D getBounds() {
    return getArea().getBounds2D();
  }

  public boolean isClosed() {
    return false;
  }

  @Override
  public double getOverlap(Area target) {
    bug("not sure what this should do, but if you see this, FREAK OUT");
    return 0;
  }

  @Override
  public Ink copy() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void move(double dx, double dy) {
    area = null;
    path = null;
    bug("Moving by " + dx + ", " + dy);
    seg.move(dx, dy);
  }
  
  public Segment getSegment() {
    return seg;
  }

}
