package org.six11.sf;

import static org.six11.util.Debug.bug;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.six11.util.io.FileUtil;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;
import org.six11.util.solve.ConstraintSolver.State;
import org.six11.util.solve.VariableBank;

public class SnapshotMachine {

  private SketchBook model;

  /**
   * The list of all state.
   */
  private List<Snapshot> state;

  private transient File rootDir;

  /**
   * Cursor that points to one past the state the user is looking at right now. Normally this is
   * simply state.size(). But when the user seeks in the undo/redo list, this value moves around
   * between [0..state.size()]. When a new snapshot is saved, it is placed at state[stateCursor],
   * and all snapshots indexed higher are discarded.
   */
  private int stateCursor = 0;

  private boolean snapshotRequested;

  private Set<Integer> staleDisplayLists;

  private long dirtyTime;

  private boolean dirty;

  private boolean snapsEnabled;

  private List<String> reasons;

  public SnapshotMachine(SketchBook model) {
    this.model = model;
    this.reasons = new ArrayList<String>(); // records reasons why snapshots are taken. cleared when snap is made.
    this.snapsEnabled = true;
    this.state = new ArrayList<Snapshot>();
    this.staleDisplayLists = new HashSet<Integer>();
    File debugOutput = new File("snapshots");
    if (!debugOutput.exists()) {
      boolean ok = debugOutput.mkdirs();
      if (ok) {
        rootDir = debugOutput;
        bug("Using snapshot root directory: " + rootDir.getAbsolutePath());
      }
    } else {
      rootDir = debugOutput;
    }
    if (rootDir != null) {
      File[] oldFiles = rootDir.listFiles();
      for (File oldFile : oldFiles) {
        oldFile.delete();
      }
      bug("Deleted " + oldFiles.length + " files in root directory.");
    }
  }

  public Snapshot save() {
    Snapshot ret = null;
    if (snapsEnabled && (model.getConstraints().getSolutionState() == State.Solved)
        && snapshotRequested) {
      snapshotRequested = false;
      ret = takeSnapshotImmediately();
    }
    return ret;
  }

  public Snapshot takeSnapshotImmediately() {
    Snapshot ret = new Snapshot(model);
    if (snapsEnabled) {
      reasons.clear();
      push(ret);
      if (rootDir != null) {
        File snapFile = new File(rootDir, "snapshot-" + ret.getID() + ".txt");
        File bugFile = new File(rootDir, "bug-snapshot-" + ret.getID() + ".txt");
        try {
          FileUtil.writeStringToFile(snapFile, ret.getJSONRoot().toString(2), false);
          FileUtil.writeStringToFile(bugFile, model.getMondoDebugString(), false);
        } catch (JSONException e) {
          FileUtil.writeStringToFile(snapFile, "Unable to print json object!", false);
        }
      }
    } else {
      bug("Warning: you are calling takeSnapshotImmediately() while snapshots are disabled.");
    }
    return ret;
  }

  public int[] flushStaleDisplayLists() {
    Integer[] ids = staleDisplayLists.toArray(new Integer[0]);
    int[] ret = new int[ids.length];
    for (int i = 0; i < ids.length; i++) {
      ret[i] = ids[i];
    }
    staleDisplayLists.clear();
    return ret;
  }

  public void setSnapshotsEnabled(boolean val) {
    this.snapsEnabled = val;
  }

  public void requestSnapshot(String reason) {
    reasons.add(reason);
    this.snapshotRequested = true;
    model.getSurface().snapshot();
  }

  public Snapshot get(int idx) {
    return state.get(idx);
  }

  public int length() {
    return state.size();
  }

  public static Segment load(JSONObject segObj, SketchBook model) {
    SegmentDelegate d = null;
    int segID = 0;
    try {
      segID = segObj.getInt("segID");
      VariableBank bank = model.getConstraints().getVars();
      String typeStr = segObj.getString("type");
      if (typeStr == null) {
        bug("typeStr is null while loading segment " + segID);
      }
      String pt1Name = segObj.optString("p1");
      if (pt1Name == null) {
        bug("pt1Name is null while loading segment " + segID + " of type " + typeStr);
      }
      String pt2Name = segObj.optString("p2");
      if (pt2Name == null) {
        bug("pt2Name is null while loading segment type " + segID + " of type " + typeStr);
      }
      if (typeStr.equals(Segment.Type.Line.toString())) {
        Pt p1 = bank.getPointWithName(pt1Name);
        Pt p2 = bank.getPointWithName(pt2Name);
        d = new LineSegment(p1, p2);
      } else if (typeStr.equals(Segment.Type.Curve.toString())) {
        Pt p1 = bank.getPointWithName(pt1Name);
        Pt p2 = bank.getPointWithName(pt2Name);
        double[] pri = makeDoubleArray(segObj.getJSONArray("pri"));
        double[] alt = makeDoubleArray(segObj.getJSONArray("alt"));
        d = new CurvySegment(p1, p2, pri, alt);
      } else if (typeStr.equals(Segment.Type.EllipticalArc.toString())) {
        Pt p1 = bank.getPointWithName(pt1Name);
        Pt p2 = bank.getPointWithName(pt2Name);
        double[] pri = makeDoubleArray(segObj.getJSONArray("pri"));
        double[] alt = makeDoubleArray(segObj.getJSONArray("alt"));
        d = new EllipseArcSegment(p1, p2, pri, alt);
      } else if (typeStr.equals(Segment.Type.CircularArc.toString())) {
        Pt p1 = bank.getPointWithName(pt1Name);
        Pt p2 = bank.getPointWithName(pt2Name);
        double cpx = segObj.getDouble("cpx");
        double cpy = segObj.getDouble("cpy");
        double mpx = segObj.getDouble("mpx");
        double mpy = segObj.getDouble("mpy");
        Vec centerVec = new Vec(cpx, cpy);
        Vec arcMidVec = new Vec(mpx, mpy);
        d = new CircularArcSegment(p1, p2, centerVec, arcMidVec);
      } else if (typeStr.equals(Segment.Type.Circle.toString())) {
        d = new CircleSegment(model, segObj);
      } else if (typeStr.equals(Segment.Type.Ellipse.toString())) {
        d = new EllipseSegment(model, segObj);
      } else if (typeStr.equals(Segment.Type.Blob.toString())) {
        d = new Blob(model, segObj);
      } else {
        bug("***");
        bug("*** Can't load type " + typeStr
            + " from JSONObject yet. I will now System.exit(0) in your face.");
        bug("***");
        System.exit(0);
      }
    } catch (JSONException e) {
      e.printStackTrace();
    }
    Segment ret = new Segment(d, segID);
    return ret;
  }

  private static double[] makeDoubleArray(JSONArray arr) throws JSONException {
    double[] ret = new double[arr.length()];
    for (int i = 0; i < arr.length(); i++) {
      ret[i] = arr.getDouble(i);
    }
    return ret;
  }

  public void load(Snapshot snap) {
    snap.load();
    setDirty();
  }

  public void setDirty() {
    dirtyTime = System.currentTimeMillis();
    dirty = true;
  }

  public void clearDirty() {
    dirty = false;
  }

  public Snapshot undo() {
    Snapshot ret = null;
    if (stateCursor > 1) { // don't go below one
      stateCursor = stateCursor - 1; // decrement
      ret = state.get(stateCursor - 1);
    } else {
      bug("Unable to undo. Cursor: " + stateCursor + " / " + length());
    }
    return ret;
  }

  public Snapshot redo() {
    Snapshot ret = null;
    if (stateCursor < state.size()) { // don't got beyond end
      ret = state.get(stateCursor);
      stateCursor = stateCursor + 1;
    } else {
      bug("Unable to redo. Cursor: " + stateCursor + " / " + length());
    }
    return ret;
  }

  public Snapshot getCurrent() {
    return state.get(stateCursor - 1);
  }

  public int getCurrentIdx() {
    return stateCursor - 1;
  }

  public boolean canRedo() {
    return stateCursor < state.size();
  }

  public boolean canUndo() {
    return stateCursor > 1;
  }

  public void push(Snapshot snap) {
    setDirty();
    if (stateCursor < state.size()) {
      Snapshot old = state.remove(stateCursor);
      staleDisplayLists.add(old.getDisplayListID());
    }
    state.add(stateCursor, snap); // add snapshot at cursor
    stateCursor = stateCursor + 1; // increment cursor
  }

  public long getLastDirtyTime() {
    return dirtyTime;
  }

  public boolean isDirty() {
    return dirty;
  }

  public void setCurrentIdx(int currentIdx) {
    stateCursor = currentIdx + 1;
  }

}
