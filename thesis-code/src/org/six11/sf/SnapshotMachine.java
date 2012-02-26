package org.six11.sf;

import java.util.Stack;

import org.json.JSONException;
import org.json.JSONObject;
import org.six11.util.pen.Pt;
import org.six11.util.solve.VariableBank;
import static org.six11.util.Debug.bug;

public class SnapshotMachine {

  private SketchBook model;

  /**
   * The undo stack where each state is placed when go() is called.
   */
  private Stack<Snapshot> undoStack;

  /**
   * The redo stack is where the top of undoStack is popped to. This lets you move back and forth.
   */
  private Stack<Snapshot> redoStack;

  public SnapshotMachine(SketchBook model) {
    this.model = model;
    this.undoStack = new Stack<Snapshot>();
    this.redoStack = new Stack<Snapshot>();
  }

  public Snapshot save() {
    Snapshot ret = new Snapshot(model);
    undoStack.push(ret);
    redoStack.clear();
    return ret;
  }

  public Snapshot get(int idx) {
    return undoStack.get(idx);
  }

  public int length() {
    return undoStack.size();
  }

  public static Segment load(JSONObject segObj, SketchBook model) {
    SegmentDelegate d = null;
    try {
      VariableBank bank = model.getConstraints().getVars();
      String typeStr = segObj.getString("type");
      String pt1Name = segObj.getString("p1");
      String pt2Name = segObj.getString("p2");
      if (typeStr.equals(Segment.Type.Line.toString())) {
        Pt p1 = bank.getPointWithName(pt1Name);
        Pt p2 = bank.getPointWithName(pt2Name);
        d = new LineSegment(p1, p2);
      } else {
        bug("Can't load type " + typeStr + " from JSONObject yet");
      }
    } catch (JSONException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    Segment ret = new Segment(d);
    return ret;
  }
  
  public void load(Snapshot snap) {
    bug("Loading " + snap);
    undoStack.push(snap);
    snap.load();
    bug("Loaded " + snap);
    model.getEditor().drawStuff();
  }

  public Snapshot undo() {
    Snapshot prev = undoStack.pop();
    redoStack.push(prev);
    return prev;
  }
  
  public Snapshot redo() {
    Snapshot next = redoStack.pop();
    undoStack.push(next);
    return next;
  }

  public int getUndoLength() {
    return undoStack.size();
  }

  public int getRedoLength() {
    return redoStack.size();
  }
  
}
