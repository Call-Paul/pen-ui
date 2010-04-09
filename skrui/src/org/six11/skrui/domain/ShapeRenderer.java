package org.six11.skrui.domain;

import org.six11.skrui.script.Neanderthal;
import org.six11.util.pen.DrawingBuffer;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public abstract class ShapeRenderer {
 
  @SuppressWarnings("unused")
  public void draw(DrawingBuffer db, Shape s) {
  }

  @SuppressWarnings("unused")
  public void drawContextually(DrawingBuffer db, Shape s, Neanderthal data) {
  }
}
