package org.six11.sf;

import static org.six11.util.Debug.bug;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.six11.sf.constr.UserConstraint;
import org.six11.util.Debug;
import org.six11.util.pen.Pt;
import org.six11.util.solve.Constraint;
import org.six11.util.solve.JsonIO;

public class Snapshot {

  private static int ID_COUNTER = 0;
  private final int id = ID_COUNTER++;

  private SketchBook model;
  private JSONObject top;
  private BufferedImage img;
  
  public String toString() {
    return "Snapshot " + id;
  }

  public Snapshot(SketchBook model) {
//    Debug.stacktrace("Saving model!   --   Saving model!   --   Saving model!   --   Saving model!   --   Saving model!   --   ", 5);
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

    //  (save**)  layers.clearAllBuffers(); // ** save image data to repaint a preview of it.
    //  (save)    editor.getGrid().clear();
    //  (save)    editor.getCutfilePane().clear();

    //  (ignore)    derivedGuides.clear();
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
      
      
      //
      // user constraints
      //
      Collection<UserConstraint> userConstraints = model.getUserConstraints();
      JSONArray userConstraintsArr = new JSONArray();
      for (UserConstraint uc : userConstraints) {
        userConstraintsArr.put(uc.toJson());
      }
      top.put("userConstraints", userConstraintsArr);
      
      //
      // stencils
      //
      Set<Stencil> stencils = model.getStencils();
      JSONArray stencilArr = new JSONArray();
      for (Stencil s : stencils) {
        stencilArr.put(s.toJson());
      }
      top.put("stencils", stencilArr);
      
      //
      // selected stencils
      //
      Set<Stencil> selStencils = model.getSelectedStencils();
      JSONArray selStencilArr = new JSONArray();
      for (Stencil s : selStencils) {
        selStencilArr.put(s.getId());
      }
      top.put("selectedStencils", selStencilArr);
      
      //
      // selected segments
      //
      Set<Segment> selSegs = model.getSelectedSegments();
      JSONArray selSegArr = new JSONArray();
      for (Segment s : selSegs) {
        selSegArr.put(s.getId());
      }
      top.put("selectedSegments", selSegArr);
      
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
      
      //
      // user constraints
      //
      JSONArray ucArray = top.getJSONArray("userConstraints");
      for (int i=0; i < ucArray.length(); i++) {
        JSONObject ucObj = ucArray.getJSONObject(i);
        UserConstraint uc = UserConstraint.fromJson(model, ucObj);
        model.addUserConstraint(uc);
      }
      
      //
      // stencils
      //
      JSONArray stencilArr = top.getJSONArray("stencils");
      for (int i=0; i < stencilArr.length(); i++) {
        JSONObject stencilObj = stencilArr.getJSONObject(i);
        Stencil stencil = new Stencil(model, stencilObj);
        model.addStencil(stencil);
      }
      
      //
      // selectedStencils
      //
      JSONArray selStencilArr = top.getJSONArray("selectedStencils");
      Set<Stencil> sel = new HashSet<Stencil>();
      for (int i=0; i < selStencilArr.length(); i++) {
        int stencilID = selStencilArr.getInt(i);
        sel.add(model.getStencil(stencilID));
      }
      model.setSelectedStencils(sel);
      
      //
      // selectedSegments
      //
      JSONArray selSegArr = top.getJSONArray("selectedSegments");
      Set<Segment> selSegs = new HashSet<Segment>();
      for (int i=0; i < selSegArr.length(); i++) {
        int segID = selSegArr.getInt(i);
        selSegs.add(model.getSegment(segID));
      }
      model.setSelectedSegments(selSegs);
      
      
      
    } catch (JSONException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return ok;
  }

  public BufferedImage getPreview() {
    return img;
  }

  public int getID() {
    return id;
  }

  public JSONObject getJSONRoot() {
    return top;
  }
}
