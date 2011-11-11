package org.six11.sf;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.warn;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.six11.util.pen.PenEvent;
import org.six11.util.pen.PenListener;
import org.six11.util.pen.Pt;

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
  Point dragPoint = null;

  public GlassPane(SkruiFabEditor editor) {
    this.editor = editor;
    addMouseListener(this);
    addMouseMotionListener(this);
    setOpaque(false);
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
    } else if (component != null) {
      warn(this, "Component not a pen listener: " + component.toString());
    }
  }

  public void paintComponent(Graphics g) {
  }

  public void mouseDragged(MouseEvent ev) {
    dragPoint = ev.getPoint();
    MouseEventInfo mei = new MouseEventInfo(ev);
    if (prevComponent != mei.component) {
      if (prevComponent != null) {
        givePenEvent(prevComponent, PenEvent.buildExitEvent(this, ev));
      }
      if (mei.component != null) {
        givePenEvent(mei.component, PenEvent.buildEnterEvent(this, ev));
      }
    }
    prevComponent = mei.component;
    Pt pt = new Pt(mei.componentPoint, ev.getWhen());
    givePenEvent(mei.component, PenEvent.buildDragEvent(this, pt, null, 0, null, ev));
    if (dragging && dragPoint != null /* && mod.getGestures().getGesture() != null */) {
      repaint();
    }
  }

  public void mouseMoved(MouseEvent ev) {
    MouseEventInfo mei = new MouseEventInfo(ev);
    if (prevComponent != mei.component) {
      if (prevComponent != null) {
        givePenEvent(prevComponent, PenEvent.buildExitEvent(this, ev));
      }
      if (mei.component != null) {
        givePenEvent(mei.component, PenEvent.buildEnterEvent(this, ev));
      }
    }
    prevComponent = mei.component;
    Pt translated = new Pt(ev);
    translated.setLocation(mei.componentPoint.getX(), mei.componentPoint.getY());
    givePenEvent(mei.component, PenEvent.buildHoverEvent(this, translated));
  }

  /**
   * This will never be implemented, because a click is mouse-centric.
   */
  public void mouseClicked(MouseEvent ev) {
  }

  public void mouseEntered(MouseEvent ev) {
    // This means the mouse entered the glass pane---it is somewhere over the app, but if you want more info you have to look
    MouseEventInfo mei = new MouseEventInfo(ev);
    givePenEvent(mei.component, PenEvent.buildEnterEvent(this, ev));
  }

  public void mouseExited(MouseEvent ev) {
    // This means the mouse exited the glass pane---it is somewhere over the app, but if you want more info you have to look
    MouseEventInfo mei = new MouseEventInfo(ev);
    givePenEvent(mei.component, PenEvent.buildExitEvent(this, ev));
  }

  public void mousePressed(MouseEvent ev) {
    MouseEventInfo mei = new MouseEventInfo(ev);
    dragging = true;
    dragPoint = ev.getPoint();
    if (mei.componentPoint == null) {
      bug("mei.componentPoint is null");
    }

    // now give it to component if we can.
    givePenEvent(mei.component,
        PenEvent.buildDownEvent(this, new Pt(mei.componentPoint, ev.getWhen()), ev));
    dragStartComponent = mei.component;
  }

  public void mouseReleased(MouseEvent ev) {
    dragPoint = null;
    MouseEventInfo mei = new MouseEventInfo(ev);
    givePenEvent(mei.component, PenEvent.buildIdleEvent(this, ev));
    dragging = false; // drag completed.
    dragStartComponent = null;
  }

}
