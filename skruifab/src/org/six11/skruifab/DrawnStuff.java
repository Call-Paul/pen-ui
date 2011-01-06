package org.six11.skruifab;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.six11.util.pen.DrawingBuffer;

/**
 * Represents a layer of drawn elements. Each element is a DrawnThing implementation. Anonymous
 * elements are kept in an ordered list that represents painting order.
 * 
 * Named elements may also be provided but these are considered transient (will not be persisted).
 * Named buffers are really useful for debugging.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class DrawnStuff {
  private List<ChangeListener> changeListeners;

  private List<DrawnThing> things;
  private List<DrawnThing> editList;
  private List<DrawnThing> redoList;
  private Map<String, DrawingBuffer> namedBuffers;
  private Set<String> transientBufferNames;
  private transient List<DrawingBuffer> cachedBufferList;
  private transient boolean dirty;

  public DrawnStuff() {
    this.things = new ArrayList<DrawnThing>();
    this.redoList = new ArrayList<DrawnThing>();
    this.editList = new ArrayList<DrawnThing>();
    this.namedBuffers = new HashMap<String, DrawingBuffer>();
    this.transientBufferNames = new HashSet<String>();
    this.cachedBufferList = new ArrayList<DrawingBuffer>();
    this.dirty = false;
  }

  /**
   * Add a new thing to the list of drawn stuff. This simply calls edit(thing).
   */
  public void add(DrawnThing thing) {
    edit(thing);
  }

  /**
   * Records the fact that a thing has been manipulated. If this is a newly made thing it is added
   * to the end of the drawing list. It also is added to the end of the edit list (for undo/redo
   * porpoises), and a snapshot is taken (thing.snap()).
   */
  public void edit(DrawnThing thing) {
    if (!things.contains(thing)) {
      things.add(thing);
    }
    editList.add(thing);
    thing.snap();
    redoList.clear();
    fireChange();
  }

  /**
   * Returns the element on the top of the edit list, if any. It returns null if nothing has been
   * drawn.
   */
  public DrawnThing getMostRecentThing() {
    DrawnThing ret = null;
    if (editList.size() > 0) {
      ret = editList.get(editList.size() - 1);
    }
    return ret;
  }

  /**
   * Removes the given thing from the list of known drawn elements (and from redo/undo lists).
   */
  public void remove(DrawnThing thing) {
    things.remove(thing);
    editList.remove(thing);
    redoList.remove(thing);
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
    editList.clear();
    redoList.clear();
    fireChange();
  }

  public void addNamedBuffer(String name, DrawingBuffer db, boolean isTransient) {
    namedBuffers.put(name, db);
    if (isTransient) {
      transientBufferNames.add(name);
    } else {
      transientBufferNames.remove(name); // in case it was there before.
    }
    fireChange();
  }

  public void removeNamedBuffer(String name) {
    namedBuffers.remove(name);
    fireChange();
  }

  public void removeTransientBuffers() {
    for (String name : transientBufferNames) {
      namedBuffers.remove(name);
    }
    transientBufferNames.clear();
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
      dt.redo();
      if (!things.contains(dt)) {
        things.add(dt);
      }
      editList.add(dt);
      removeTransientBuffers();
      fireChange();
    }
  }

  public void undo() {
    if (things.size() > 0) {
      DrawnThing dt = editList.remove(editList.size() - 1);
      int remaining = dt.undo();
      if (remaining == 0) {
        things.remove(dt);
      }
      redoList.add(dt);
      removeTransientBuffers();
      fireChange();
    }
  }

}
