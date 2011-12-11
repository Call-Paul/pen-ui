package org.six11.sf;

import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;
import static org.six11.util.Debug.bug;

public class GuidePoint {

  private Segment seg;
  private Vec param;
  private Pt pt;
  
  public GuidePoint(Segment seg, Vec param) {
    this.seg = seg;
    this.param = param;
  }

  public GuidePoint(Pt pt) {
    this.pt = pt;
  }

  public Pt getLocation() {
    Pt ret = null;
    if (seg != null && param != null) {
      if (param.isZero()) {
        ret = seg.getP1().copyXYT();
      } else {
        Pt p1 = seg.getP1();
        Pt p2 = seg.getP2();
        double fullLen = p1.distance(p2);
        Vec v = new Vec(p1, p2).getUnitVector();
        double priComponent = param.getX() * fullLen;
        double altComponent = param.getY() * fullLen;
        Pt spot = p1.getTranslated(v, priComponent);
        spot = spot.getTranslated(v.getNormal().getFlip(), altComponent);
        ret = spot;
      }
    } else if (pt != null) {
      ret = pt;
    }
    
    if (ret == null) {
      bug("Warning: getLocation() only returns null if something has gone terribly wrong. Note: getLocation() is returning null.");
    }
    return ret;
  }

}
