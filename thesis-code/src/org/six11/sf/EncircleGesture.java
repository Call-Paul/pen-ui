package org.six11.sf;

import java.awt.Point;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.List;

import static org.six11.util.Debug.bug;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class EncircleGesture extends Gesture {

  List<Pt> points;
  Area area;

  /**
   * @param likelihood
   */
  public EncircleGesture(Sequence original, double likelihood, int startInclusive, int endInclusive) {
    super(original);
    p = likelihood;
    setPoints(original, startInclusive, endInclusive);
  }

  public String getHumanName() {
    return "Encircle Gesture";
  }

  public double getProbability() {
    return p;
  }

  public void setPoints(Sequence seq, int startInclusive, int endInclusive) {

    points = new ArrayList<Pt>();
    for (int i = startInclusive; i <= endInclusive; i++) {
      points.add(seq.get(i));
    }
  }

  public List<Pt> getPoints() {
    return points;
  }

  public Area getArea() {
    if (area == null) {
      area = new Area(new Sequence(points));
    }
    return area;
  }

  public Gesture createSubsequentGesture(Point componentPoint) {
    bug("Gesture.createSubsequentGesture not implemented yet!");
    return null;
  }
}
