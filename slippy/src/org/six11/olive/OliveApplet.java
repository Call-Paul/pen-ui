package org.six11.olive;

import javax.swing.JApplet;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class OliveApplet extends JApplet {

  public void init() {
    OliveIDE ide = new OliveIDE(new WebEnvironment(getCodeBase()));
    ide.attachKeyListener(getRootPane());
    add(ide);
  }
}
