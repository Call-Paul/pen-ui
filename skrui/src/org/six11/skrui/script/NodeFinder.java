package org.six11.skrui.script;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import org.six11.skrui.BoundedParameter;
import org.six11.skrui.DrawingBufferRoutines;
import org.six11.skrui.Segment;
import org.six11.skrui.SkruiScript;
import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.data.Statistics;
import org.six11.util.pen.ConvexHull;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Functions;
import org.six11.util.pen.OliveSoupEvent;
import org.six11.util.pen.OliveSoupListener;
import org.six11.util.pen.Pt;
import org.six11.util.pen.SequenceEvent;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class NodeFinder extends SkruiScript {

  private static final String K_NODE_END_CLOSENESS_THRESHOLD = "node-closeness-thresh";

  // private static final String K_ANOTHER_THING = "another-thing";

  public static Arguments getArgumentSpec() {
    Arguments args = new Arguments();
    args.setProgramName("Node Finder");
    args.setDocumentationProgram("Looks for generic nodes in a box-and-arrow diagram.");

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
    defs.put(K_NODE_END_CLOSENESS_THRESHOLD, new BoundedParameter.Double(
        K_NODE_END_CLOSENESS_THRESHOLD, "Node end closeness threshold",
        "The maximum distance the endpoints of the node must be in order to be considered closed.",
        1, 100, 30));
    // defs.put(K_ANOTHER_THING, new BoundedParameter.Double(K_ANOTHER_THING, "Another Thing",
    // "This is another parameter.", 1, 5, 2.5));
    return defs;
  }

  @Override
  public void initialize() {
    bug("NodeFinder is alive!");
    getSoup().addSoupListener("segmentation", new OliveSoupListener() {
      @SuppressWarnings("unchecked")
      public void handleSoupEvent(OliveSoupEvent ev) {
        findNodeSingleStroke((SortedSet<Segment>) ev.getData());
      }
    });
  }

  protected void findNodeSingleStroke(SortedSet<Segment> data) {
    List<Pt> inPoints = new ArrayList<Pt>();
    for (Segment seg : data) {
      inPoints.addAll(seg.getSeq().getPoints());
    }
    ConvexHull ch = new ConvexHull(inPoints);
    List<Pt> hullPoints = ch.getHull();
    Statistics stats = Functions.getClosenessStatistics(inPoints, hullPoints);
    bug("---------------");
    bug("Mean distance: " + Debug.num(stats.getMean()));
    bug("Median distance: " + Debug.num(stats.getMedian()));
    bug("Max distance: " + Debug.num(stats.getMax()));
    double wholeSpread = stats.getMax() - stats.getMin();
    double medianSpread = stats.getMedian() - stats.getMin();
    double medianFraction = medianSpread / wholeSpread;
    bug("Median Fraction: " + Debug.num(medianFraction));
    DrawingBuffer db = new DrawingBuffer();
    db.setColor(Color.GREEN);
    db.setThickness(2.2);
    for (int i=0; i < hullPoints.size(); i++) {
      Pt pt = hullPoints.get(i);
      if (i == 0) {
        db.moveTo(pt.x, pt.y);
        db.down();
      } else {
        db.moveTo(pt.x, pt.y);
      }
    }
    db.up();
    main.getDrawingSurface().getSoup().addBuffer(db);    
  }

  private static void bug(String what) {
    Debug.out("NodeFinder", what);
  }

  public Map<String, BoundedParameter> initializeParameters(Arguments args) {
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
