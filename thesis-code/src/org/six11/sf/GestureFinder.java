package org.six11.sf;

import java.util.ArrayList;
import java.util.List;

import org.six11.util.pen.Sequence;
import static org.six11.util.Debug.bug;

/**
 * 
 *
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public interface GestureFinder {

  public Gesture findGesture(Sequence seq);

}
