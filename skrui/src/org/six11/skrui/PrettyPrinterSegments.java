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
import org.six11.util.gui.Colors;
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
public class PrettyPrinterSegments extends DrawingScript {

  private static final String K_SEGMENT_THICKNESS = "seg-thickness";
  private static final String K_DRAW_CORNERS = "draw-corners";
  private static final String K_CORNER_THICKNESS = "corner-thickness";
  private static final String K_CORNER_SIZE = "corner-size";
  private static final String K_CORNER_COLOR = "corner-color";

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
      if (args.hasValue(k)) {
        params.get(k).setDouble(Double.parseDouble(args.getValue(k)));
        bug("Set " + params.get(k).getHumanReadableName() + " to "
            + Debug.num(params.get(k).getDouble()));
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
      DrawingBuffer db = new DrawingBuffer();
      Set<Sequence> involvedSequences = new HashSet<Sequence>();

      for (Segment seg : segs) {
        DrawingBufferRoutines.seg(db, seg, Color.BLACK, getParam(CornerFinder.K_LINE_RATIO)
            .getDouble());
        involvedSequences.add(seg.seq);
      }
      main.getDrawingSurface().getSoup().addBuffer(db);

      for (Sequence seq : involvedSequences) {
        bug("Disabling visual for a sequence...");
        disableOriginalInput(seq);
        List<Pt> corners = new ArrayList<Pt>();
        for (Pt pt : seq) {
          if (pt.getBoolean("corner")) {
            corners.add(pt);
          }
        }
        drawCorners(corners, seq);
      }

    }
  }

  private void disableOriginalInput(Sequence seq) {
    main.getDrawingSurface().getSoup().getDrawingBufferForSequence(seq).setVisible(false);
  }

  private void drawCorners(List<Pt> corners, Sequence seq) {
    if (corners != null && corners.size() > 0) {
      DrawingBuffer db = new DrawingBuffer();
      Color cornerColor = new Color(255, 0, 0, 127);
      Color mergedColor = new Color(0, 0, 255, 127);
      Color normalColor = new Color(255, 255, 255, 127);
      Color curvyColor = Colors.makeAlpha(Color.GREEN, 0.6f);
      Color slowColor = Colors.makeAlpha(Color.YELLOW, 0.6f);
      Color slowAndCurvyColor = Colors.makeAlpha(Color.MAGENTA, 0.6f);
      boolean addedSomething = false;
      for (Pt pt : seq) {
        boolean specialPoint = false;
        Color c = cornerColor;
        if (pt.getBoolean("corner")) {
          specialPoint = true;
        } else if (pt.getBoolean("removed")) {
          c = mergedColor;
          specialPoint = true;
        } else if (pt.getBoolean("slow") && pt.getBoolean("curvy")) {
          c = slowAndCurvyColor;
        } else if (pt.getBoolean("curvy")) {
          c = curvyColor;
        } else if (pt.getBoolean("slow")) {
          c = slowColor;
        } else {
          c = normalColor;
        }

        if (specialPoint) {
          DrawingBufferRoutines.dot(db, pt, 4.0, 0.4, Color.BLACK, c);
          addedSomething = true;
        } else {
          // DrawingBufferRoutines.rect(db, pt, 3.0, 3.0, Color.BLACK, c, 0.3);
        }
      }
      if (addedSomething) {
        main.getDrawingSurface().getSoup().addBuffer(db);
      }
    }
  }

  private static void bug(String what) {
    Debug.out("PrettyPrinterSegments", what);
  }
}
