package org.six11.skrui.script;

import java.awt.Color;
import java.awt.Cursor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.six11.skrui.BoundedParameter;
import org.six11.skrui.DrawingBufferRoutines;
import org.six11.skrui.DrawnThing;
import org.six11.skrui.SkruiScript;
import org.six11.skrui.data.Journal;
import org.six11.skrui.mesh.HalfEdge;
import org.six11.skrui.mesh.Mesh;
import org.six11.skrui.mesh.Triangle;
import org.six11.skrui.mesh.Where;
import org.six11.skrui.shape.Region;
import org.six11.skrui.shape.Stroke;
import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.data.Statistics;
import org.six11.util.pen.*;

public class Scribbler extends SkruiScript implements SequenceListener {

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
  Stroke seq;
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

  private double previousThickness; // during a scribble-fill, this holds the pen thickness

  public void handleSequenceEvent(SequenceEvent seqEvent) {
    SequenceEvent.Type type = seqEvent.getType();
    Stroke seq = (Stroke) seqEvent.getSeq();
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

  public void sendDown(Stroke seq) {
    // clear/set the gesture-detection variables
    this.seq = seq;
    lastExaminedIndex = -1;
    possibleCornerIndexes.clear();
    linelike.clear();

    // clear the mesh-forming variables
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
    for (Pt hp : hull.getHullClosed()) {
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
          seq.get(mid).setDouble("Scribbler corner value", angle);
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
        if (mostRecent.getDouble("Scribbler corner value") > me.getDouble("Scribbler corner value")) {
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
      main.getDrawnStuff().addNamedBuffer("scribble fill", db, true);
    }
  }

  private void drawFinalMesh() {
    data.forget(seq, false);
    main.getDrawnStuff().removeNamedBuffer("scribble fill");
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
      Region region = new Region(boundary, main.getPenColor());
      addRegion(region);
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

  private List<Region> getRegions() {
    List<Region> ret = new ArrayList<Region>();
    List<DrawnThing> manyThings = main.getDrawnStuff().getDrawnThings();
    for (DrawnThing dt : manyThings) {
      if (dt instanceof Region) {
        ret.add((Region) dt);
      }
    }
    return ret;
  }

  public JSONObject getSaveData(Journal jnl) throws JSONException {
    JSONObject ret = new JSONObject();
    // ---------------------------------------------- regions
    List<Region> regions = getRegions();
    if (regions.size() > 0) {
      JSONArray jsonRegions = new JSONArray();
      for (Region reg : regions) {
        JSONObject jsonRegion = new JSONObject();
        JSONArray jsonRegionPoints = new JSONArray();
        for (Pt pt : reg.getPoints()) {
          jsonRegionPoints.put(jnl.makeFullJsonPt(pt));
        }
        jsonRegion.put("id", reg.getId());
        jsonRegion.put("points", jsonRegionPoints);
        jsonRegion.put("color", jnl.makeJsonObj(reg.getColor()));
        jsonRegions.put(jsonRegion);
      }
      ret.put("regions", jsonRegions);
    }

    return ret;
  }

  public void openSaveData(Journal jnl, JSONObject job) throws JSONException {
    JSONArray jsonRegions = job.optJSONArray("regions");
    if (jsonRegions != null) {
      for (int i = 0; i < jsonRegions.length(); i++) {
        JSONObject jsonRegion = jsonRegions.getJSONObject(i);
        JSONArray jsonRegionPoints = jsonRegion.getJSONArray("points");
        List<Pt> regionPoints = new ArrayList<Pt>();
        for (int j = 0; j < jsonRegionPoints.length(); j++) {
          regionPoints.add(jnl.makeFullLonelyPoint(jsonRegionPoints.getJSONObject(j)));
        }
        Color color = Journal.makeColor(jsonRegion.getJSONObject("color"));
        int id = jsonRegion.getInt("id");
        Region region = new Region(id, regionPoints, color);
        addRegion(region);
      }
    }
  }
  
  private void addRegion(Region region) {
    DrawingBuffer db = new DrawingBuffer();
    DrawingBufferRoutines.region(db, region);
    region.setDrawingBuffer(db);
    main.getDrawnStuff().add(region);
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

}
