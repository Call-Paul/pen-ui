package org.six11.skrui.ui;

import java.awt.event.MouseEvent;

import org.six11.skrui.Main;
import org.six11.util.pen.HoverEvent;
import org.six11.util.pen.MouseThing;

  /**
   * A mouse motion and click adapter that sends events to org.six11.skrui.Main.
   * 
   * @author Gabe Johnson <johnsogg@cmu.edu>
   */
  public class SkruiMouseThing extends MouseThing {

    private Main main;

    public SkruiMouseThing(Main main) {
      this.main = main;
    }

    public void mousePressed(MouseEvent ev) {
      main.addRawInputBegin(ev.getX(), ev.getY(), ev.getWhen());
    }

    public void mouseDragged(MouseEvent ev) {
      main.addRawInputProgress(ev.getX(), ev.getY(), ev.getWhen());
    }

    public void mouseReleased(MouseEvent ev) {
      main.addRawInputEnd();
    }

    public void mouseMoved(MouseEvent ev) {
      main.addHover(ev.getX(), ev.getY(), ev.getWhen(), HoverEvent.Type.Hover);
    };

    public void mouseEntered(MouseEvent ev) {
      main.addHover(ev.getX(), ev.getY(), ev.getWhen(), HoverEvent.Type.In);
    }

    public void mouseExited(MouseEvent ev) {
      main.addHover(ev.getX(), ev.getY(), ev.getWhen(), HoverEvent.Type.Out);
    }
  }