package org.six11.sf;

import java.awt.Point;

import org.six11.util.Debug;
import org.six11.util.pen.Sequence;
import static org.six11.util.Debug.bug;

/**
 * Represents a user-drawn gesture such as "erase" or "lasso select".
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public abstract class Gesture {

  Sequence originalPoints;

  public Gesture(Sequence originalPoints) {
    this.originalPoints = originalPoints;
  }

  /**
   * The gesture's probability. Set this in the constructor, or have a mutator method.
   */
  protected double p;
  private boolean wasReal;

  public abstract String getHumanName();

  public abstract double getProbability();

  public Sequence getOriginalSequence() {
    return originalPoints;
  }

  public void setActualGesture(boolean v) {
    bug("It really was a gesture!");
    this.wasReal = v;
  }

  public boolean wasActualGesture() {
    return wasReal;
  }

  /**
   * A gesture may have follow-up gestures. For example, the first pen stroke might encircle an
   * object while the second indicates the encircled object should move. In such a case, the first
   * pen stroke constitutes an EncircleGesture 'circGest'. To generate a MoveGesture, call
   * circGest.createSubsequentGesture(..).
   */
  public abstract Gesture createSubsequentGesture(Point componentPoint);

}
