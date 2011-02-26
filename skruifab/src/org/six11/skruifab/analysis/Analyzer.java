package org.six11.skruifab.analysis;

import java.util.TreeSet;

import org.six11.util.Debug;
import org.six11.util.pen.Pt;

public class Analyzer {
  
  public static final String PRIMITIVES = "primitives";
  
  /**
   * tg is a TimeGraph that stores Sequences orderd by time.
   */
  TimeGraph tg;

  /**
   * allPoints stores all visible points associated with lines or filled regions. This makes it more
   * efficient to answer questions like 'which points are near X,Y'.
   */
  PointGraph allPoints;
  
  
  public Analyzer() {
    tg = new TimeGraph();
    allPoints = new PointGraph();
  }
  
  public TimeGraph getTimeGraph() {
    return tg;
  }
  
  public PointGraph getAllPoints() {
    return allPoints;
  }
  
  public void processFinishedSequence(Stroke seq) {
    getTimeGraph().add(seq);
    // explicity map points back to their original sequence so we can find it later.
    getAllPoints().addAll(seq);
    // Create a 'primitives' set where dots/ellipses/polyline elements will go.
    seq.setAttribute(PRIMITIVES, new TreeSet<Primitive>(Primitive.sortByIndex));
    // All the 'detectXYZ' methods will insert a Primitive into seq's primitives set.
//    detectDot(seq);
//    detectVeryShortLines(seq);
//    detectEllipse(seq);
//    detectPolyline(seq);
//
//    for (Primitive prim : getPrimitiveSet(seq)) {
//      recordPrimitive(prim);
//    }
//
//    // complain when points are not part of a primitive
//    for (Pt pt : seq) {
//      TreeSet<Primitive> pointPrims = (TreeSet<Primitive>) pt.getAttribute(PRIMITIVES);
//      if (pointPrims == null || pointPrims.size() == 0) {
//        bug("Found lonely point: " + pt.getID() + ": " + Debug.num(pt));
//        pt.setBoolean("buggy", true);
//      }
//    }
//
//    firePrimitiveEvent(seq);

  }

}
