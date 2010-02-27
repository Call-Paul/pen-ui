package org.six11.skrui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.OliveSoupEvent;
import org.six11.util.pen.OliveSoupListener;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class PrettyPrinterSegments extends SkruiScript {

  private static final String K_SEGMENT_THICKNESS = "seg-thickness";
  private static final String K_DRAW_CORNERS = "draw-corners";
  private static final String K_DRAW_CONTROL_POINTS = "draw-control-points";
  private static final String K_CORNER_THICKNESS = "corner-thickness";
  private static final String K_CORNER_SIZE = "corner-size";
  private static final String K_CORNER_COLOR = "corner-color";
  public static final String K_SHOW_RAW_INK = "show-ink";
  public static final String K_SHOW_RAW_DOTS = "show-ink-dots";
  private static final String K_DRAW_SEGMENTS = "draw-segments";

  public static Arguments getArgumentSpec() {
    Arguments args = new Arguments();
    args.setProgramName("Pretty Printer for Segments");
    args.setDocumentationProgram("Draws rectified segments.");

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
    defs.put(K_SEGMENT_THICKNESS, new BoundedParameter.Double(K_SEGMENT_THICKNESS,
        "Segment line thickness", "The thickness of each segment line/arc.", 0.01, 100, 2.3));
    defs.put(K_DRAW_CORNERS, new BoundedParameter.Boolean(K_DRAW_CORNERS, "Draw corners or not",
        "Set to true to display the locations of segment junctions (aka corners)", false));
    defs.put(K_DRAW_SEGMENTS, new BoundedParameter.Boolean(K_DRAW_SEGMENTS,
        "Draw rectified segments or not", "Set to true to display the rectified segments", true));
    defs.put(K_DRAW_CONTROL_POINTS, new BoundedParameter.Boolean(K_DRAW_CONTROL_POINTS,
        "Draw control points or not",
        "Set to true to display the locations of spline control points", false));
    defs.put(K_SHOW_RAW_INK, new BoundedParameter.Boolean(K_SHOW_RAW_INK, "Hide raw ink or not",
        "Tells the system to draw (or not) the original ink when pretty printing.", true));
    defs.put(K_SHOW_RAW_DOTS, new BoundedParameter.Boolean(K_SHOW_RAW_DOTS,
        "Hide dots for raw ink or not",
        "Tells the system to draw (or not) dots at the original ink sample points.", false));
    defs.put(K_CORNER_THICKNESS, new BoundedParameter.Double(K_CORNER_THICKNESS,
        "Corner line thickness",
        "The thickness of the corner lines (little dots optionally drawn at junctions)", 0.01, 2.0,
        0.4));
    defs.put(K_CORNER_SIZE, new BoundedParameter.Double(K_CORNER_SIZE, "Corner radius",
        "How big to make the corner visuals, when drawn.", 0.05, 10, 4));
    defs.put(K_CORNER_COLOR, new BoundedParameter.Paint(K_CORNER_COLOR, "Corner Color",
        "Fill corner for corners, when drawn.", new java.awt.Color[] {}, Color.RED));
    return defs;
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

  @Override
  public void initialize() {
    getSoup().addSoupListener("segmentation", new OliveSoupListener() {
      @SuppressWarnings("unchecked")
      public void handleSoupEvent(OliveSoupEvent ev) {
        prettyPrint((SortedSet<Segment>) ev.getData());
      }
    });
  }

  private void prettyPrint(SortedSet<Segment> segs) {
    if (segs.size() > 0) {
      if (getParam(K_DRAW_SEGMENTS).getBoolean()) {
        DrawingBuffer db = new DrawingBuffer();
        Map<Segment.Type, Color> colors = new HashMap<Segment.Type, Color>();
        colors.put(Segment.Type.LINE, Color.BLACK);
        colors.put(Segment.Type.ARC, Color.BLACK);
        colors.put(Segment.Type.SPLINE, Color.GREEN);
        for (Segment seg : segs) {
          DrawingBufferRoutines.seg(db, seg, colors.get(seg.getBestType()));
        }
        main.getDrawingSurface().getSoup().addBuffer(db);
      }
      if (getParam(K_SHOW_RAW_INK).getBoolean() == false) {
        for (Segment seg : segs) {
          disableOriginalInput(seg.seq);
        }
      }
      if (getParam(K_SHOW_RAW_DOTS).getBoolean() == true) {
        Set<Sequence> seqs = new HashSet<Sequence>();
        for (Segment seg : segs) {
          seqs.add(seg.seq);
        }
        for (Sequence seq : seqs) {
          drawDots(seq.getPoints(), Color.LIGHT_GRAY);
        }
      }
      if (getParam(K_DRAW_CONTROL_POINTS).getBoolean()) {
        for (Segment seg : segs) {
          if (seg.getBestType() == Segment.Type.SPLINE) {
            if (seg.splineControlPoints != null && seg.splineControlPoints.size() > 0) {
              drawDots(new ArrayList<Pt>(seg.splineControlPoints), Color.YELLOW);
            }
          }
        }
      }
      if (getParam(K_DRAW_CORNERS).getBoolean()) {
        List<Pt> corners = new ArrayList<Pt>();
        for (Segment seg : segs) {
          if (!corners.contains(seg.start)) {
            corners.add(seg.start);
          }
          if (!corners.contains(seg.end)) {
            corners.add(seg.end);
          }
        }
        if (corners.size() > 0) {
          drawDots(corners, getParam(K_CORNER_COLOR).getPaint());
        }

      }
    }
  }

  private void disableOriginalInput(Sequence seq) {
    main.getDrawingSurface().getSoup().getDrawingBufferForSequence(seq).setVisible(false);
  }

  private void drawDots(List<Pt> dots, Color fill) {
    DrawingBuffer db = new DrawingBuffer();
    if (dots != null && dots.size() > 0) {
      double size = getParam(K_CORNER_SIZE).getDouble();
      double thk = getParam(K_CORNER_THICKNESS).getDouble();
      for (Pt pt : dots) {
        DrawingBufferRoutines.dot(db, pt, size, thk, Color.BLACK, fill);
      }
      main.getDrawingSurface().getSoup().addBuffer(db);
    }
  }

  private static void bug(String what) {
    Debug.out("PrettyPrinterSegments", what);
  }
}
