package org.six11.sf;

import java.awt.Component;
import java.awt.Image;
import java.awt.Point;
import java.awt.image.BufferedImage;

import org.six11.util.Debug;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import static org.six11.util.Debug.bug;

/**
 * Represents a user-drawn gesture such as "erase" or "lasso select".
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public abstract class Gesture {

  Sequence originalPoints;
  Component componentStart;
  Component componentEnd;
  Image thumb;
  boolean drawThumbOnDrag;
  Point startLoc;

  public Gesture(Component start, Sequence originalPoints) {
    this.componentStart = start;
    this.originalPoints = originalPoints;
    this.thumb = null;
    this.drawThumbOnDrag = false;
  }

  /**
   * The gesture's probability. Set this in the constructor, or have a mutator method.
   */
  protected double p;
  private boolean wasReal;

  public abstract String getHumanName();

  public abstract double getProbability();

  protected void setStartLocation(Point mouseLoc) {
    this.startLoc = mouseLoc;
  }

  public Point getStartLocation() {
    return startLoc;
  }

  public Sequence getOriginalSequence() {
    return originalPoints;
  }

  public void setActualGesture(boolean v) {
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
  public abstract Gesture createSubsequentGesture(Component start, Point componentPoint);

  /**
   * Tells you if the given point is near the gesture or some other location that is relevant to the
   * gesture. For example if the gesture is an encircled region that selects some objects, it is
   * reasonable to consider the selected objects as part of the gesture to activate it.
   */
  public abstract boolean isPointNearHotspot(Pt pt);

  /**
   * Sets an optional image representing the contents of the gesture.
   * 
   * @param thumb
   */
  public void setThumbnail(Image thumb) {
    this.thumb = thumb;
  }

  /**
   * Gives an optional image representing the contents of the gesture. This might be null, depending
   * on which type of gesture it is.
   */
  public Image getThumbnail() {
    return thumb;
  }

  public Component getComponentStart() {
    return componentStart;
  }

  public Component getComponentEnd() {
    return componentEnd;
  }

  public void setComponentEnd(Component c) {
    this.componentEnd = c;
  }

  public void setDragImage(boolean showThumbWhenDragging) {
    drawThumbOnDrag = showThumbWhenDragging;
  }
}
