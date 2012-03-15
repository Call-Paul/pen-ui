package org.six11.sf.rec;

import static org.six11.util.Debug.bug;

import java.awt.geom.Area;
import java.util.Collection;

import org.six11.sf.Ink;
import org.six11.sf.Segment;
import org.six11.sf.SketchBook;
import org.six11.sf.SketchRecognizer;
import org.six11.util.pen.ConvexHull;


// ---------------------------------------------------------------------------------------------------
//
//
//          N o t e
//
//                   This class isn't used any longer. See SketchBook#analyzeForErase().
//
//
// ---------------------------------------------------------------------------------------------------
public class EraseGestureRecognizer extends SketchRecognizer {

  public EraseGestureRecognizer(SketchBook model) {
    super(model, Type.SingleRaw);
  }

  public Collection<RecognizedItem> applyTemplate(Collection<RecognizerPrimitive> in)
      throws UnsupportedOperationException {
    throw new UnsupportedOperationException("sorry babe");
  }

  public RecognizedRawItem applyRaw(Ink ink) throws UnsupportedOperationException {
    RecognizedRawItem ret = RecognizedRawItem.noop();
//    long elapsed = ink.getSequence().getLast().getTime() - ink.getSequence().getFirst().getTime();
//    if (elapsed > 500) {
//      float zoom = model.getCamera().getZoom();
//      double dx = ink.getSequence().getRoughDX(zoom);
//      double dy = ink.getSequence().getRoughDY(zoom);
//      if (Math.min(dx, dy) > 7.0) {
//        double area = ink.getSequence().getRoughArea(zoom);
//        double density = ink.getSequence().getRoughDensity(zoom);
//        if ((area > 100) && (density > 2.0)) {
//          ConvexHull hull = ink.getHull();
//          final Area hullArea = new Area(hull.getHullShape());
//          final Collection<Segment> doomed = model.pickDoomedSegments(hullArea);
//          final Collection<Ink> doomedInk = model.pickDoomedInk(hullArea, ink);
//          ret = new RecognizedRawItem(true, RecognizedRawItem.SCRIBBLE_TO_ERASE) {
//            public void activate(SketchBook model) {
//              if (doomedInk.size() > 0) {
//                for (Ink ink : doomedInk) {
//                  model.removeInk(ink);
//                }
//              } else {
//                for (Segment seg : doomed) {
//                  model.removeGeometry(seg);
//                }
//              }
//            }
//          };
//        }
//      } else {
//        bug("Rejecting Mark's favorite bug.");
//      }
//    }
    return ret;
  }

  

}
