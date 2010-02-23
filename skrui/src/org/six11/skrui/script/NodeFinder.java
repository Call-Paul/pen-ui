package org.six11.skrui.script;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import org.six11.skrui.BoundedParameter;
import org.six11.skrui.Delaunay;
import org.six11.skrui.DrawingBufferRoutines;
import org.six11.skrui.Segment;
import org.six11.skrui.SkruiScript;
import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.pen.ConvexHull;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.OliveSoupEvent;
import org.six11.util.pen.OliveSoupListener;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.SequenceEvent;
import org.six11.util.pen.SequenceListener;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class NodeFinder extends SkruiScript implements SequenceListener {

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
    main.getDrawingSurface().getSoup().addSequenceListener(this);
  }

  protected void findNodeSingleStroke(SortedSet<Segment> data) {
    List<Pt> inPoints = new ArrayList<Pt>();
    for (Segment seg : data) {
      inPoints.addAll(seg.getSeq().getPoints());
    }
  }

  private void drawConvexHull(List<Pt> inPoints) {
    ConvexHull ch = new ConvexHull(inPoints);
    List<Pt> hullPoints = ch.getHullClosed();
    if (hullPoints.get(0).equals(hullPoints.get(hullPoints.size() - 1))) {
      bug("First and last hull points are the same (as they should be)");
    } else {
      bug("First and last hull points are not the same, and that makes baby Jesus cry:");
      bug(Debug.num(hullPoints.get(0)) + " != " + Debug.num(hullPoints.get(hullPoints.size() - 1)));
    }
    DrawingBuffer db = new DrawingBuffer();
    DrawingBufferRoutines.lines(db, hullPoints, Color.GREEN, 2.2);
    DrawingBufferRoutines.dots(db, hullPoints, 3, 0.3, Color.GRAY, Color.GRAY);
    DrawingBufferRoutines.dot(db, ch.getConvexCentroid(), 6.0, 1.0, Color.BLACK, Color.RED);
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

  public void handleSequenceEvent(SequenceEvent seqEvent) {
    if (seqEvent.getType() == SequenceEvent.Type.END) {
      Sequence seq = seqEvent.getSeq();
      
      Delaunay dl = new Delaunay(main);
      dl.triangulate(seq);
    }
  }

}
