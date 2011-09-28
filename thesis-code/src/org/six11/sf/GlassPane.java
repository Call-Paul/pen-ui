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

import org.six11.util.Debug;
import org.six11.util.pen.PenEvent;
import org.six11.util.pen.PenListener;
import org.six11.util.pen.Pt;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.warn;
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

  private class MouseEventInfo {
    Point glassPanePoint;
    Container container;
    Point containerPoint;
    Component component;
    Point componentPoint;

    MouseEventInfo(MouseEvent ev) {
      glassPanePoint = ev.getPoint();
      container = editor.getContentPane();
      containerPoint = SwingUtilities.convertPoint(GlassPane.this, glassPanePoint, container);
      component = SwingUtilities.getDeepestComponentAt(container, containerPoint.x,
          containerPoint.y);
      componentPoint = null;
      if (component != null) {
        componentPoint = SwingUtilities.convertPoint(GlassPane.this, glassPanePoint, component);
      }
    }
  }

  private void givePenEvent(Component component, PenEvent ev) {
    if (component instanceof PenListener) {
      ((PenListener) component).handlePenEvent(ev);
    } else {
      warn(this, "Component not a pen listener: " + component.toString());
    }
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
    SketchBook mod = editor.getModel();
    MouseEventInfo mei = new MouseEventInfo(ev);

    if (mod.getGestures().isGesturing()) {
      bug("Gesture underway. what do I do now?");
    } else {
      givePenEvent(mei.component,
          PenEvent.buildDragEvent(this, new Pt(mei.componentPoint), null, 0, null, ev));
    }

  }

  public void mouseMoved(MouseEvent ev) {
    //    give(ev);
  }

  /**
   * This will never be implemented, because a click is mouse-centric.
   */
  public void mouseClicked(MouseEvent ev) {
    //    give(ev);
  }

  public void mouseEntered(MouseEvent ev) {
    //    give(ev);
  }

  public void mouseExited(MouseEvent ev) {
    //    give(ev);
  }

  public void mousePressed(MouseEvent ev) {
    MouseEventInfo mei = new MouseEventInfo(ev);
    SketchBook mod = editor.getModel();
    GestureController gests = mod.getGestures();
    Gesture pot = gests.getPotentialGesture();

    dragging = true;
    if (mei.componentPoint == null) {
      bug("mei.componentPoint is null");
    }
    if (pot != null && pot.isPointNearHotspot(new Pt(mei.componentPoint))) {
      // enter gesture ON mode
      pot.setActualGesture(true);
      Gesture gest = gests.getPotentialGesture().createSubsequentGesture(mei.componentPoint);
      mod.getGestures().addGesture(gest);
    } else {
      if (pot != null) {
        bug("Pressed, and there is a potential gesture still alive, but you are dragging far from it.");
        gests.revertPotentialGesture();
      }
      dragging = true; // down and dragging over component
      // now give it to component if we can.
      givePenEvent(mei.component, PenEvent.buildDownEvent(this, new Pt(mei.componentPoint), ev));
    }
    dragStartComponent = mei.component;
  }

  public void mouseReleased(MouseEvent ev) {
    MouseEventInfo mei = new MouseEventInfo(ev);
    SketchBook mod = editor.getModel();
    if (mod.getGestures().isGesturing()) {
      bug("Gesture complete...");
    } else {
      givePenEvent(mei.component, PenEvent.buildIdleEvent(this, ev));
    }
    dragging = false; // drag completed.
    dragStartComponent = null;
  }

  private void oldGive(MouseEvent ev) {
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
          ev.getModifiers(), componentPoint.x, componentPoint.y, ev.getClickCount(),
          ev.isPopupTrigger());
      prevComponent.dispatchEvent(exitEvent);
    }

    if (component != null && prevComponent != component) {
      Point componentPoint = SwingUtilities.convertPoint(this, glassPanePoint, component);
      MouseEvent enterEvent = new MouseEvent(component, MouseEvent.MOUSE_ENTERED, ev.getWhen(),
          ev.getModifiers(), componentPoint.x, componentPoint.y, ev.getClickCount(),
          ev.isPopupTrigger());
      component.dispatchEvent(enterEvent);
    }

    // handle gesture
    if (dragging && ev.getID() == MouseEvent.MOUSE_DRAGGED
        && editor.getModel().getGestures().isGesturing()) {
      // give to whichever component the mouse is over, or previous component if out of bounds
      if (component != null) {
        Point componentPoint = SwingUtilities.convertPoint(this, glassPanePoint, component);
        giveTo(component, ev, componentPoint);
      } else if (prevComponent != null) {
        Point componentPoint = SwingUtilities.convertPoint(this, glassPanePoint, prevComponent);
        giveTo(prevComponent, ev, componentPoint);
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
          && editor.getModel().getGestures().isGesturing()) {
        Point startComponentPoint = SwingUtilities.convertPoint(this, glassPanePoint,
            dragStartComponent);
        Point dropComponentPoint = SwingUtilities.convertPoint(this, glassPanePoint, component);
        giveTo(dragStartComponent, ev, startComponentPoint);
        gcev = new GestureCompleteEvent(this, dragStartComponent, component, dropComponentPoint,
            editor.getModel().getGestures().getPotentialGesture());
      }
      if (component != null) {
        Point componentPoint = SwingUtilities.convertPoint(this, glassPanePoint, component);
        giveTo(component, ev, componentPoint);
      }
      if (gcev != null) {
        fire(gcev);
      }
    }

    prevComponent = component;
  }

}
