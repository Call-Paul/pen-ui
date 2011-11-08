package org.six11.sf;

import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.Collection;
import java.util.HashSet;

import org.six11.util.pen.Pt;

import static org.six11.util.Debug.num;
import static org.six11.util.Debug.bug;

public class StructuredInk {

  Segment seg;
  Path2D path;
  Area area;

  public StructuredInk(Segment seg) {
    this.seg = seg;
  }

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

//  public Collection<EndCap> getEndCaps() {
//    Collection<EndCap>ret = new HashSet<EndCap>();
//    ret.add(new EndCap(this.seg, EndCap.WhichEnd.Start));
//    ret.add(new EndCap(this.seg, EndCap.WhichEnd.End));
//    return ret;
//  }

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

  public double getOverlap(Area target) {
    bug("not sure what this should do, but if you see this, FREAK OUT");
    return 0;
  }

  public Ink copy() {
    // TODO Auto-generated method stub
    return null;
  }

//  public void move(double dx, double dy) {
//    area = null;
//    path = null;
//    bug("Moving by " + dx + ", " + dy);
//    seg.move(dx, dy);
//  }
  
  public Segment getSegment() {
    return seg;
  }

}
