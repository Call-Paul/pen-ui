package org.six11.sf;

import java.awt.Color;
import java.awt.Rectangle;

import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Pt;

public abstract class Guide {

  public abstract void draw(DrawingBuffer buf, Pt hoverPoint, Color in, Rectangle bounds);


}
