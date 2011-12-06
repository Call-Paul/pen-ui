package org.six11.sf;

import java.awt.Color;
import java.awt.geom.Area;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import static java.lang.Math.toDegrees;
import static java.lang.Math.abs;

import javax.naming.OperationNotSupportedException;

import org.six11.sf.rec.RecognizedItem;
import org.six11.sf.rec.RecognizedRawItem;
import org.six11.sf.rec.RecognizerPrimitive;
import org.six11.util.data.Statistics;
import org.six11.util.gui.shape.Areas;
import org.six11.util.gui.shape.ShapeFactory;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

public class SelectGestureRecognizer extends SketchRecognizer {

  public SelectGestureRecognizer(SketchBook model) {
    super(model, Type.SingleRaw);
  }

  @Override
  public Collection<RecognizedItem> applyTemplate(Collection<RecognizerPrimitive> in)
      throws OperationNotSupportedException {
    throw new OperationNotSupportedException(
        "can't use this recognizer to look at primitives. i need ink");
  }

  @Override
  public RecognizedRawItem applyRaw(Ink ink) throws OperationNotSupportedException {
    RecognizedRawItem ret = RecognizedRawItem.noop();
    Area totalArea = ShapeFactory.getFuzzyArea(ink.getSequence().getPoints(), 3.5);
    //    DrawingBuffer db = model.getLayers().getLayer("select gesture");
    //    db.clear();
    //    DrawingBufferRoutines.fillShape(db, totalArea, new Color(255, 0, 0, 120), 0.5);
    Collection<Segment> underneath = model.findSegments(totalArea, 3.5);
    //    for (Segment under : underneath) {
    //      DrawingBufferRoutines.fillShape(db, under.getFuzzyArea(3.5), new Color(0, 0, 255, 120), 0.5);
    //    }
    final Collection<Segment> selectUs = new HashSet<Segment>();
    //    boolean selectedSomething = false;
    Vec inkVec = new Vec(ink.getSequence().getFirst(), ink.getSequence().getLast());
    for (Segment undy : underneath) {
      Statistics stats = new Statistics();
      for (Pt pt : ink.getSequence()) {
        List<Pt> segPoints = undy.asPolyline();
        Pt near = Functions.getNearestPointOnPolyline(pt, segPoints);
        stats.addData(near.distance(pt));
      }
      if (stats.getMax() < 10.0 || (stats.getMax() < 15.0 && stats.getMean() < 5.0)) {

        double ang = 0;
        if (undy.getType() == Segment.Type.Line) {
          ang = Math.min(abs(Functions.getSignedAngleBetween(undy.getStartDir(), inkVec)),
              abs(Functions.getSignedAngleBetween(undy.getEndDir(), inkVec)));
          ang = toDegrees(ang);
          bug("angle: " + num(ang));
        }
        if (ang < 20) {
          bug("** Select " + undy);
          selectUs.add(undy);
        }
        //        selectedSomething = true;
      }
      //      bug("For " + undy + "...");
      //      stats.printDebug();
    }
    if (selectUs.isEmpty()) {
      bug("Selected nothing");
    }
    if (!selectUs.isEmpty()) {
      ret = new RecognizedRawItem(true) {
        public void activate(SketchBook model) {
          model.setSelectedSegments(selectUs);
          model.getEditor().drawStuff();
        }
      };
    }
    return ret;
  }
}
