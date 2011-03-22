package org.six11.colorpicker;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JWindow;

import org.six11.util.Debug;
import org.six11.util.gui.ApplicationFrame;
import org.six11.util.gui.Components;

/**
 * 
 **/
public class Main {
  public static void main(String[] args) {
    ApplicationFrame af = new ApplicationFrame("Sample Sketching Area");
    af.setSize(800, 600);
    af.center();
    af.setVisible(true);

    JFrame window = new JFrame();
    window.setUndecorated(true);
    ColorPicker cp = new ColorPicker();
    window.add(cp);
    window.setSize(201,201);
    window.setAlwaysOnTop(true);
    Components.centerComponent(window);
    window.setVisible(true);
    
    bug("You should see something now.");
  }

  private static void bug(String what) {
    Debug.out("Main", what);
  }
  
  
}
