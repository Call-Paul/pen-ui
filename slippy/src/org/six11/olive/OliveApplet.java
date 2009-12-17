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
    String fqClass = this.getParameter("mainSlippyClass");
    System.out.println("Got fqClass: " + fqClass);
    System.out.println("Trying absolute path approach...");
    OliveIDE ide = new OliveIDE(true, "");
    ide.attachKeyListener(getRootPane());
    add(ide);
    try {
      if (fqClass != null) {
        ide.openBuffer(fqClass);
      }
    } catch (Exception ex) {
      bug("Applet got exception: " + ex.getMessage());
      ex.printStackTrace();
    }
  }

  private static void bug(String what) {
    Debug.out("OliveApplet", what);
  }

}
