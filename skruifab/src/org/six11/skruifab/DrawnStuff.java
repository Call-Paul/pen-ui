package org.six11.skruifab;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.six11.util.Debug;
import org.six11.util.pen.DrawingBuffer;

/**
 * Holds layers of drawn elements. Each element is a DrawnThing implementation. Anonymous elements
 * are kept in an ordered list that represents painting order.
 * 
 * Named elements may also be provided but these are considered transient (will not be persisted).
 * Named buffers are really useful for debugging.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class DrawnStuff {
  private List<ChangeListener> changeListeners;

  private List<DrawnThing> things;
  private Map<String, DrawingBuffer> namedBuffers;
  private Set<String> transientBufferNames;
  private transient List<DrawingBuffer> cachedBufferList;
  private transient boolean dirty;
  private Map<String, List<DrawnThing>> layers;
  private List<String> layerNamesInOrder;

  public DrawnStuff() {
    this.things = new ArrayList<DrawnThing>();
    this.namedBuffers = new HashMap<String, DrawingBuffer>();
    this.transientBufferNames = new HashSet<String>();
    this.cachedBufferList = new ArrayList<DrawingBuffer>();
    this.layers = new HashMap<String, List<DrawnThing>>();
    this.layerNamesInOrder = new ArrayList<String>();
    this.dirty = false;
  }

  /**
   * Add a new thing to the list of drawn stuff. This defers to addToLayer(), using the 'on top'
   * layer, or makes a default layer if there isn't one yet.
   */
  public void add(DrawnThing thing) {
    if (layerNamesInOrder.isEmpty()) {
      addLayer("default layer", false);
    }
    addToLayer(layerNamesInOrder.get(0), thing);
  }

  /**
   * Create a layer and put it "on top".
   */
  public void addLayer(String layerName, boolean onTop) {
    List<DrawnThing> layerData = new ArrayList<DrawnThing>();
    layers.put(layerName, layerData);
    if (onTop) {
      layerNamesInOrder.add(layerName);
    } else {
      layerNamesInOrder.add(0, layerName);
    }
    bug("Added layer. They are in order:");
    for (String ln : layerNamesInOrder) {
      bug("  " + ln);
    }
  }

  public void addToLayer(String layerName, DrawnThing thing) {
    if (!layers.containsKey(layerName)) {
      addLayer(layerName, false);
    }
    layers.get(layerName).add(thing);
    // TODO: not messing with editlist or undo/redo until I figure out wth I'm doing with that.
    fireChange();
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
    //    editList.add(thing);
    thing.snap();
    //    redoList.clear();
    fireChange();
  }

  /**
   * Removes the given thing from the list of known drawn elements (and from redo/undo lists).
   */
  public void remove(DrawnThing thing) {
    things.remove(thing);
    //    editList.remove(thing);
    //    redoList.remove(thing);
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
      for (DrawingBuffer db : namedBuffers.values()) {
        cachedBufferList.add(db);
      }
      for (DrawnThing dt : things) { // TODO: I MOVED the 'things' block below the 'namedBuffers' block. If drawing is wonky move it back.
        cachedBufferList.add(dt.getDrawingBuffer());
      }
      for (String layerName : layerNamesInOrder) {
        for (DrawnThing layerData : layers.get(layerName)) {
          cachedBufferList.add(layerData.getDrawingBuffer());
        }
      }
    }
    return cachedBufferList;
  }

  public void clear() {
    namedBuffers.clear();
    things.clear();
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

  private static void bug(String what) {
    Debug.out("DrawnStuff", what);
  }

  public boolean hasLayer(String name) {
    return layerNamesInOrder.contains(name);
  }

  public List<DrawnThing> getLayer(String name) { 
    return layers.get(name);
  }

}
