package org.six11.slippy.example;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.GeneralPath;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

import org.six11.olive.DiskEnvironment;
import org.six11.olive.DrawingBuffer;
import org.six11.olive.OliveMouseThing;
import org.six11.olive.OliveSoup;
import org.six11.slippy.*;
import org.six11.util.Debug;
import org.six11.util.gui.ApplicationFrame;
import org.six11.util.gui.Components;
import org.six11.util.gui.Strokes;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class PenIOExample {

  private List<DrawingBuffer> buffers;

  public static void main(String[] args) throws FileNotFoundException, IOException {
    Debug.useColor = false;
    if (args.length != 1) {
      bug("param 1: path to root of slippy code.");
      bug("$ ./run org.six11.slippy.example.PenIOExample slippy");
      System.exit(0);
    }
    String loadPath = args[0];
    SlippyInterpreter interp = new SlippyInterpreter();
    SlippyMachine machine = interp.getMachine();
    Environment env = new DiskEnvironment(loadPath);
    machine.setEnvironment(env);

    new org.six11.olive.BoundDrawingBuffer(interp);
    new PenIOExample(interp).go();
  }

  private static void bug(String what) {
    Debug.out("PenIOExample", what);
  }

  private ApplicationFrame af;
  private JComponent canvas;
  private OliveSoup soup;
  private GeneralPath currentSeq;
  private int lastCurrentSequenceIdx;

  public PenIOExample(SlippyInterpreter interp) throws FileNotFoundException, IOException {
    this.buffers = new ArrayList<DrawingBuffer>();
    af = new ApplicationFrame("Slippy Pen Input/Output Example");
    
    // Slippy code can use the 'signal' function to pass events and arbitrary Thing objects
    // back into Java. This is done using a message bus.
    interp.getMachine().setMessageBus(new PenIOMessageBus());
    
    canvas = new JComponent() {
      public void paintComponent(Graphics g1) {
        Graphics2D g = (Graphics2D) g1;
        for (DrawingBuffer buffer : buffers) {
          buffer.paste(g);
        }
        if (currentSeq != null) {
          Components.antialias(g);
          g.setColor(Color.black);
          g.setStroke(Strokes.get((float) 1.4));
          g.draw(currentSeq);
        }
      }
    };
    Thing t = interp.importFromFile("OliveSoup", "org.six11.olive");
    SlippyObjectType type = new SlippyObjectType(t, interp);
    soup = new OliveSoup(type.make(new ArrayList<Object>()));
    OliveMouseThing mt = new OliveMouseThing(soup);
    canvas.addMouseListener(mt);
    canvas.addMouseMotionListener(mt);
    af.add(canvas);
  }

  private void drawCurrentSequence(Thing.Array arr) {
    for (int i = lastCurrentSequenceIdx; i < arr.size(); i++) {
      Thing t = arr.getSlot(i).getValue();
      if (t.type == Thing.Type.Instance) {
        Thing.Instance inst = (Thing.Instance) t;
        Thing.Num xThing = (Thing.Num) ((Thing.Variable)inst.getMember("x")).getValue();
        Thing.Num yThing = (Thing.Num) ((Thing.Variable)inst.getMember("y")).getValue();
        float x = xThing.getFloatValue();
        float y = yThing.getFloatValue();
        if (i == 0) {
          currentSeq = new GeneralPath();
          currentSeq.moveTo(x, y);
        } else {
          currentSeq.lineTo(x, y);
        }
      }
      lastCurrentSequenceIdx = i;
    }
  }

  private void forgetCurrentSequence() {
    lastCurrentSequenceIdx = 0;
    currentSeq = null;
  }
  
  public void go() {
    af.setVisible(true);
  }

  private class PenIOMessageBus extends MessageBus {

    public void relay(List<Thing> paramValues, SymbolTable context) {
      for (Thing t : paramValues) {
        if (t.type == Thing.Type.JavaObject && ((Thing.JavaObject) t).obj instanceof DrawingBuffer) {
          forgetCurrentSequence();
          DrawingBuffer buf = (DrawingBuffer) ((Thing.JavaObject) t).obj;
          if (!buffers.contains(buf)) {
            buffers.add(buf);
          }
          canvas.repaint();
        } else if (t.type == Thing.Type.Array) {
          drawCurrentSequence((Thing.Array) t);
          canvas.repaint();
        }
      }
    }
  }
}
