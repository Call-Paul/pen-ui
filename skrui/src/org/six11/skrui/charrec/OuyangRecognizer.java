package org.six11.skrui.charrec;

import java.util.ArrayList;
import java.util.List;

import org.six11.skrui.shape.Stroke;
import org.six11.util.Debug;
import org.six11.util.data.Statistics;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;

public class OuyangRecognizer {

  private List<Callback> friends;
  private static Vec zero = new Vec(1, 0);
  private static Vec fortyFive = new Vec(1, 1).getUnitVector();
  private static Vec ninety = new Vec(0, 1);
  private static Vec oneThirtyFive = new Vec(-1, 1).getUnitVector();

  public OuyangRecognizer() {
    friends = new ArrayList<Callback>();
  }

  public interface Callback {
    public void recognitionBegun();

    public void recognitionComplete();
  }

  public void addCallback(Callback friend) {
    friends.add(friend);
  }

  public void recognize(List<Stroke> strokes) {
    for (Callback c : friends) {
      c.recognitionBegun();
    }
    List<List<Pt>> normalized = getNormalizedStrokes(strokes);
    Statistics xData = new Statistics();
    Statistics yData = new Statistics();
    for (List<Pt> seq : normalized) {
      computeInitialFeatureValues(seq, xData, yData);
    }
    bug("Center of mass: " + xData.getMean() + ", " + yData.getMean());
    bug("Standard deviation in x: " + xData.getStdDev());
    bug("Standard deviation in y: " + yData.getStdDev());
    for (Callback c : friends) {
      c.recognitionComplete();
    }
  }

  private void computeInitialFeatureValues(List<Pt> seq, Statistics xData, Statistics yData) {
    // set the four direction features for all non-endpoints, and keep stats on where points are
    for (int i = 1; i < seq.size() - 1; i++) {
      Pt prev = seq.get(i - 1);
      Pt here = seq.get(i);
      Pt next = seq.get(i + 1);
      Vec dir = new Vec(prev, next).getUnitVector();
      computeAngle(here, dir, zero, "dir0");
      computeAngle(here, dir, fortyFive, "dir1");
      computeAngle(here, dir, ninety, "dir2");
      computeAngle(here, dir, oneThirtyFive, "dir3");
      here.setDouble("endpoint", 0.0); // record that this is not an endpoint
      xData.addData(here.getX());
      yData.addData(here.getY());
    }
    // cheat a little and copy the direction feature values to the endpoints
    copyAttribs(seq.get(seq.size() - 2), seq.get(seq.size() - 1), "dir0", "dir1", "dir2", "dir3");
    copyAttribs(seq.get(1), seq.get(0), "dir0", "dir1", "dir2", "dir3");
    
    // record the first and last points as endpoints.
    seq.get(0).setDouble("endpoint", 1.0);
    seq.get(seq.size() - 1).setDouble("endpoint", 1.0);
    
    // also add x/y coords for statistics
    xData.addData(seq.get(0).getX());
    yData.addData(seq.get(0).getY());
    xData.addData(seq.get(seq.size() - 1).getX());
    yData.addData(seq.get(seq.size() - 1).getY());
    
  }

  private void copyAttribs(Pt src, Pt dst, String... attribNames) {
    for (String attrib : attribNames) {
      dst.setDouble(attrib, src.getDouble(attrib));
    }
  }

  private void computeAngle(Pt pt, Vec dir, Vec cardinal, String attribName) {
    double angle = Functions.getAngleBetween(cardinal, dir);
    // bug("Angle: " + Debug.num(angle));
    if (Math.abs(angle) > Math.PI / 2) {
      dir = dir.getFlip();
      // bug("Flipping, because angle was: " + Debug.num(angle));
      angle = Functions.getAngleBetween(cardinal, dir);
    }
    double difference = Math.abs(angle);
    double featureValue = Math.max(0, 1 - difference / (Math.PI / 4));
    pt.setDouble(attribName, featureValue);

  }

  private static void bug(String what) {
    Debug.out("OuyangRecognizer", what);
  }

  private List<List<Pt>> getNormalizedStrokes(List<Stroke> strokes) {
    List<List<Pt>> ret = new ArrayList<List<Pt>>();
    for (Stroke stroke : strokes) {
      ret.add(Functions.getNormalizedSequence(stroke.getPoints(), 4.0));
    }
    return ret;
  }

}
