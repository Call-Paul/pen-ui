package org.six11.skrui.domain;

import org.six11.util.pen.DrawingBuffer;

/**
 * 
 *
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public abstract class ShapeRenderer {
  public abstract void draw(DrawingBuffer db, Shape s);
}
