package org.six11.sf;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

import org.six11.util.gui.BoundingBox;
import org.six11.util.pen.Sequence;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public abstract class Ink {

  protected Type type;
  protected long created;
  protected Rectangle2D bounds;
  protected Area area;
  protected Path2D path;
  protected boolean analyzed;

  public enum Type {
    Structured, Unstructured
  }

  public Ink(Type type) {
    this.type = type;
    created = System.currentTimeMillis();
  }

  public abstract Path2D getPath();

  public abstract Area getArea();

  public abstract Rectangle2D getBounds();

  public abstract boolean isClosed();

  public Type getType() {
    return type;
  }

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
  public abstract double getOverlap(Area target);

  public abstract Ink copy();

  public abstract void move(double dx, double dy);
}
