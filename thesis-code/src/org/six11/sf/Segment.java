package org.six11.sf;

import java.awt.geom.Area;

public class Segment implements HasFuzzyArea {
  
  private SegmentDelegate d;
  
  public Segment(SegmentDelegate delegate) {
    this.d = delegate;
  }

  public Area getFuzzyArea(double fuzzyFactor) {
    return d.getFuzzyArea(fuzzyFactor);
  }

}
