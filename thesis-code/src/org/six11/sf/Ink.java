package org.six11.sf;

import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

import org.six11.util.gui.BoundingBox;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Ink {

//  protected Type type;
  protected long created;
  protected Rectangle2D bounds;
  protected Area area;
  protected Path2D path;
  protected boolean analyzed;
  protected Sequence seq;

  public enum Type {
    Structured, Unstructured
  }

  public Ink(Sequence seq) {
//    this.type = type;
    this.seq = seq;
    created = System.currentTimeMillis();
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
    return new Ink(seq.copy());
  }

  public void move(double dx, double dy) {
    for (Pt moveMe : seq) {
      moveMe.setLocation(moveMe.getX() + dx, moveMe.getY() + dy);
    }
  }

//  public abstract Path2D getPath();
//
//  public abstract Area getArea();
//
//  public abstract Rectangle2D getBounds();
//
//  public abstract boolean isClosed();
//
//  public Type getType() {
//    return type;
//  }

  public boolean isAnalyzed() {
    return analyzed;
  }

  public void setAnalyzed(boolean v) {
    analyzed = v;
  }

  /**
   * Returns a value indicating how much of this ink is in the target region. Returns zero for no
   * inclusion, one for complete inclusion, and some number in between if the ink is only partially
   * contained. The exact semantics are type-specific. 
   */
//  public abstract double getOverlap(Area target);
//
//  public abstract Ink copy();
//
//  public abstract void move(double dx, double dy);
}
