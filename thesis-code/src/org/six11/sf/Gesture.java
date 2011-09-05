package org.six11.sf;

/**
 * Represents a user-drawn gesture such as "erase" or "lasso select".
 *
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public abstract class Gesture {
  
  /**
   * The gesture's probability. Set this in the constructor, or have a mutator method.
   */
  protected double p;

  public abstract String getHumanName();

  public abstract double getProbability();

}
