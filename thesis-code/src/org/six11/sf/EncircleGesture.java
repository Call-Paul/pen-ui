package org.six11.sf;

import java.util.ArrayList;
import java.util.List;

import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

/**
 * 
 *
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class EncircleGesture extends Gesture {
  
  List<Pt> points;

  /**
   * @param likelihood
   */
  public EncircleGesture(double likelihood) {
    p = likelihood;
  }

  public String getHumanName() {
    return "Encircle Gesture";
  }

  public double getProbability() {
    return p;
  }
  
  public void setPoints(Sequence seq, int startInclusive, int endInclusive) {
    points = new ArrayList<Pt>();
    for (int i=startInclusive; i <= endInclusive; i++) {
      points.add(seq.get(i));
    }
  }
  
  public List<Pt> getPoints() {
    return points;
  }

}
