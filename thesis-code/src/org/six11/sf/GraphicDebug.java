package org.six11.sf;

import java.awt.Color;
import java.util.List;

import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import static org.six11.util.Debug.bug;

public class GraphicDebug {

  private DrawingBufferLayers layers;
  // these are the keyboard-activated layers, mostly for debugging.
  public static final String DB_UNSTRUCTURED_INK = "0";
  public static final String DB_RECENT_INK = "1";
  public static final String DB_JUNCTION_LAYER = "2";
  public static final String DB_DOT_LAYER = "3";
  public static final String DB_SEGMENT_LAYER = "4";
  public static final String DB_LATCH_LAYER = "5";
  public static final String DB_COMPLETE_LAYER = "6";
  public static final String DB_HIGHLIGHTS = "7";
  public static final String DB_SELECTION = "8";
  public static final String DB_COPY_LAYER = "9";
  public static final String DB_STRUCTURED_INK = "structured ink";

  public GraphicDebug(DrawingBufferLayers layers) {
    this.layers = layers;
  }

  public void drawJunctions(Sequence seq) {
    DrawingBuffer db = layers.getLayer(GraphicDebug.DB_JUNCTION_LAYER);
    List<Integer> juncts = (List<Integer>) seq.getAttribute(CornerFinder.SEGMENT_JUNCTIONS);
    for (int idx : juncts) {
      Pt where = seq.get(idx);
      DrawingBufferRoutines.dot(db, where, 3.0, 0.5, Color.BLACK, Color.RED);
    }
    layers.repaint();
  }

  public void drawSegments(List<Segment> segs) {
    DrawingBuffer db = layers.getLayer(GraphicDebug.DB_SEGMENT_LAYER);
    drawSegments(db, segs, null);
  }

  /**
   * Draw some segments with an optional color. If the color is null, default colors are chosen
   * based on segment type.
   * 
   * @param allSegments
   * @param preferredColor
   */
  public void drawSegments(DrawingBuffer db, List<Segment> segments, Color preferredColor) {
    Color darkGreen = Color.green.darker();
    Color darkBlue = Color.blue.darker();
    Color darkRed = Color.red.darker();
    Color pc = preferredColor;
    for (Segment seg : segments) {
      if (seg.getType() == Segment.Type.Line) {
        DrawingBufferRoutines.line(db, seg.asLine(), pc == null ? darkGreen : pc, 2.0);
      } else if (seg.getType() == Segment.Type.Curve) {
        DrawingBufferRoutines.drawShape(db, seg.asSpline(), pc == null ? darkBlue : pc, 2.0);
      } else if (seg.getType() == Segment.Type.EllipticalArc) {
        DrawingBufferRoutines.drawShape(db, seg.asPolyline(), pc == null ? darkRed : pc, 2.0);
      } else {
        bug("Unknown segment type in drawSegments: " + seg.getType());
      }
    }
  }

  public void whackLayerVisibility(String name) {
    DrawingBuffer db = layers.getLayer(name);
    db.setVisible(!db.isVisible());
    bug((db.isVisible() ? "Showing" : "Hiding") + " layer " + db.getHumanReadableName());
    layers.repaint();
  }

  public void fillShape(List<Pt> points, Color color) {
    DrawingBuffer db = layers.getLayer(DB_HIGHLIGHTS);
    DrawingBufferRoutines.fillShape(db, points, color, 0);
  }

  public void ghostlyOutlineShape(DrawingBuffer db, List<Pt> points, Color ghostColor) {
    DrawingBufferRoutines.drawShape(db, points, ghostColor, GestureController.GESTURE_AOE_DISTANCE / 2);
  }

}
