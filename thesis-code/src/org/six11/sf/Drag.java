package org.six11.sf;

import java.awt.Point;


public class Drag {

  public static interface Listener {
    public void dragMove(Drag.Event ev);
    public void dragEnter(Drag.Event ev);
    public void dragExit(Drag.Event ev);
    public void dragDrop(Drag.Event ev);
  }
  
  public static class Event {

    private Point pt;
    private GlassPane.ActivityMode mode;
    
    public Event(Point pt, GlassPane.ActivityMode mode) {
      this.pt = pt;
      this.mode = mode;
    }
    
    public Point getPt() {
      return pt;
    }
    
    public GlassPane.ActivityMode getMode() {
      return mode;
    }
    
  }
}
