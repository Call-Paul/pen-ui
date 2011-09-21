package org.six11.sf;

import java.awt.Component;
import java.awt.Point;
import java.util.EventObject;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class GestureCompleteEvent extends EventObject {

  private Point componentPoint;
  private Component dragEndComponent;
  private Component dragStartComponent;
  private Gesture gesture;
  
  public GestureCompleteEvent(Object source, Component dragStartComponent,
      Component dragEndComponent, Point componentPoint, Gesture gesture) {
    super(source);
    this.dragStartComponent = dragStartComponent;
    this.dragEndComponent = dragEndComponent;
    this.componentPoint = componentPoint;
    this.gesture = gesture;
  }

  public Point getComponentPoint() {
    return componentPoint;
  }

  public Component getDragEndComponent() {
    return dragEndComponent;
  }

  public Component getDragStartComponent() {
    return dragStartComponent;
  }
  
}
