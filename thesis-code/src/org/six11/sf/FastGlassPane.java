package org.six11.sf;

import static org.six11.util.Debug.warn;

import java.awt.Component;
import java.awt.Container;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.lang.reflect.InvocationTargetException;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.six11.sf.GlassPane.ActivityMode;
import org.six11.util.pen.PenEvent;
import org.six11.util.pen.PenListener;
import org.six11.util.pen.Pt;

import static org.six11.util.Debug.bug;

public class FastGlassPane extends JComponent implements MouseListener {

  SkruiFabEditor editor;
  private boolean dragging;
  private Timer timer;
  private TimerTask tt;
  private Point prevLoc;

  public FastGlassPane(SkruiFabEditor editor) {
    this.editor = editor;
    timer = new Timer();
    tt = new TimerTask() {
      public void run() {
        PointerInfo info = MouseInfo.getPointerInfo();
        Point loc = info.getLocation();
        placePoint(loc);
      }
    };
    timer.schedule(tt, 10, 10);
    addMouseListener(this);
  }

  private void givePenEvent(Component component, PenEvent ev) {
    if (component instanceof PenListener) {
      ((PenListener) component).handlePenEvent(ev);
    } else if (component != null) {
      warn(this, "Component not a pen listener: " + component.toString());
    }
  }

  public void setActivity(ActivityMode dragselection) {
  }

  public ActivityMode getActivity() {
    return null;
  }

  public void setGatherText(boolean b) {
  }

  private void placePoint(final Point loc) {
    final long now = System.currentTimeMillis();
    Component container = editor.getContentPane();
    SwingUtilities.convertPointFromScreen(loc, container);
    boolean sameSpot = false;
    if (prevLoc != null) {
      sameSpot = prevLoc.x == loc.x && prevLoc.y == loc.y;
    }
    prevLoc = loc;
    if (!sameSpot) {
      try {
        Runnable r;
        if (dragging) {
          // equivalent to a mouseDragged event.
          r = new Runnable() {
            public void run() {
              secretMouseDrag(loc, now);
            }
          };
        } else {
          // equivalent to a mousePressed event.
          r = new Runnable() {
            public void run() {
              secretMouseMove(loc, now);
            }
          };
        }
        SwingUtilities.invokeAndWait(r);
      } catch (InterruptedException e) {
        e.printStackTrace();
      } catch (InvocationTargetException e) {
        e.printStackTrace();
      }
    }
  }

  protected void secretMouseMove(Point loc, long now) {
    MouseEventInfo mei = new MouseEventInfo(loc);
    givePenEvent(mei.component, PenEvent.buildHoverEvent(this, new Pt(loc.x, loc.y, now)));
  }

  private void secretMouseDrag(Point loc, long time) {
    MouseEventInfo mei = new MouseEventInfo(loc);
    givePenEvent(mei.component, PenEvent.buildDragEvent(this, new Pt(mei.componentPoint, time)));
  }

  public void mouseClicked(MouseEvent ev) {
  }

  @Override
  public void mouseEntered(MouseEvent ev) {
  }

  @Override
  public void mouseExited(MouseEvent ev) {
  }

  @Override
  public void mousePressed(MouseEvent ev) {
    dragging = true;
    editor.getModel().getConstraints().setPaused(true);
    MouseEventInfo mei = new MouseEventInfo(ev);
    givePenEvent(mei.component,
        PenEvent.buildDownEvent(this, new Pt(mei.componentPoint, ev.getWhen()), ev));
  }

  @Override
  public void mouseReleased(MouseEvent ev) {
    dragging = false;
    editor.getModel().getConstraints().setPaused(false);
    MouseEventInfo mei = new MouseEventInfo(ev);
    givePenEvent(mei.component, PenEvent.buildIdleEvent(this, ev));
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
      containerPoint = SwingUtilities.convertPoint(FastGlassPane.this, glassPanePoint, container);
      component = SwingUtilities.getDeepestComponentAt(container, containerPoint.x,
          containerPoint.y);
      componentPoint = null;
      if (component != null) {
        componentPoint = SwingUtilities.convertPoint(FastGlassPane.this, glassPanePoint, component);
      }
    }

    MouseEventInfo(Point pt) {
      glassPanePoint = pt;
      container = editor.getContentPane();
      containerPoint = SwingUtilities.convertPoint(FastGlassPane.this, glassPanePoint, container);
      component = SwingUtilities.getDeepestComponentAt(container, containerPoint.x,
          containerPoint.y);
      componentPoint = null;
      if (component != null) {
        componentPoint = SwingUtilities.convertPoint(FastGlassPane.this, glassPanePoint, component);
      }
    }
  }

}
