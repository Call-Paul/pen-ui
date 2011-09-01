package org.six11.sf;

import org.six11.util.gui.ApplicationFrame;
import org.six11.util.layout.FrontEnd;
import org.six11.util.pen.PenEvent;
import org.six11.util.pen.PenListener;
import org.six11.util.pen.Sequence;

import static org.six11.util.Debug.bug;
import static org.six11.util.layout.FrontEnd.*;

/**
 * A self-contained editor instance.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class SkruiFabEditor implements PenListener {

  public static final String UNSTRUCTURED_INK = "unstructured ink";

  Main main;
  DrawingBufferLayers layers;
  SketchBook model;

  public SkruiFabEditor(Main m) {
    this.main = m;
    ApplicationFrame af = new ApplicationFrame("SkruiFab (started " + m.varStr("dateString")
        + " at " + m.varStr("timeString") + ")");
    af.setSize(600, 400);
    model = new SketchBook();
    layers = new DrawingBufferLayers(model);
    layers.addPenListener(this);
    model.setLayers(layers);

    ScrapGrid grid = new ScrapGrid();
    CutfilePane cutfile = new CutfilePane();

    FrontEnd fe = new FrontEnd();
    fe.add(layers, "layers");
    fe.add(grid, "grid");
    fe.add(cutfile, "cutfile");
    fe.addRule(ROOT, N, "layers", N);
    fe.addRule(ROOT, W, "layers", W);
    fe.addRule(ROOT, S, "layers", S);
    fe.addRule(ROOT, N, "grid", N);
    fe.addRule(ROOT, E, "grid", E);
    fe.addRule(ROOT, E, "cutfile", E);
    fe.addRule(ROOT, S, "cutfile", S);
    fe.addRule("cutfile", N, "grid", S);
    fe.addRule("cutfile", W, "layers", E);
    fe.addRule("cutfile", W, "grid", W);
    af.add(fe);
    af.center();
    af.setVisible(true);
  }

  public void handlePenEvent(PenEvent ev) {
    // see docs/ink-processing-highlevel.pdf for a diagram of this
    switch (ev.getType()) {
      case Down:
        handleDown(ev);
        break;
      case Drag:
        handleDrag(ev);
        break;
      case Flow:
        handleFlow(ev);
        break;
      case Tap:
        handleTap(ev);
        break;
      case Idle:
        handleIdle(ev);
        break;
      default:
        bug("Unknown pen event type received: " + ev.getType());
    }
  }

  private void handleDown(PenEvent ev) {
    model.startScribble(ev.getPt());
  }

  private void handleDrag(PenEvent ev) {
    model.addScribble(ev.getPt());
  }

  private void handleFlow(PenEvent ev) {
    bug("Flow");
  }

  private void handleTap(PenEvent ev) {
    bug("Tap");
  }

  private void handleIdle(PenEvent ev) {
    Sequence seq = model.endScribble(ev.getPt());
    
  }

}
