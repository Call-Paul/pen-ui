package org.six11.skrui.data;

import java.awt.Color;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.six11.skrui.DrawnStuff;
import org.six11.skrui.DrawnThing;
import org.six11.skrui.Main;
import org.six11.skrui.SkruiScript;
import org.six11.skrui.script.Neanderthal;
import org.six11.skrui.script.Neanderthal.Certainty;
import org.six11.skrui.shape.ArcSegment;
import org.six11.skrui.shape.Dot;
import org.six11.skrui.shape.Ellipse;
import org.six11.skrui.shape.LineSegment;
import org.six11.skrui.shape.Primitive;
import org.six11.skrui.shape.Stroke;
import org.six11.util.Debug;
import org.six11.util.pen.Pt;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Journal {

  Main main;
  Map<Integer, Pt> pointMap;
  Map<Integer, Stroke> seqMap;

  Map<String, Class> validPointAttributes;
  Map<String, Class> validSequenceAttributes;

  Map<String, Class> missedPointAttributes;
  Map<String, Class> missedSequenceAttributes;

  public Journal(Main main) {
    this.main = main;
    this.pointMap = new HashMap<Integer, Pt>();
    this.seqMap = new HashMap<Integer, Stroke>();
    validSequenceAttributes = new HashMap<String, Class>();
    validSequenceAttributes.put("pen thickness", Double.class);
    validSequenceAttributes.put("pen color", Color.class);
    validSequenceAttributes.put(Neanderthal.SCRAP, Boolean.class);

    validPointAttributes = new HashMap<String, Class>();
    validPointAttributes.put("corner", Boolean.class);

    missedPointAttributes = new HashMap<String, Class>();
    missedSequenceAttributes = new HashMap<String, Class>();
  }

  public void save() throws JSONException, IOException {
    List<Stroke> sequences = main.getSequences();
    JSONObject sketch = new JSONObject();
    JSONArray jsonSequences = new JSONArray();
    sketch.put("sequences", jsonSequences);

    // [1] Persist the pen strokes
    for (Stroke seq : sequences) {
      JSONObject jsonSeq = makeJsonSequence(seq);
      if (jsonSeq != null) {
        jsonSequences.put(jsonSeq);
      }
    }
    // [2] Persist any script-related structures
    for (String scriptName : main.getScriptNames()) {
      SkruiScript script = main.getScript(scriptName);
      JSONObject scriptSave = script.getSaveData(this);
      if (scriptSave != null) {
        sketch.put(scriptName, scriptSave);
      }
    }
    // [3] Must retain stacking order of pen strokes and other structures (like filled regions)
    DrawnStuff ds = main.getDrawnStuff();
    JSONArray jsonStackOrder = new JSONArray();
    for (DrawnThing dt : ds.getDrawnThings()) {
      jsonStackOrder.put(dt.getClass().getName() + " " + dt.getId());
    }
    sketch.put("stack order", jsonStackOrder);

    // [4] Write the complete JSON string to the line.
    write(sketch);

    // Debugging output below here:
    bug("Missed " + missedSequenceAttributes.size() + " sequence attributes:");
    for (String att : missedSequenceAttributes.keySet()) {
      bug("  " + att + ": " + missedSequenceAttributes.get(att));
    }
    bug("Missed " + missedPointAttributes.size() + " point attributes:");
    for (String att : missedPointAttributes.keySet()) {
      bug("  " + att + ": " + missedPointAttributes.get(att));
    }
  }

  public void open() throws FileNotFoundException, JSONException {
    File file = main.getCurrentFile();
    JSONTokener toks = new JSONTokener(new FileReader(file));
    JSONObject sketch = new JSONObject(toks);

    // [1] Read pen strokes.
    JSONArray sequences = sketch.getJSONArray("sequences");
    for (int i = 0; i < sequences.length(); i++) {
      JSONObject jsonSeq = (JSONObject) sequences.get(i);
      Stroke seq = makeSequence(jsonSeq);
      mapSequence(seq.getId(), seq);
      main.addFinishedSequenceNoninteractively(seq);
    }

    // [2] Read script-generated structures (e.g. filled regions)
    String[] names = JSONObject.getNames(sketch);
    for (String name : names) {
      SkruiScript script = main.getScript(name);
      if (script != null) {
        script.openSaveData(this, sketch.getJSONObject(name));
      }
    }

    // [3] Ensure visual elements are drawn in correct stack order
    JSONArray jsonStackOrder = sketch.getJSONArray("stack order");
    List<String> order = new ArrayList<String>();
    for (int i = 0; i < jsonStackOrder.length(); i++) {
      String who = jsonStackOrder.getString(i);
      order.add(who);
    }
    main.getDrawnStuff().setStackOrder(order);
  }

  private void write(JSONObject job) throws IOException, JSONException {
    FileWriter fw = new FileWriter(main.getCurrentFile());
    fw.write(job.toString(2));
    fw.flush();
    fw.close();
  }

  public JSONObject makeJsonSequence(Stroke seq) throws JSONException {
    JSONObject jsonSeq = null;
    jsonSeq = new JSONObject();
    jsonSeq.put("id", seq.getId());
    JSONArray jsonPointArray = new JSONArray();
    for (int idx = 0; idx < seq.size(); idx++) {
      jsonPointArray.put(idx, makeFullJsonPt(seq.get(idx)));
    }
    jsonSeq.put("points", jsonPointArray);

    Set<String> attribs = seq.getAttributeNames();
    for (String att : attribs) {
      if (validSequenceAttributes.containsKey(att)) {
        Object attObj = makeJsonObj(seq.getAttribute(att));
        if (attObj != null) {
          jsonSeq.put(att, attObj);
        } else {
          bug("makeJsonSequence: '" + att + "': avoiding null value)");
        }
      } else if (seq.getAttribute(att) != null) {
        missedSequenceAttributes.put(att, seq.getAttribute(att).getClass());
      }
    }
    return jsonSeq;
  }

  public Stroke makeSequence(JSONObject jsonSeq) throws JSONException {
    int id = jsonSeq.getInt("id");
    Stroke seq = new Stroke(id);
    for (String name : JSONObject.getNames(jsonSeq)) {
      if (validSequenceAttributes.containsKey(name)) {
        Class cls = validSequenceAttributes.get(name);
        if (cls.equals(Double.class)) {
          seq.setAttribute(name, jsonSeq.getDouble(name));
        } else if (cls.equals(Boolean.class)) {
          seq.setAttribute(name, jsonSeq.getBoolean(name));
        } else if (cls.equals(Color.class)) {
          Color color = makeColor(jsonSeq.getJSONObject(name));
          seq.setAttribute(name, color);
        } else if (cls.equals(Integer.class)) {
          seq.setAttribute(name, jsonSeq.getInt(name));
        } else {
          warn("Unable to deserialize sequence attribute '" + name + "' of type: " + cls);
        }
      }
    }

    JSONArray points = jsonSeq.getJSONArray("points");
    for (int i = 0; i < points.length(); i++) {
      Pt pt = makeFullPoint((JSONObject) points.get(i), seq);
      seq.add(pt);
    }
    return seq;
  }

  public void mapSequence(int id, Stroke seq) {
    seqMap.put(id, seq);
  }

  public Pt makeFullPoint(JSONObject jsonPt, Stroke seq) throws JSONException {
    Pt ret = makeFullLonelyPoint(jsonPt);
    if (seq != null) {
      ret.setAttribute(Neanderthal.MAIN_SEQUENCE, seq);
      if (pointMap.containsKey(ret.getID())) {
        warn("Created duplicate point: " + Debug.num(ret) + " (id: " + ret.getID() + ")");
      }
      pointMap.put(ret.getID(), ret);
    } else {
      warn("Point " + ret.getID() + " does not have a sequence!");
    }
    return ret;
  }

  public Pt makeFullLonelyPoint(JSONObject jsonPt) throws JSONException {
    JSONArray ptData = jsonPt.getJSONArray("pt");
    int id = ptData.getInt(0);
    double x = ptData.getDouble(1);
    double y = ptData.getDouble(2);
    long t = ptData.getLong(3);
    Pt ret = new Pt(id, x, y, t);
    JSONObject attr = jsonPt.optJSONObject("attribs");
    if (attr != null) {
      String[] names = JSONObject.getNames(attr);
      if (names != null) {
        for (String name : names) {
          if (validPointAttributes.containsKey(name)) {
            Class cls = validPointAttributes.get(name);
            if (cls.equals(Double.class)) {
              ret.setAttribute(name, attr.getDouble(name));
            } else if (cls.equals(Boolean.class)) {
              ret.setAttribute(name, attr.getBoolean(name));
            } else if (cls.equals(Color.class)) {
              Color color = makeColor(attr.getJSONObject(name));
              ret.setAttribute(name, color);
            } else {
              warn("Unable to deserialize point attribute '" + name + "' of type: " + cls);
            }
          }
        }
      }
    }
    return ret;
  }

  public static Color makeColor(JSONObject jsonColor) throws JSONException {
    int red = jsonColor.getInt("red");
    int green = jsonColor.getInt("green");
    int blue = jsonColor.getInt("blue");
    int alpha = jsonColor.getInt("alpha");
    return new Color(red, green, blue, alpha);
  }

  public Primitive makePrimitive(JSONArray jsonPrim) throws JSONException {
    Primitive ret = null;
    // 0=primID 1=seqID 2=startIdx 3=endIdx 4=cert 5=type
    int primID = jsonPrim.getInt(0);
    int sequenceID = jsonPrim.getInt(1);
    Stroke seq = findSequence(sequenceID);
    int startIdx = jsonPrim.getInt(2);
    int endIdx = jsonPrim.getInt(3);
    Certainty cert = makeCertainty(jsonPrim.getString(4));
    String type = jsonPrim.getString(5);
    if ("LineSegment".equals(type)) {
      ret = new LineSegment(primID, seq, startIdx, endIdx, cert);
    } else if ("ArcSegment".equals(type)) {
      ret = new ArcSegment(primID, seq, startIdx, endIdx, cert);
    } else if ("Dot".equals(type)) {
      ret = new Dot(primID, seq, cert);
    } else if ("Ellipse".equals(type)) {
      ret = new Ellipse(primID, seq, cert);
    } else {
      bug("Unknown primitive type: " + type);
    }
    return ret;
  }

  private Certainty makeCertainty(String str) {
    Certainty ret = Certainty.Unknown;
    if ("Yes".equals(str)) {
      ret = Certainty.Yes;
    } else if ("No".equals(str)) {
      ret = Certainty.No;
    } else if ("Maybe".equals(str)) {
      ret = Certainty.Maybe;
    }
    return ret;
  }

  public Object makeJsonObj(Object value) throws JSONException {
    Object ret = null;
    if (value instanceof Double || //
        value instanceof Boolean || //
        value instanceof Integer || //
        value instanceof Long || //
        value instanceof Short) {
      ret = value;
    } else if (value instanceof Color) {
      JSONObject job = new JSONObject();
      Color c = (Color) value;
      job.put("red", c.getRed());
      job.put("green", c.getGreen());
      job.put("blue", c.getBlue());
      job.put("alpha", c.getAlpha());
      ret = job;
    } else if (value instanceof Primitive) {
      // Sequence seq, int startIdx, int endIdx, Certainty cert
      JSONArray jarr = new JSONArray();
      Primitive prim = (Primitive) value;
      jarr.put(prim.getId()); // primID seqID startIdx endIdx cert type
      jarr.put(prim.getSeq().getId());
      jarr.put(prim.getStartIdx());
      jarr.put(prim.getEndIdx());
      jarr.put(prim.getCert().toString()); // Yes, Maybe, No
      String type = null;
      if (prim instanceof LineSegment) {
        type = "LineSegment";
      } else if (prim instanceof ArcSegment) {
        type = "ArcSegment";
      } else if (prim instanceof Dot) {
        type = "Dot";
      } else if (prim instanceof Ellipse) {
        type = "Ellipse";
      } else {
        bug("You missed the primitive type: " + prim.getClass());
      }
      if (type != null) {
        jarr.put(type);
      }
      ret = jarr;
    } else {
      bug("makeJsonObj: Unknown object type: " + value.getClass() + ". This will screw things up.");
    }
    return ret;
  }

  public JSONObject makeFullJsonPt(Pt pt) throws JSONException {
    JSONObject ret = new JSONObject();
    JSONArray ptData = new JSONArray();
    // ret.put("id", pt.getID());
    // ret.put("x", pt.getX());
    // ret.put("y", pt.getY());
    // ret.put("t", pt.getTime());
    ptData.put(pt.getID());
    ptData.put(pt.getX());
    ptData.put(pt.getY());
    ptData.put(pt.getTime());
    ret.put("pt", ptData);

    Map<String, Object> attribs = pt.getAttribs();
    JSONObject attribObj = new JSONObject();
    int attribCount = 0;
    for (String attrName : attribs.keySet()) {
      if (validPointAttributes.containsKey(attrName)) {
        Object val = makeJsonObj(attribs.get(attrName));
        if (val != null) {
          attribCount++;
          attribObj.put(attrName, val);
        } else {
          bug("avoiding point attribute '" + attrName + "'. is this bad?");
        }
      } else {
        missedPointAttributes.put(attrName, attribs.get(attrName).getClass());
      }
    }
    if (attribCount > 0) {
      ret.put("attribs", attribObj);
    }
    return ret;
  }

  private static void bug(String what) {
    Debug.out("Journal", what);
  }

  private static void warn(String what) {
    System.out.println("*** Warning ***\t" + what);
  }

  public Stroke findSequence(int seqId) {
    Stroke ret = seqMap.get(seqId);
    if (ret == null) {
      bug("Could not find sequence: " + seqId);
    }
    return ret;
  }

  public Pt findPoint(int ptId) {
    Pt ret = pointMap.get(ptId);
    if (ret == null) {
      bug("Could not find point: " + ptId);
    }
    return ret;
  }

}
