package org.six11.skrui;

import java.awt.BorderLayout;

import org.six11.util.Debug;
import org.six11.util.gui.ApplicationFrame;
import org.six11.util.pen.OliveDrawingSurface;

/**
 * 
 **/
public class Main {
  public static void main(String[] args) {
    Debug.useColor = false;
    ApplicationFrame af = new ApplicationFrame("Olive Test GUI");
    OliveDrawingSurface ds = new OliveDrawingSurface();
    af.setLayout(new BorderLayout());
    af.add(ds, BorderLayout.CENTER);
    af.setSize(500, 400);
    af.center();
    af.setVisible(true);
  }
}
