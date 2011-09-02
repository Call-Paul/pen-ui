package org.six11.sf;

import java.awt.Color;
import java.util.List;

import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

public class GraphicDebug {

  private DrawingBufferLayers layers;
  
  public GraphicDebug(DrawingBufferLayers layers) {
    this.layers = layers;
  }

  public void drawJunctions(Sequence seq) {
    DrawingBuffer db = layers.getLayer(CornerFinder.DB_JUNCTION_LAYER);
    List<Integer> juncts = (List<Integer>) seq.getAttribute(CornerFinder.SEGMENT_JUNCTIONS);
    for (int idx : juncts) {
      Pt where = seq.get(idx);
      DrawingBufferRoutines.dot(db, where, 3.0, 0.5, Color.BLACK, Color.RED);
    }
    layers.repaint();
  }

}
