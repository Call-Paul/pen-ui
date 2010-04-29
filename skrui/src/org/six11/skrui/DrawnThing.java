package org.six11.skrui;

import org.six11.util.pen.DrawingBuffer;

/**
 * Represents an item that can be drawn on the screen. It is generally the result of a single user
 * action (such as drawing a stroke or filling a region).
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public interface DrawnThing {
  public void setDrawingBuffer(DrawingBuffer buf);

  public DrawingBuffer getDrawingBuffer();

  public int getId();

}
