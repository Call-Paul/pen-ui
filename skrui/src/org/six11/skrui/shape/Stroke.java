package org.six11.skrui.shape;

import org.six11.skrui.DrawnThing;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Sequence;

/**
 * 
 *
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Stroke extends Sequence implements DrawnThing {

  DrawingBuffer db;
  
  /**
   * @param id
   */
  public Stroke(int id) {
    super(id);
  }

  /**
   * 
   */
  public Stroke() {
    super();
  }

  public void setDrawingBuffer(DrawingBuffer buf) {
    db = buf;
  }
  
  public DrawingBuffer getDrawingBuffer() {
    return db;
  }

}
