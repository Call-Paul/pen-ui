package org.six11.olive;

import javax.swing.JApplet;

import org.six11.util.Debug;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class OliveApplet extends JApplet {

  public OliveApplet() {

  }

  public void init() {

    OliveIDE ide = new OliveIDE(new WebEnvironment(getCodeBase()));
    ide.attachKeyListener(getRootPane());
    add(ide);

  }

  @SuppressWarnings("unused")
  private static void bug(String what) {
    Debug.out("OliveApplet", what);
  }

}
