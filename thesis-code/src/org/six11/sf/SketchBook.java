package org.six11.sf;

import java.util.ArrayList;
import java.util.List;

import org.six11.util.data.Lists;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

import static org.six11.util.Debug.bug;
import org.six11.util.Debug;

public class SketchBook {

  List<Sequence> scribbles;
  private DrawingBufferLayers layers;

  public SketchBook() {
    this.scribbles = new ArrayList<Sequence>();
  }
  
  public Sequence startScribble(Pt pt) {
    Sequence scrib = new Sequence();
    scrib.add(pt);
    scribbles.add(scrib);
    return scrib;
  }

  public Sequence addScribble(Pt pt) {
    Sequence scrib = (Sequence) Lists.getLast(scribbles);
    if (!scrib.getLast().isSameLocation(pt)) {
      scrib.add(pt);
    } else {
      bug("Avoid duplicate point in scribble");
    }
    return scrib;
  }

  public Sequence endScribble(Pt pt) {
    DrawingBuffer buf = layers.getLayer(SkruiFabEditor.UNSTRUCTURED_INK);
    Sequence scrib = (Sequence) Lists.getLast(scribbles);
    bug("Finished scribble " + scribbles.size() + " with " + scrib.size() + " points");
    DrawingBufferRoutines.drawShape(buf, scrib.getPoints(), DrawingBufferLayers.DEFAULT_COLOR,
        DrawingBufferLayers.DEFAULT_THICKNESS);
    layers.repaint();
    return scrib;
  }

  public void setLayers(DrawingBufferLayers layers) {
    this.layers = layers;
  }

}
