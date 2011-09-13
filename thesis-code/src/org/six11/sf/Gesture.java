package org.six11.sf;

import org.six11.util.pen.Sequence;

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

  public abstract String getHumanName();

  public abstract double getProbability();

  public Sequence getOriginalSequence() {
    return originalPoints;
  }

}
