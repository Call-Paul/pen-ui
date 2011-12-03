package org.six11.sf.rec;

import java.awt.geom.Area;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import static java.lang.Math.toDegrees;

import javax.naming.OperationNotSupportedException;

import org.six11.sf.CornerFinder;
import org.six11.sf.Ink;
import org.six11.sf.Segment;
import org.six11.sf.SketchBook;
import org.six11.sf.SketchRecognizer;
import org.six11.util.data.Statistics;
import org.six11.util.pen.ConvexHull;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Vec;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

public class EraseGestureRecognizer extends SketchRecognizer {

  private CornerFinder cf;

  public EraseGestureRecognizer(SketchBook model) {
    super(model, Type.SingleRaw);
    this.cf = new CornerFinder();
  }

  public Collection<RecognizedItem> applyTemplate(Collection<RecognizerPrimitive> in)
      throws OperationNotSupportedException {
    throw new OperationNotSupportedException("This recognizer can't do templates.");
  }

  @Override
  public RecognizedRawItem applyRaw(Ink ink) throws OperationNotSupportedException {
    RecognizedRawItem ret = RecognizedRawItem.noop();
    Set<Segment> segs = cf.findCorners(ink);
    int lines = 0;
    int total = 0;
    Statistics lengthStats = new Statistics();
    for (Segment seg : segs) {
      if (seg.getType() == Segment.Type.Line) {
        lengthStats.addData(seg.length());
        lines++;
      }
      total++;
    }
    if (lines >= 5 && (((double) lines / (double) total) >= 6.0 / 7.0)) {
      Set<Vec> directions = new HashSet<Vec>();
      double median = lengthStats.getMedian();
      for (Segment seg : segs) {
        if (seg.getType() == Segment.Type.Line) {
          Vec v = new Vec(seg.getP1(), seg.getP2());
          if (v.mag() > median / 2) {
            directions.add(v);
          }
        }
      }
      Vec[] dirs = directions.toArray(new Vec[directions.size()]);
      Statistics angleStats = new Statistics();
      for (int i = 0; i < dirs.length; i++) {
        Vec cursor = dirs[i];
        for (int j = i + 1; j < dirs.length; j++) {
          Vec v = dirs[j];
          double ang = Math.abs(Functions.getSignedAngleBetween(cursor, v));
          if (ang > Math.PI / 2) {
            ang = Math.abs(Functions.getSignedAngleBetween(cursor.getFlip(), v));
          }
          angleStats.addData(toDegrees(ang));
        }
      }
      bug("Angle statistics: ");
      angleStats.printDebug();
      if (angleStats.getMedian() < 10.0 && angleStats.getMean() < 10.0) {
        bug("** ERASE! maybe. ** ");
        final Area area = ink.getArea();
        ret = new RecognizedRawItem(true) {
          public void activate(SketchBook model) {
            model.eraseUnderArea(area);
          }
        };
      }
    }
    bug("-- (" + lines + "/" + total + ")");
    return ret;
  }
}
