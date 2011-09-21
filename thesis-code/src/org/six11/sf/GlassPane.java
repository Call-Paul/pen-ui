package org.six11.sf;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.Collection;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class GlassPane extends JComponent implements MouseMotionListener, MouseListener {

  SkruiFabEditor editor;
  Component prevComponent;
  Component dragStartComponent;
  Component dragEndComponent;

  boolean dragging = false;
  private Collection<GestureListener> gestureListeners;

  public GlassPane(SkruiFabEditor editor) {
    this.editor = editor;
    gestureListeners = new ArrayList<GestureListener>();
    addMouseListener(this);
    addMouseMotionListener(this);
  }

  public void addGestureListener(GestureListener lis) {
    if (!gestureListeners.contains(lis)) {
      gestureListeners.add(lis);
    }
  }

  public Component getDragEndComponent() {
    return dragEndComponent;
  }

  private void give(MouseEvent ev) {

    Point glassPanePoint = ev.getPoint();
    Container container = editor.getContentPane();
    Point containerPoint = SwingUtilities.convertPoint(this, glassPanePoint, container);
    Component component = SwingUtilities.getDeepestComponentAt(container, containerPoint.x,
        containerPoint.y);

    if (ev.getID() == MouseEvent.MOUSE_RELEASED) {
      dragEndComponent = component;
    }

    // handle exit, then enter
    if (prevComponent != null && prevComponent != component) {
      Point componentPoint = SwingUtilities.convertPoint(this, glassPanePoint, prevComponent);
      MouseEvent exitEvent = new MouseEvent(prevComponent, MouseEvent.MOUSE_EXITED, ev.getWhen(),
          ev.getModifiers(), componentPoint.x, componentPoint.y, ev.getClickCount(), ev
              .isPopupTrigger());
      prevComponent.dispatchEvent(exitEvent);
      bug("EXIT " + prevComponent.getName());
    }

    if (component != null && prevComponent != component) {
      Point componentPoint = SwingUtilities.convertPoint(this, glassPanePoint, component);
      MouseEvent enterEvent = new MouseEvent(component, MouseEvent.MOUSE_ENTERED, ev.getWhen(), ev
          .getModifiers(), componentPoint.x, componentPoint.y, ev.getClickCount(), ev
          .isPopupTrigger());
      component.dispatchEvent(enterEvent);
      bug("ENTER " + component.getName());
    }

    // handle gesture
    if (dragging && ev.getID() == MouseEvent.MOUSE_DRAGGED && editor.getModel().isGesturing()) {
      // give to whichever component the mouse is over, or previous component if out of bounds
      if (component != null) {
        Point componentPoint = SwingUtilities.convertPoint(this, glassPanePoint, component);
        giveTo(component, ev, componentPoint);
        //        bug("Case 1: " + component.getName() + " " + num(componentPoint));
      } else if (prevComponent != null) {
        Point componentPoint = SwingUtilities.convertPoint(this, glassPanePoint, prevComponent);
        giveTo(prevComponent, ev, componentPoint);
        //        bug("Case 2: " + prevComponent.getName() + " " + num(componentPoint));
      }
    }

    // handle non-gesture dragging
    else if (dragging && ev.getID() == MouseEvent.MOUSE_DRAGGED) {
      Point componentPoint = SwingUtilities.convertPoint(this, glassPanePoint, dragStartComponent);
      giveTo(dragStartComponent, ev, componentPoint);
    }

    // handle non-drag
    else {
      GestureCompleteEvent gcev = null;
      // up/down, hover
      // when a gesture ends, tell the originating component there has been a mouse up.
      if (dragStartComponent != null && ev.getID() == MouseEvent.MOUSE_RELEASED
          && editor.getModel().isGesturing()) {
        Point startComponentPoint = SwingUtilities.convertPoint(this, glassPanePoint,
            dragStartComponent);
        Point dropComponentPoint = SwingUtilities.convertPoint(this, glassPanePoint, component);
        giveTo(dragStartComponent, ev, startComponentPoint);
        bug("Told " + dragStartComponent.getName() + " that the gesture has ended. I think.");
        gcev = new GestureCompleteEvent(this, dragStartComponent, component, dropComponentPoint,
            editor.getModel().getPotentialGesture());
      }
      if (component != null) {
        Point componentPoint = SwingUtilities.convertPoint(this, glassPanePoint, component);
        giveTo(component, ev, componentPoint);
        //        bug("Case 4: " + component.getName() + " " + num(componentPoint));
      }
      if (gcev != null) {
        fire(gcev);
      }
    }

    prevComponent = component;

  }

  private void fire(GestureCompleteEvent gcev) {
    for (GestureListener lis : gestureListeners) {
      lis.gestureComplete(gcev);
    }
  }

  private void giveTo(Component component, MouseEvent ev, Point componentPoint) {
    component.dispatchEvent(new MouseEvent(component, ev.getID(), ev.getWhen(), ev.getModifiers(),
        componentPoint.x, componentPoint.y, ev.getClickCount(), ev.isPopupTrigger()));
  }

  public void paintComponent(Graphics g) {
    // nothing
  }

  public void mouseDragged(MouseEvent ev) {
    give(ev);
  }

  public void mouseMoved(MouseEvent ev) {
    give(ev);
  }

  public void mouseClicked(MouseEvent ev) {
    give(ev);
  }

  public void mouseEntered(MouseEvent ev) {
    give(ev);
  }

  public void mouseExited(MouseEvent ev) {
    give(ev);
  }

  public void mousePressed(MouseEvent ev) {
    dragging = true;
    Point glassPanePoint = ev.getPoint();
    Container container = editor.getContentPane();
    Point containerPoint = SwingUtilities.convertPoint(this, glassPanePoint, container);
    dragStartComponent = SwingUtilities.getDeepestComponentAt(container, containerPoint.x,
        containerPoint.y);
    give(ev);
  }

  public void mouseReleased(MouseEvent ev) {
    give(ev);
    dragging = false;
    dragStartComponent = null;
  }

}
