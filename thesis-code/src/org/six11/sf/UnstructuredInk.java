package org.six11.sf;

import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

import org.six11.sf.Ink.Type;
import org.six11.util.gui.BoundingBox;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class UnstructuredInk extends Ink {

  protected Sequence seq;

  public UnstructuredInk(Sequence seq) {
    super(Type.Unstructured);
    this.seq = seq;
  }

  public Rectangle2D getBounds() {
    if (bounds == null) {
      BoundingBox bb = new BoundingBox(seq.getPoints());
      bounds = bb.getRectangle();
    }
    return bounds;
  }

  public Area getArea() {
    if (area == null) {
      area = new Area(seq);
    }
    return area;
  }

  public Path2D getPath() {
    if (path == null) {
      path = new GeneralPath(seq);
    }
    return path;
  }

  public boolean isClosed() {
    return false;
  }

  public Sequence getSequence() {
    return seq;
  }

  /**
   * Returns 0 to 1 depending on how many points of the total unstructured ink stroke are included
   * in the target region.
   */
  public double getOverlap(Area target) {
    double numHits = 0;
    for (Pt pt : seq) {
      if (target.contains(pt)) {
        numHits = numHits + 1;
      }
    }
    return numHits / (double) seq.size();
  }

  public Ink copy() {
    return new UnstructuredInk(seq.copy());
  }

  public void move(double dx, double dy) {
    for (Pt moveMe : seq) {
      moveMe.setLocation(moveMe.getX() + dx, moveMe.getY() + dy);
    }
  }

}
