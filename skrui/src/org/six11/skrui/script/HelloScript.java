package org.six11.skrui.script;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

import org.six11.skrui.BoundedParameter;
import org.six11.skrui.DrawingBufferRoutines;
import org.six11.skrui.DrawingScript;
import org.six11.util.Debug;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.SequenceEvent;
import org.six11.util.pen.SequenceListener;

/**
 * This is a 'hello world' implementation of DrawingScript. To use it, just mention it on the Main
 * command line.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class HelloScript extends DrawingScript implements SequenceListener {

  @Override
  public void initialize() {
    bug("HelloScript is alive!");
    main.getDrawingSurface().getSoup().addSequenceListener(this);
  }

  private static void bug(String what) {
    Debug.out("HelloScript", what);
  }

  public void handleSequenceEvent(SequenceEvent seqEvent) {
    if (seqEvent.getType() == SequenceEvent.Type.END) {
      bug("Sequence received.");
      DrawingBuffer db = new DrawingBuffer();
      DrawingBufferRoutines.line(db, seqEvent.getSeq().getFirst(), seqEvent.getSeq().getLast(),
          Color.BLUE);
      main.getDrawingSurface().getSoup().addBuffer(db);
    }
  }

  public Map<String, BoundedParameter> getParameters() {
    return new HashMap<String, BoundedParameter>();
  }
}
