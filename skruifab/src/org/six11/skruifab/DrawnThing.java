package org.six11.skruifab;

import org.six11.util.pen.DrawingBuffer;

/**
 * Represents an item that can be drawn on the screen. It is generally the result of a single user
 * action (such as drawing a stroke or filling a region).
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public interface DrawnThing {
  
  /**
   * Sets this thing's drawing buffer.
   */
  public void setDrawingBuffer(DrawingBuffer buf);

  /**
   * Gives access to this drawn thing's drawing buffer.
   */
  public DrawingBuffer getDrawingBuffer();

  /**
   * Each DrawnThing should have a unique ID to tell them apart.
   */
  public int getId();

  /**
   * Asks the drawn thing to revert to the previously known snap state, if there is one. It returns
   * the number of snap states remaining after the undo is performed. If it returns zero, it means
   * this thing has been completely undone and should no longer be included in the sketch state.
   * 
   * If the thing is in state K, and then undo() is called, the thing is now in state J, and K is at
   * the top of the redo stack. Calling undo() again puts J on top of the redo stack (pushing down
   * K), and we are now in state I. And so on.
   */
  public int undo();

  /**
   * Pops the top of the redo stack and puts it back on the thing's current state stack. See undo().
   */
  public void redo();

  /**
   * Take a snapshot of the current state for undo/redo purposes. This has two effects: first, it
   * adds to the current state stack. Second, it removes all elements from the redo stack.
   */
  public void snap();
}
