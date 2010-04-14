package org.six11.skrui.script;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.six11.skrui.BoundedParameter;
import org.six11.skrui.DrawingBufferRoutines;
import org.six11.skrui.SkruiScript;
import org.six11.skrui.mesh.Mesh;
import org.six11.skrui.mesh.Triangle;
import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.data.Statistics;
import org.six11.util.pen.ConvexHull;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.SequenceEvent;
import org.six11.util.pen.SequenceListener;
import org.six11.util.pen.Vec;

public class Scribbler extends SkruiScript implements SequenceListener {

  Neanderthal data;
  Statistics linelike;
  Statistics timestamps;
  boolean filling;
  Sequence seq;
  int lastExaminedIndex;
  double halfWindowPixels = 10;
  List<Integer> possibleCornerIndexes;
  ConvexHull hull;
  List<Pt> penPath;
  Mesh mesh;
  boolean meshDrawn = false;
  Pt lastDrag;
  int cornerNumThreshold = 6;

//  public Scribbler(Neanderthal data) {
//  }

  public void sendDown(Sequence seq) {
    // first see if this is a continuation.
    boolean shouldFill = false;
    Pt nub = seq.getLast().getTranslated(0.5, 0.5);
    if (mesh != null && (nub.getTime() - mesh.getTime()) < 4000) {
      ConvexHull h = mesh.getHull();
      if (Functions.isPointInRegion(nub, h.getHullClosed())) {
        shouldFill = true;
      }
    }
    this.seq = seq;
    hull = null;
    penPath = null;
    mesh = null;
    lastDrag = null;
    lastExaminedIndex = -1;
    possibleCornerIndexes.clear();
    linelike.clear();

    if (shouldFill) {
      beginFill();
    }
  }

  public List<Pt> getPossibleCorners() {
    List<Pt> ret = new ArrayList<Pt>();
    for (int idx : possibleCornerIndexes) {
      ret.add(seq.get(idx));
    }
    return ret;
  }

  public void sendDrag() {
    if (!filling) {
      examine();
    } else {
      expandHull();
    }
  }

  private void draw(boolean done) {
    DrawingBuffer db = new DrawingBuffer();
    if (db.isVisible() && penPath != null && penPath.size() > 2) {
      Set<Triangle> inside = seekInside();
      DrawingBufferRoutines.triangles(db, inside, main.getPenColor());
      if (done) {
        data.addRegion(new Mesh(mesh.getPoints(), 0));
      }
      main.addBuffer("scribble fill", db);
    }
  }

  private Set<Triangle> seekInside() {
    Set<Triangle> ret = new HashSet<Triangle>();
    // Now get a convex hull for everything, including the original scribble hull and the pen path.
    List<Pt> bigHullPoints = new ArrayList<Pt>(hull.getHull());
    bigHullPoints.addAll(penPath);
    ConvexHull bigHull = new ConvexHull(bigHullPoints);
    bigHullPoints = bigHull.getHull();
    for (Triangle t : mesh.getTriangles()) {
      if (Functions.isPointInRegion(t.getCentroid(), bigHullPoints)) {
        ret.add(t);
      }
    }
    return ret;
  }

  private void beginFill() {
    filling = true;
    seq.setAttribute(Neanderthal.SCRAP, true);
    // hull = new ConvexHull(getPossibleCorners());
    hull = new ConvexHull(seq.getPoints());
    penPath = new ArrayList<Pt>();
    mesh = new Mesh(hull.getHull(), 0);
    draw(false);
    lastDrag = seq.getLast();
    bug("** FILLING **");
  }

  private void expandHull() {
    if (seq.size() > 3 && hull != null) {
      Pt here = seq.getLast();
      if (!Functions.isPointInRegion(here, hull.getHullClosed())) {
        if (here.distance(lastDrag) > 6) {
          lastDrag = here;
          try {
            // Color color = data.getDrawingSurface().getSoup().getPenColor();
            if (mesh.expand(here)) {
              penPath.add(here);
              draw(false);
            } else {
              bug("I did not expand (maybe it contained that point already?");
            }
          } catch (Exception ex) {
            // occasionally seq is null, and I'm not sure why though I suspect threading issues.
            ex.printStackTrace();
          }
        }
      }
    }
  }

  private void examine() {
    int examineMe = -1;
    if (lastExaminedIndex < 0) {
      if (seq.length() > halfWindowPixels) {
        for (int i = 0; i < seq.size(); i++) {
          if (seq.get(i).getDouble("path-length", 0) > halfWindowPixels) {
            examineMe = i;
            break;
          }
        }
      }
    } else {
      examineMe = lastExaminedIndex + 1;
    }
    if (examineMe > 0) {
      int leftIdx = look(examineMe, -1);
      int rightIdx = look(examineMe, 1);
      detectCorner(leftIdx, rightIdx, examineMe);
    }
  }

  private void detectCorner(int leftIdx, int rightIdx, int mid) {
    if (leftIdx >= 0 && rightIdx >= 0) {
      Vec v1 = new Vec(seq.get(mid), seq.get(leftIdx));
      Vec v2 = new Vec(seq.get(mid), seq.get(rightIdx));
      if (v1.mag() > (halfWindowPixels * 0.8) && v1.mag() > (halfWindowPixels * 0.8)) {
        double angle = Math.abs(Functions.getAngleBetween(v1, v2));
        if (angle < 1) {
          seq.get(mid).setDouble("scribbler corner value", angle);
          addPossibleCornerIndex(mid);
        }
      }
      lastExaminedIndex = mid;
    }
  }

  private void addPossibleCornerIndex(int idx) {
    // only need to compare it with the most recently added point, if any. If the path length is
    // closer than the halfWindowPixels value, choose the one with the better angle.
    boolean yes = false;
    if (possibleCornerIndexes.size() > 0) {
      int lastIdx = possibleCornerIndexes.size() - 1;
      int mostRecentIdx = possibleCornerIndexes.get(lastIdx);
      if (pl(idx, mostRecentIdx) < halfWindowPixels) {
        Pt mostRecent = seq.get(mostRecentIdx);
        Pt me = seq.get(idx);
        if (mostRecent.getDouble("scribbler corner value") > me.getDouble("scribbler corner value")) {
          possibleCornerIndexes.remove(lastIdx);
          seq.get(lastIdx).setBoolean("scribble-actual-corner", false);
          possibleCornerIndexes.add(idx);
        }
      } else {
        // before adding idx, do some housekeeping on the earlier corners.
        if (possibleCornerIndexes.size() >= 2) {
          // the next to last and last points are irrefutably corners, so we can data to
          // the lengths and pointgraph for them.
          int aIdx = possibleCornerIndexes.get(lastIdx - 1);
          int bIdx = possibleCornerIndexes.get(lastIdx);
          Pt a = seq.get(aIdx);
          Pt b = seq.get(bIdx);
          // pg.add(b);
          timestamps.addData(b.getTime() - a.getTime());
          double idealLength = a.distance(b);
          double pathLength = pl(aIdx, bIdx);
          linelike.addData(idealLength / pathLength);
          yes = true;
        }
        // now add the corner at hand.
        possibleCornerIndexes.add(idx);
        seq.get(idx).setBoolean("scribble-possible-corner", true);
        seq.get(idx).setBoolean("scribble-actual-corner", true);
      }
    } else {
      possibleCornerIndexes.add(idx);
    }

    if (yes && linelike.getN() == cornerNumThreshold && linelike.getMin() >= 0.9) {
      beginFill();
    }
  }

  private int look(int startIdx, int dir) {
    int ret = -1;
    int cursor = startIdx;
    while (cursor >= 0 && cursor < seq.size()) {
      if (pl(startIdx, cursor) > halfWindowPixels) {
        ret = cursor;
        break;
      } else {
        cursor = cursor + dir;
      }
    }
    return ret;
  }

  /**
   * Get absolute value of path length from a to b.
   */
  private double pl(int idxA, int idxB) {
    return Math
        .abs(seq.get(idxA).getDouble("path-length") - seq.get(idxB).getDouble("path-length"));
  }

  public void sendUp() {
    if (filling) {
      draw(true);
      bug("** Finished doing mondo draw.");
      DrawingBuffer db = main.getBuffer("scribble fill");
      if (db != null) {
        main.addToLayer("fill", db);
      }
    }
    filling = false;
  }

  private static void bug(String what) {
    Debug.out("Scribbler", what);
  }

  @Override
  public void initialize() {
    bug("Scribbler initializing...");
    this.data = (Neanderthal) main.getScript("Neanderthal");
    data.addSequenceListener(this);
    possibleCornerIndexes = new ArrayList<Integer>();
    // pg = new PointGraph();
    timestamps = new Statistics();
    timestamps.setMaximumN(cornerNumThreshold);
    linelike = new Statistics();
    linelike.setMaximumN(cornerNumThreshold);

    bug("Scribbler initialized!");
  }
  
  public static Arguments getArgumentSpec() {
    Arguments args = new Arguments();
    args.setProgramName("Scribbler: scribble to fill regions.");
    args.setDocumentationProgram("Detects scribble gestures that begins a fill operation.");

    Map<String, BoundedParameter> defs = getDefaultParameters();
    for (String k : defs.keySet()) {
      BoundedParameter p = defs.get(k);
      args.addFlag(p.getKeyName(), ArgType.ARG_OPTIONAL, ValueType.VALUE_REQUIRED, p
          .getDocumentation()
          + " Defaults to " + p.getValueStr() + ". ");
    }
    return args;
  }

  public static Map<String, BoundedParameter> getDefaultParameters() {
    Map<String, BoundedParameter> defs = new HashMap<String, BoundedParameter>();

    return defs;
  }


  @Override
  public Map<String, BoundedParameter> initializeParameters(Arguments args) {
    // TODO: why is this not just a part of SkruiScript?
    Map<String, BoundedParameter> params = copyParameters(getDefaultParameters());
    for (String k : params.keySet()) {
      if (args.hasFlag(k)) {
        if (args.hasValue(k)) {
          params.get(k).setValue(args.getValue(k));
          bug("Set " + params.get(k).getHumanReadableName() + " to " + params.get(k).getValueStr());
        } else {
          params.get(k).setValue("true");
          bug("Set " + params.get(k).getHumanReadableName() + " to " + params.get(k).getValueStr());
        }
      }
    }
    return params;
  }
  
  public void handleSequenceEvent(SequenceEvent seqEvent) {
    SequenceEvent.Type type = seqEvent.getType();
    Sequence seq = seqEvent.getSeq();
    switch (type) {
      case BEGIN:
        sendDown(seq);
        break;
      case PROGRESS:
        sendDrag();
        break;
      case END:
        sendUp();
        break;
    }
    
  }
}
