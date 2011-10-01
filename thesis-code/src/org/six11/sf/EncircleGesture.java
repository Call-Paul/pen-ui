package org.six11.sf;

import java.awt.Component;
import java.awt.Point;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.List;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

import org.six11.util.pen.Functions;
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
  List<Ink> selection;

  /**
   * @param likelihood
   */
  public EncircleGesture(Component start, Sequence original, double likelihood, int startInclusive, int endInclusive) {
    super(start, original);
    p = likelihood;
    setPoints(original, startInclusive, endInclusive);
  }

  public String getHumanName() {
    return "Encircle Gesture";
  }

  public void setSelection(List<Ink> selection) {
    this.selection = selection;
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

  public Gesture createSubsequentGesture(Component start, Point componentPoint) {
    Sequence seq = new Sequence();
    seq.add(new Pt(componentPoint));
    Gesture ret = new MoveGesture(start, seq, getArea());
    return ret;
  }

  public boolean isPointNearHotspot(Pt pt) {
    boolean ret = false;
    if (selection == null) {
      bug("FYI the selection is null. This will give you problems.");
    }
    // first check the gesture points
    Pt nearest = Functions.getNearestPointOnSequence(pt, originalPoints);
    double dist = nearest.distance(pt);

    if (dist < GestureController.GESTURE_AOE_DISTANCE) {
      ret = true;
    } else if (selection != null) {
      for (Ink sel : selection) {
        if (sel instanceof UnstructuredInk) {
          Sequence seq = ((UnstructuredInk) sel).getSequence();
          dist = Functions.getNearestPointOnSequence(pt, seq).distance(pt);
          if (dist < GestureController.GESTURE_AOE_DISTANCE) {
            bug("Input is at least " + num(dist) + " px from a hotspot, so i say it is near!");
            ret = true;
            break;
          }
        } else {
          // TODO: do stuff.
        }
      }
    }
    return ret;
  }
}
