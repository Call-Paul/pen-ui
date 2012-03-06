package org.six11.sf;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.List;

import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.Vec;
import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

public class GuidePoint extends Guide {

  private Segment seg;
  private Vec param;
  private Pt pt;

  public GuidePoint(Segment seg, Vec param) {
    super(Type.Point);
    this.seg = seg;
    this.param = param;
  }

  public GuidePoint(Pt pt) {
    super(Type.Point);
    this.pt = pt;
  }

  public boolean isPinnedToSegment() {
    return seg != null;
  }

  public Segment getSegment() {
    return seg;
  }

  /**
   * A guide point can change locations.
   * 
   * @param pt
   */
  public void setLocation(Pt pt) {
    getLocation().setLocation(pt.getX(), pt.getY());
  }

  public Pt getLocation() {
    Pt ret = null;
    if (seg != null && param != null) {
      if (Functions.eq(0.0, param.getX(), Functions.EQ_TOL)) {
        ret = seg.getP1(); /*seg.getP1().copyXYT();*/
      } else if (Functions.eq(1.0, param.getX(), Functions.EQ_TOL)) {
        ret = seg.getP2();
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

  @Override
  public void draw(DrawingBuffer buf, Pt hoverPoint, Color color, Rectangle bounds) {
    Pt spot = getLocation();
    double distance = spot.distance(hoverPoint);
    double alpha = DrawingBufferLayers.getAlpha(distance, 5, 30, 0.1);
    Color c = new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (alpha * 255.0));
    Color alphaBlack = new Color(0f, 0f, 0f, (float) alpha);
    DrawingBufferRoutines.dot(buf, spot, 3.0, 0.3, alphaBlack, c);
  }

  /**
   * Tells you if seq[start] or seq[end] is near the guide point's location, within some short
   * tolerance like 6 pixels.
   */
  public boolean claims(Sequence seq, int start, int end) {
    Pt where = getLocation();
    double closest = Math.min(seq.get(start).distance(where), seq.get(end).distance(where));
    return (closest < 6.0);
  }

  public String toString() {
    return "point: " + num(getLocation());
  }

  public boolean isDynamic() {
    return false;
  }

  @Override
  public Guide getFixedCopy() {
    GuidePoint ret;
    if (pt == null) {
      ret = new GuidePoint(seg, param);
    } else {
      ret = new GuidePoint(pt);
    }
    return ret;
  }

  @Override
  public Segment adjust(Ink ink, int start, int end) {
    Pt where = getLocation();
    List<Segment> segs = ink.getSegments();
    if (segs != null) {
      Pt closestPt = null;
      double closestDist = Double.MAX_VALUE;
      for (Segment s : segs) {
        bug("  " + s);
        if (s.getP1() != null && s.getP1().distance(where) < closestDist) {
          closestDist = s.getP1().distance(where);
          closestPt = s.getP1();
        }
        if (s.getP2() != null && s.getP2().distance(where) < closestDist) {
          closestDist = s.getP2().distance(where);
          closestPt = s.getP2();
        }
      }
      closestPt.setLocation(where);
    }
    return null;
  }
}
