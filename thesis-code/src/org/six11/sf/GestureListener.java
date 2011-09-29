package org.six11.sf;

/**
 * 
 *
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public interface GestureListener {

  void gestureStart(GestureEvent ev);
  void gestureProgress(GestureEvent ev);
  void gestureComplete(GestureEvent ev);

}
