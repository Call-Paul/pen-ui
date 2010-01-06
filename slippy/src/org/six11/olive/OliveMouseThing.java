package org.six11.olive;

import java.awt.event.MouseEvent;

import org.six11.util.Debug;
import org.six11.util.pen.MouseThing;

/**
 * A mouse motion and click adapter that sends events to an OliveSoup instance.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class OliveMouseThing extends MouseThing {

  private OliveSoup soup;

  public OliveMouseThing(OliveSoup soup) {
    this.soup = soup;
  }

  public void mousePressed(MouseEvent ev) {
    soup.addRawInputBegin(ev.getX(), ev.getY(), ev.getWhen());
  }

  public void mouseDragged(MouseEvent ev) {
    soup.addRawInputProgress(ev.getX(), ev.getY(), ev.getWhen());
  }

  public void mouseReleased(MouseEvent ev) {
    soup.addRawInputEnd();
  }

  @SuppressWarnings("unused")
  private void bug(final String what) {
    Debug.out("OliveMouseThing", what);
  }

}
