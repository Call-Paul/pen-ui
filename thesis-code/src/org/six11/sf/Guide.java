package org.six11.sf;

import java.awt.Color;
import java.awt.Rectangle;

import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

public abstract class Guide {

  public enum Type {
    Circle, Line, Point, Unknown
  };
  
  protected Pt fixedHover;
  protected Type type;
  
  protected Guide(Type t) {
    this.type = t;
  }

//  public abstract void draw(DrawingBuffer buf, Pt hoverPoint, Color in, Rectangle bounds);

  /**
   * Some guides have one empty point, and calculations defer to the current pen location. Call this
   * to fix the hover point for a guide so it has something to use when claiming ink. This sets the
   * fixedHover protected member so subclasses can use it as needed.
   */
  public void setFixedHover(Pt hoverPt) {
    this.fixedHover = hoverPt;
  }

  public abstract boolean claims(Sequence seq, int start, int end);
  
  public abstract String toString();
  
  public abstract boolean isDynamic();

  public abstract Guide getFixedCopy();

  public abstract Segment adjust(Ink ink, int start, int end);

  public Type getType() {
    return type;
  }
}
