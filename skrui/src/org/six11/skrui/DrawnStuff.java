package org.six11.skrui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.six11.util.Debug;
import org.six11.util.pen.DrawingBuffer;

/**
 * Represents a layer of drawn elements. Each element is a DrawnThing implementation. Anonymous
 * elements are kept in an ordered list that represents painting order. Named elements may also be
 * provided but these are considered transient (will not be persisted).
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class DrawnStuff {
  private List<ChangeListener> changeListeners;

  private List<DrawnThing> things;
  private List<DrawnThing> redoList;
  private Map<String, DrawingBuffer> namedBuffers;
  private transient List<DrawingBuffer> cachedBufferList;
  private transient boolean dirty;

  public DrawnStuff() {
    this.things = new ArrayList<DrawnThing>();
    this.redoList = new ArrayList<DrawnThing>();
    this.namedBuffers = new HashMap<String, DrawingBuffer>();
    this.cachedBufferList = new ArrayList<DrawingBuffer>();
    this.dirty = false;
  }

  public void add(DrawnThing thing) {
    things.add(thing);
    branchRedo();
    fireChange();
  }

  public DrawnThing getMostRecentThing() {
    DrawnThing ret = null;
    if (things.size() > 0) {
      ret = things.get(things.size() - 1);
    }
    return ret;
  }
  
  private void branchRedo() {
    redoList.clear();
  }

  public void remove(DrawnThing thing) {
    things.remove(thing);
    fireChange();
  }

  /**
   * Returns a list of the anonymous drawn things in this DrawnStuff in order from oldest to newest.
   * This means you can be sure that the returned order is also the stacking order so the elements
   * can be persisted with that order.
   * 
   * This does not return any of the named buffers (unless of course they have been independently
   * added to the list of anonymous buffers).
   */
  public List<DrawnThing> getDrawnThings() {
    return things;
  }

  public DrawingBuffer getNamedBuffer(String name) {
    return namedBuffers.get(name);
  }

  private static void bug(String what) {
    Debug.out("DrawnStuff", what);
  }

  public List<DrawingBuffer> getDrawingBuffers() {
    if (dirty) {
      cachedBufferList.clear();
      for (DrawnThing dt : things) {
        cachedBufferList.add(dt.getDrawingBuffer());
      }
      for (DrawingBuffer db : namedBuffers.values()) {
        cachedBufferList.add(db);
      }
    }
    return cachedBufferList;
  }

  public void clear() {
    namedBuffers.clear();
    things.clear();
    fireChange();
  }

  public void addNamedBuffer(String name, DrawingBuffer db) {
    namedBuffers.put(name, db);
    fireChange();
  }

  public void removeNamedBuffer(String name) {
    namedBuffers.remove(name);
    fireChange();
  }

  /**
   * Fires a simple event indicating some (potentially) visual aspect of the data has changed.
   */
  public void fireChange() {
    dirty = true;
    if (changeListeners != null) {
      ChangeEvent ev = new ChangeEvent(this);
      for (ChangeListener cl : changeListeners) {
        cl.stateChanged(ev);
      }
    }
  }

  /**
   * Registers a change listener, which is whacked every time some (potentially) visual aspect of
   * the soup has changed and the GUI should be repainted.
   */
  public void addChangeListener(ChangeListener lis) {
    if (changeListeners == null) {
      changeListeners = new ArrayList<ChangeListener>();
    }
    if (!changeListeners.contains(lis)) {
      changeListeners.add(lis);
    }
  }

  /**
   * Sets the stack order of anonymous drawn things with a list of strings formatted as
   * "className id".
   */
  public void setStackOrder(List<String> order) {
    List<DrawnThing> newWorldOrder = new ArrayList<DrawnThing>();
    for (String who : order) {
      int space = who.indexOf(" ");
      String className = who.substring(0, space);
      int id = Integer.parseInt(who.substring(space + 1));
      DrawnThing extracted = null;
      for (DrawnThing dt : things) {
        if (dt.getClass().getName().equals(className)) {
          if (dt.getId() == id) {
            extracted = dt;
            break;
          }
        }
      }
      if (extracted != null) {
        things.remove(extracted);
        newWorldOrder.add(extracted);
      }
    }
    if (things.size() > 0) {
      bug("Warning: could not assign stack order for " + things.size()
          + " items. They were not in the given stack order.");
      for (DrawnThing dt : things) {
        newWorldOrder.add(dt);
      }
    }
    things = newWorldOrder;
    fireChange();
  }

  public void redo() {
    if (redoList.size() > 0) {
      DrawnThing dt = redoList.remove(redoList.size() - 1);
      things.add(dt);
      fireChange();
    }
  }

  public void undo() {
    if (things.size() > 0) {
      DrawnThing dt = things.remove(things.size() - 1);
      redoList.add(dt);
      fireChange();
    }
  }
}
