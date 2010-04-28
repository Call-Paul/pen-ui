package org.six11.skrui.script;

import java.awt.Cursor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.six11.skrui.BoundedParameter;
import org.six11.skrui.DrawingBufferRoutines;
import org.six11.skrui.SkruiScript;
import org.six11.skrui.mesh.HalfEdge;
import org.six11.skrui.mesh.Mesh;
import org.six11.skrui.mesh.Triangle;
import org.six11.skrui.mesh.Where;
import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.data.Statistics;
import org.six11.util.pen.*;

public class Scribbler2 extends SkruiScript implements SequenceListener {

  /**
   * Reference to the mothership.
   */
  Neanderthal data;

  //
  // ---------------------------------------- member variables for detecting scribble gesture -----
  //
  Statistics linelike;
  Statistics timestamps;
  boolean filling;
  Sequence seq;
  int lastExaminedIndex;
  double halfWindowPixels = 10;
  List<Integer> possibleCornerIndexes;
  int cornerNumThreshold = 6;

  //
  // ---------------------------------------- member variables for making a filled region ---------
  //
  List<Pt> penPath;
  Mesh mesh;
  Pt lastDrag;

  private double previousThickness;

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

  public void sendDown(Sequence seq) {
    // clear/set the gesture-detection variables
    this.seq = seq;
    lastExaminedIndex = -1;
    possibleCornerIndexes.clear();
    linelike.clear();

    // clear the mesh-forming variables
    // hull = null;
    mesh = null;
  }

  public List<Pt> getPossibleCorners() {
    List<Pt> ret = new ArrayList<Pt>();
    for (int idx : possibleCornerIndexes) {
      ret.add(seq.get(idx));
    }
    return ret;
  }

  public void sendDrag() {
    if (filling) {
      expandRegion(); // user is filling---might involve expanding the region.
    } else {
      examine(); // user is not (yet) filling---examine input to see if there is a scribble gesture
    }
  }

  private void beginFill() {
    filling = true;
    main.setTransientMode(this);
    data.forget(seq, false);
    previousThickness = main.getPenThickness();
    seq.setAttribute("pen thickness", 1.0);
    main.setPenThickness(1.0);
    ConvexHull hull = new ConvexHull(getPossibleCorners());
    penPath = new ArrayList<Pt>(hull.getHullClosed());
    mesh = new Mesh();
    mesh.setSequenceMatters(true);
    for (Pt hp : hull.getHull()) {
      mesh.addPoint(hp);
    }
    lastDrag = seq.getLast();
    draw(false);
  }
  
  public Cursor getCursor() {
    return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
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
          seq.get(mid).setDouble("Scribbler2 corner value", angle);
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
        if (mostRecent.getDouble("Scribbler2 corner value") > me
            .getDouble("Scribbler2 corner value")) {
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
      main.setPenThickness(previousThickness);
      main.clearTransientMode();
    }
    filling = false;
  }

  //
  // ---------------------------------------- member functions for making a filled region ---------
  //

  private void draw(boolean done) {
    mesh.classifyTriangles();

    if (done) {
      drawFinalMesh();
    } else {
      DrawingBuffer db = new DrawingBuffer();
      DrawingBufferRoutines.triangles(db, mesh.getInsideTriangles(), main.getPenColor());
      main.addBuffer("scribble fill", db);
    }
  }

  private void drawFinalMesh() {
    data.forget(seq, false);
    data.getRegions().add(mesh);
    main.removeBuffer("scribble fill"); // the 'fill' layer will show it now---avoid double draw

    Set<Triangle> explored = new HashSet<Triangle>();
    HalfEdge e = getBoundaryEdge(explored);
    while (e != null) {
      HalfEdge cursor = e;
      List<Pt> boundary = new ArrayList<Pt>();
      do {
        boundary.add(cursor.getPoint());
        cursor = crawl(cursor, explored);
      } while (cursor != e);
      boundary.add(boundary.get(0));
      DrawingBuffer shapeBuffer = new DrawingBuffer();
      DrawingBufferRoutines.fill(shapeBuffer, boundary, main.getPenThickness(), main
          .getPenColor(), main.getPenColor());
      main.addToLayer("fill", shapeBuffer);
      e = getBoundaryEdge(explored); // do it again if there's an unexplored boundary triangle.
    }
  }

  private HalfEdge crawl(HalfEdge e, Set<Triangle> explored) {
    HalfEdge cursor = e.getNext().getPair();
    explored.add(cursor.getFace());
    while (cursor.getFace().getMeshLocation() == Where.Inside) {
      cursor = cursor.getNext().getPair();
      if (cursor.getFace().getMeshLocation() == Where.Inside) {
        explored.add(cursor.getFace());
      }
    }
    cursor = cursor.getPair();
    return cursor;
  }

  private HalfEdge getBoundaryEdge(Set<Triangle> explored) {
    HalfEdge ret = null;
    Set<Triangle> inside = mesh.getInsideTriangles();
    Set<Triangle> unexplored = new HashSet<Triangle>(inside);
    unexplored.removeAll(explored);
    outside: {
      for (Triangle t : unexplored) {
        explored.add(t);
        for (Triangle n : t.getAdjacentTriangles()) {
          if (n.getMeshLocation() == Where.Outside) {
            ret = t.getCommonEdge(n);
            break outside;
          }
        }
      }
    }
    return ret;
  }

  private void expandRegion() {
    Pt here = seq.getLast();
    if (lastDrag.distance(here) > 4 && (here.getTime() - lastDrag.getTime()) > 10) {
      penPath.add(here);
      lastDrag = here;
      mesh.addPoint(here);
      draw(false);
    }
  }

  //

  //
  // public JSONObject getSaveData(Journal jnl) throws JSONException {
  // JSONObject ret = new JSONObject();
  //    
  // // ---------------------------------------------- regions
  // if (data.getRegions().size() > 0) {
  // JSONArray jsonRegions = new JSONArray();
  // for (Mesh mesh : data.getRegions()) {
  // List<Pt> meshPoints = mesh.getPoints();
  // JSONObject jsonMesh = new JSONObject();
  // JSONArray jsonMeshPoints = new JSONArray();
  // for (Pt pt : meshPoints) {
  // jsonMeshPoints.put(jnl.makeFullJsonPt(pt));
  // }
  // jsonMesh.put("points", jsonMeshPoints);
  // jsonRegions.put(jsonMesh);
  // }
  // ret.put("regions", jsonRegions);
  // }
  //    
  // return ret;
  // }
  //  
  // public void openSaveData(Journal jnl, JSONObject job) throws JSONException {
  // JSONArray jsonRegions = job.getJSONArray("regions");
  // for (int i=0; i < jsonRegions.length(); i++) {
  // JSONObject jsonMesh = jsonRegions.getJSONObject(i);
  // JSONArray jsonMeshPoints = jsonMesh.getJSONArray("points");
  // List<Pt> meshPoints = new ArrayList<Pt>();
  // for (int j=0; j < jsonMeshPoints.length(); j++) {
  // meshPoints.add(jnl.makeFullLonelyPoint(jsonMeshPoints.getJSONObject(j)));
  // }
  // mesh = new Mesh(meshPoints, 0);
  // // draw the mesh and add it to Neanderthal's regions list.
  // DrawingBuffer db = new DrawingBuffer();
  // // DrawingBufferRoutines.mesh(db, mesh, meshPoints, null);
  // DrawingBufferRoutines.dots(db, meshPoints, 2.0, 0.2, Color.BLACK, Color.RED);
  // main.addBuffer("scribble fill", db);
  // data.getRegions().add(mesh);
  // }
  // }
  //  

  private static void bug(String what) {
    Debug.out("Scribbler2", what);
  }

  @Override
  public void initialize() {
    bug("Scribbler2 initializing...");
    this.data = (Neanderthal) main.getScript("Neanderthal");
    data.addSequenceListener(this);
    possibleCornerIndexes = new ArrayList<Integer>();
    // pg = new PointGraph();
    timestamps = new Statistics();
    timestamps.setMaximumN(cornerNumThreshold);
    linelike = new Statistics();
    linelike.setMaximumN(cornerNumThreshold);

    bug("Scribbler2 initialized!");
  }

  public static Arguments getArgumentSpec() {
    Arguments args = new Arguments();
    args.setProgramName("Scribbler2: scribble to fill regions.");
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

}
