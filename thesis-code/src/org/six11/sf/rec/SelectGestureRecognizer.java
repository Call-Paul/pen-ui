package org.six11.sf.rec;

import static java.lang.Math.abs;
import static java.lang.Math.toDegrees;
import static org.six11.util.Debug.num;
import static org.six11.util.Debug.bug;

import java.awt.geom.Area;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.six11.sf.Ink;
import org.six11.sf.Segment;
import org.six11.sf.SketchBook;
import org.six11.sf.SketchRecognizer;
import org.six11.util.data.Statistics;
import org.six11.util.gui.shape.ShapeFactory;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;

public class SelectGestureRecognizer extends SketchRecognizer {

  public static final double FUZZY_AREA_SIZE = 3.5;

  public SelectGestureRecognizer(SketchBook model) {
    super(model, Type.SingleRaw);
  }

  @Override
  public Collection<RecognizedItem> applyTemplate(Collection<RecognizerPrimitive> in)
      throws UnsupportedOperationException {
    throw new UnsupportedOperationException(
        "can't use this recognizer to look at primitives. i need ink");
  }

  @Override
  public RecognizedRawItem applyRaw(Ink ink) throws UnsupportedOperationException {
    RecognizedRawItem ret = RecognizedRawItem.noop();
    float zoom = model.getCamera().getZoom();
    double targetFuzzy = FUZZY_AREA_SIZE / zoom;
    Area totalArea = ShapeFactory.getFuzzyArea(ink.getSequence().getPoints(), targetFuzzy);
    Collection<Segment> underneath = model.findSegments(totalArea, targetFuzzy);
    final Collection<Segment> selectUs = new HashSet<Segment>();
    final Collection<Segment> unselectUs = new HashSet<Segment>();
    Vec inkVec = new Vec(ink.getSequence().getFirst(), ink.getSequence().getLast());
    for (Segment undy : underneath) {
      Statistics stats = new Statistics();
      for (Pt pt : ink.getSequence()) {
        List<Pt> segPoints = undy.asPolyline();
        Pt near = Functions.getNearestPointOnPolyline(pt, segPoints);
        double datum = near.distance(pt) * zoom;
        stats.addData(datum); // unscaling here, rather than below with the inequalities
      }
      if ((stats.getMax() < 10.0) || ((stats.getMax() < 15.0) && (stats.getMean() < 5.0))) {
        double ang = 0;
        if (undy.getType() == Segment.Type.Line) {
          ang = Math.min(abs(Functions.getSignedAngleBetween(undy.getStartDir(), inkVec)),
              abs(Functions.getSignedAngleBetween(undy.getEndDir(), inkVec)));
          ang = toDegrees(ang);
        }
        if (ang < 20) {
          if (model.isSelected(undy)) {
            unselectUs.add(undy);
          } else {
            selectUs.add(undy);
          }
        }
      }
    }
    if (!selectUs.isEmpty() || !unselectUs.isEmpty()) {
      ret = new RecognizedRawItem(true, RecognizedRawItem.OVERTRACE_TO_SELECT_SEGMENT) {
        public void activate(SketchBook model) {
          model.setSelectedSegments(selectUs);
          model.deselectSegments(unselectUs);
        }
      };
    }
    return ret;
  }
}
