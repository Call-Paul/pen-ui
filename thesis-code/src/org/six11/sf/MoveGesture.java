package org.six11.sf;

import java.awt.Component;
import java.awt.Point;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;

import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

import static org.six11.util.Debug.bug;

public class MoveGesture extends Gesture {

  Area where;
  
  public MoveGesture(Component start, Sequence seq, Area where) {
    super(start, seq);
    this.where = where;
  }

  public Area getWhere() {
    return where;
  }
  
  @Override
  public String getHumanName() {
    return "Move Gesture";
  }

  @Override
  public double getProbability() {
    return 1;
  }

  public Gesture createSubsequentGesture(Component start, Point componentPoint) {
    bug("Can't make a subsequent gesture off MoveGesture");
    return null;
  }

  @Override
  public boolean isPointNearHotspot(Pt pt) {
    // TODO Auto-generated method stub
    return false;
  }

}
