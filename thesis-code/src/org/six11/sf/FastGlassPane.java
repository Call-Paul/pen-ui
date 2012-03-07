package org.six11.sf;

import static org.six11.util.Debug.warn;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.lang.reflect.InvocationTargetException;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.six11.sf.GlassPane.ActivityMode;
import org.six11.util.pen.PenEvent;
import org.six11.util.pen.PenListener;
import org.six11.util.pen.Pt;

import static org.six11.util.Debug.bug;

public class FastGlassPane extends JComponent implements MouseListener {

  public enum ActivityMode {
    /**
     * 'None' means mouse events are passed through to the components below.
     */
    None,
    /**
     * 'DragSelection' means the user is dragging a selection from the drawing pane.
     */
    DragSelection,
    /**
     * 'DragScrap' means the user is dragging a scrap from the scrap grid.
     */
    DragScrap
  };

  SkruiFabEditor editor;
  private boolean dragging;
  private Timer timer;
  private TimerTask tt;
  private Point prevLoc;
  private ActivityMode activity;
  private Component prevComponent;
  private Component dragStartComponent;
  private Point dragPoint;
  private boolean gatherText;
  private StringBuilder numberInput;

  public FastGlassPane(final SkruiFabEditor editor) {
    this.editor = editor;
    this.activity = ActivityMode.None;
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
    numberInput = new StringBuilder();
    long eventMask = AWTEvent.KEY_EVENT_MASK;
    Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
      public void eventDispatched(AWTEvent ev) {
        if (gatherText) {
          if (ev.getID() == KeyEvent.KEY_TYPED && ev instanceof KeyEvent) {
            KeyEvent kev = (KeyEvent) ev;
            boolean ok = false;
            try {
              int val = Integer.parseInt("" + kev.getKeyChar());
              ok = true;
              numberInput.append(val);
            } catch (NumberFormatException ex) {
              // ignore
            }
            if (!ok) {
              bug("dealing with key event char is: " + kev.getKeyChar());
              if (kev.getKeyChar() == '.' && !numberInput.toString().contains(".")) {
                numberInput.append('.');
                ok = true;
              }
            }

            if (!ok) {
              bug("What is this? key code: " + kev.getKeyCode());
            }
            if (ok) {
              editor.getModel().addTextProgress(numberInput.toString());
            }
          } else if (ev.getID() == KeyEvent.KEY_RELEASED) {
            KeyEvent kev = (KeyEvent) ev;
            switch (kev.getKeyCode()) {
              case KeyEvent.VK_ENTER:
                editor.getModel().addTextFinished(numberInput.toString());
                numberInput.setLength(0);
                kev.consume();
                break;
              case KeyEvent.VK_BACK_SPACE:
              case KeyEvent.VK_DELETE:
                if (numberInput.length() > 0) {
                  numberInput.deleteCharAt(numberInput.length() - 1);
                  editor.getModel().addTextProgress(numberInput.toString());
                }
                kev.consume();
                break;
            }
          }
        }
      }
    }, eventMask);
  }

  public void paintComponent(Graphics g) {
    Image thumb = null;
    if (activity == ActivityMode.DragSelection) {
      thumb = editor.getModel().getDraggingThumb();
    } else if (activity == ActivityMode.DragScrap) {
      thumb = editor.getGrid().getSelectedThumb();
    }
    if (thumb != null) {
      g.drawImage(thumb, dragPoint.x, dragPoint.y, null);
    }
  }

  private void givePenEvent(Component component, PenEvent ev) {
    if (component instanceof PenListener) {
      ((PenListener) component).handlePenEvent(ev);
    } else if (component != null) {
      warn(this, "Component not a pen listener: " + component.toString());
    }
  }

  public void setActivity(ActivityMode mode) {
    this.activity = mode;
  }

  public ActivityMode getActivity() {
    return activity;
  }

  public void setGatherText(boolean value) {
    gatherText = value;
    if (!gatherText) {
      numberInput.setLength(0);
    }
  }

  private void placePoint(final Point loc) {
    if (!isVisible()) {
      bug("Not yet visible. Bailage.");
      return;
    }
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
    prevComponent = mei.component;
  }

  private void secretMouseDrag(Point loc, long time) {
    MouseEventInfo mei = new MouseEventInfo(loc);

    switch (activity) {
      case DragSelection:
        giveSelectionDrag(mei);
        break;
      case DragScrap:
        giveSelectionDrag(mei);
        break;
      case None:
        givePenEvent(mei.component, PenEvent.buildDragEvent(this, new Pt(mei.componentPoint, time)));
        break;
    }
    prevComponent = mei.component;
  }

  private void giveSelectionDrag(MouseEventInfo mei) {
    dragPoint = mei.containerPoint;
    Drag.Event ev = new Drag.Event(mei.componentPoint, activity);
    if (prevComponent != mei.component) {
      if (prevComponent instanceof Drag.Listener) {
        ((Drag.Listener) prevComponent).dragExit(ev);
      }
      if (mei.component instanceof Drag.Listener) {
        ((Drag.Listener) mei.component).dragEnter(ev);
      }
    }
    if (mei.component instanceof Drag.Listener) {
      ((Drag.Listener) mei.component).dragMove(ev);
    }
    repaint();
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
    dragPoint = ev.getPoint();
    editor.getModel().getConstraints().setPaused(true);
    MouseEventInfo mei = new MouseEventInfo(ev);
    givePenEvent(mei.component,
        PenEvent.buildDownEvent(this, new Pt(mei.componentPoint, ev.getWhen()), ev));
    prevComponent = mei.component;
    dragStartComponent = mei.component;
  }

  @Override
  public void mouseReleased(MouseEvent ev) {
    dragging = false;
    editor.getModel().getConstraints().setPaused(false);
    MouseEventInfo mei = new MouseEventInfo(ev);

    switch (activity) {
      case DragSelection:
        if (mei.component instanceof Drag.Listener) {
          Drag.Event dev = new Drag.Event(mei.componentPoint, activity);
          ((Drag.Listener) mei.component).dragDrop(dev);
        }
        editor.getModel().setDraggingSelection(false);
        activity = ActivityMode.None;
        givePenEvent(editor.getModel().getSurface(), PenEvent.buildIdleEvent(this, ev));
        break;
      case DragScrap:
        if (mei.component instanceof Drag.Listener) {
          Drag.Event dev = new Drag.Event(mei.componentPoint, activity);
          ((Drag.Listener) mei.component).dragDrop(dev);
        }
        editor.getGrid().clearSelection();
        activity = ActivityMode.None;
        break;
      case None:
        givePenEvent(mei.component, PenEvent.buildIdleEvent(this, ev));
        dragStartComponent = null;
        break;

    }

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
