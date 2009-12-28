package org.six11.slippy.example;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import javax.swing.JComponent;

import org.six11.olive.DiskEnvironment;
import org.six11.olive.DrawingBuffer;
import org.six11.slippy.Environment;
import org.six11.slippy.SlippyInterpreter;
import org.six11.slippy.SlippyMachine;
import org.six11.slippy.SlippyUtils;
import org.six11.util.Debug;
import org.six11.util.gui.ApplicationFrame;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class GraphicsExample {

  public static void main(String[] args) throws FileNotFoundException, IOException {
    Debug.useColor = false;
    if (args.length != 2) {
      bug("param 1: path to root of slippy code. param 2: name of main slippy class to run.");
      bug("$ run GraphicsExample src org.six11.slippy.example.SimpleBoxes");
      System.exit(0);
    }
    String loadPath = args[0];
    String className = args[1];
    SlippyInterpreter interp = new SlippyInterpreter();
    SlippyMachine machine = interp.getMachine();
    Environment env = new DiskEnvironment(loadPath);
    machine.setEnvironment(env);// machine.setLoadPath(loadPath);
    new BoundDrawingBuffer(interp);
    new GraphicsExample(interp, className).go();
  }

  private ApplicationFrame af;

  public GraphicsExample(SlippyInterpreter interp, String className) throws FileNotFoundException,
      IOException {
    af = new ApplicationFrame("Slippy Graphics Example");
    bug("Loading slippy file...");
    interp.runFile(interp.getMachine().getEnvironment().getLoadPath() + File.separator
        + SlippyUtils.codesetStrToFileStr(className));
    bug("Loaded slippy file.");

    JComponent foo = new JComponent() {
      public void paintComponent(Graphics g1) {
        Graphics2D g = (Graphics2D) g1;
        Collection<DrawingBuffer> buffers = BoundDrawingBuffer.buffers;
        for (DrawingBuffer buffer : buffers) {
          buffer.paste(g);
        }
      }
    };

    af.add(foo);

  }

  private static void bug(String what) {
    Debug.out("GraphicsExample", what);
  }

  public void go() {
    af.setVisible(true);
  }

}
