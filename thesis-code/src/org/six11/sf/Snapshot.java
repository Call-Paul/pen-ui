package org.six11.sf;

import static org.six11.util.Debug.bug;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.six11.util.pen.Pt;
import org.six11.util.solve.Constraint;
import org.six11.util.solve.JsonIO;

public class Snapshot {

  private static int ID_COUNTER = 0;
  private final int id = ID_COUNTER++;

  private SketchBook model;
  private JSONObject top;
  private Image img;
  
  public String toString() {
    return "Snapshot " + id;
  }

  public Snapshot(SketchBook model) {
//    bug("Saving model!");
    this.model = model;
    img = model.getLayers().getScreenShot();
//    bug("got image. it is : " + img);
    // the model's clearAll function does the following. use it as a plan to save things.

    //  (save)    constraint solver : points
    //  (save)    geometry
    //  (save)    constraint solver : primitive constraints
    //  (save)    user constraints

    //  (save)    stencils
    //  (save)    clearSelectedStencils();
    //  (save)    clearSelectedSegments();
    //  (save)    guidePoints.clear();
    //  (save)    activeGuidePoints.clear();
    //  (save)    derivedGuides.clear();
    //  (save**)  layers.clearAllBuffers(); // ** save image data to repaint a preview of it.
    //  (save)    editor.getGrid().clear();
    //  (save)    editor.getCutfilePane().clear();

    //  (ignore)  actions.clear();
    //  (ignore)  redoActions.clear();
    //  (ignore)  clearInk();
    //  (ignore)  layers.repaint();
    //  (ignore)  layers.clearScribble();

    try {
      //
      // make a 'top' level object that defines the snapshot 
      JsonIO io = new JsonIO();
      top = new JSONObject();

      //
      // constraint solver : points
      //
      // write each point along with the given list of attributes ("name", "pinned", maybe more in the future)
      JSONArray points = io.write(model.getConstraints().getPoints(), "name", "pinned");
      top.put("points", points);

      //
      // constraint solver : primitive constraints
      //
      JSONArray constraints = io.write(model.getConstraints().getConstraints());
      top.put("constraints", constraints);

      // 
      // geometry
      //
      Set<Segment> geom = model.getGeometry();
      JSONArray geomArr = new JSONArray();
      for (Segment seg : geom) {
        geomArr.put(seg.toJson());
      }
      top.put("geometry", geomArr);
//      System.out.println(top.toString(3));
    } catch (JSONException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  public boolean load() {
    boolean ok = true;
    JsonIO io = new JsonIO();
    model.clearAll();
    try {
      //
      // constraint solver : points
      //
      JSONArray pointArray = top.getJSONArray("points");
      List<Pt> points = io.readPoints(pointArray, "name", "pinned");
      for (Pt pt : points) {
        model.getConstraints().addPoint(pt);
      }

      //
      // constraint solver : primitive constraints
      //
      JSONArray constraintArray = top.getJSONArray("constraints");
      List<Constraint> constraints = io.readConstraints(constraintArray, model.getConstraints()
          .getVars());
      for (Constraint c : constraints) {
        model.getConstraints().addConstraint(c);
      }

      // 
      // geometry
      //
      JSONArray geomArray = top.getJSONArray("geometry");
      for (int i=0; i < geomArray.length(); i++) {
        JSONObject segObj = geomArray.getJSONObject(i);
        Segment seg = SnapshotMachine.load(segObj, model);
        model.addGeometry(seg);
      }
      
    } catch (JSONException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return ok;
  }

  public Image getPreview() {
    return img;
  }

  public int getID() {
    return id;
  }

  public JSONObject getJSONRoot() {
    return top;
  }
}
