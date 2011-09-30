package org.six11.sf;

import java.awt.Component;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.EventObject;

import static org.six11.util.Debug.bug;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class GestureEvent extends EventObject {

  private Point componentPoint;
  private Component targetComponent;
  private Gesture gesture;

  private Type type;

  public static enum Type {
    Start, Progress, End;
  }

  private GestureEvent(Object source, Component targetComponent, Point componentPoint,
      Gesture gesture) {
    super(source);
    this.targetComponent = targetComponent;
    this.componentPoint = componentPoint;
    this.gesture = gesture;
  }

  private void setType(Type t) {
    this.type = t;
  }

  public Type getType() {
    return type;
  }

  public static GestureEvent buildStartEvent(Object source, Component dragStartComponent,
      Point componentPoint, Gesture gesture) {
    GestureEvent ret = new GestureEvent(source, dragStartComponent, componentPoint, gesture);
    ret.setType(Type.Start);
    return ret;
  }

  public static GestureEvent buildProgressEvent(Object source, Component progressComponent,
      Point componentPoint, Gesture gesture) {
    GestureEvent ret = new GestureEvent(source, progressComponent, componentPoint, gesture);
    ret.setType(Type.Progress);
    return ret;
  }
  
  public static GestureEvent buildEndEvent(Object source, Component dragEndComponent,
      Point componentPoint, Gesture gesture) {
    gesture.setComponentEnd(dragEndComponent);
    GestureEvent ret = new GestureEvent(source, dragEndComponent, componentPoint, gesture);
    ret.setType(Type.End);
    return ret;
  }

  public Point getComponentPoint() {
    return componentPoint;
  }

  public Component getTargetComponent() {
    return targetComponent;
  }
  
  public Gesture getGesture() {
    return gesture;
  }

}
