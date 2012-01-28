package org.six11.sf.rec;

import java.util.Collection;
import java.util.List;

import org.six11.sf.Dot;
import org.six11.sf.GuidePoint;
import org.six11.sf.Ink;
import org.six11.sf.Segment;
import org.six11.sf.SegmentDelegate;
import org.six11.sf.SketchBook;
import org.six11.sf.SketchRecognizer;
import org.six11.sf.SketchRecognizer.Type;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;

import static org.six11.util.Debug.bug;

public class DotReferenceGestureRecognizer extends SketchRecognizer {

  public static double NEARNESS_THRESHOLD = 4.5;

  public DotReferenceGestureRecognizer(SketchBook model) {
    super(model, Type.SingleRaw);
  }

  @Override
  public Collection<RecognizedItem> applyTemplate(Collection<RecognizerPrimitive> in)
      throws UnsupportedOperationException {
    throw new UnsupportedOperationException("This recognizer can't do templates.");
  }

  @Override
  public RecognizedRawItem applyRaw(Ink ink) throws UnsupportedOperationException {
    RecognizedRawItem ret = RecognizedRawItem.noop();
    List<Segment> segs = ink.getSegments();
    if (segs.size() == 1 && segs.get(0).getType() == SegmentDelegate.Type.Dot) {
      Segment dot = segs.get(0);
      Pt loc = dot.getP1();

      // The are five possibilities: 
      //   (1) Near a segment endpoint; 
      //   (2) Near a segment interior; 
      //   (3) Near a suggested point (made e.g. midpoint of selected segment), 
      //   (4) Near a guide line
      //   (5) None of the above
      // Search for these cases in that order and stop when a match is found.

      boolean ok = false;
      // cases 1 and 2
      for (Segment seg : model.getGeometry()) {
        if (loc.distance(seg.getP1()) < NEARNESS_THRESHOLD) {
          ret = makeEndpointItem(seg, true);
          ok = true;
        } else if (loc.distance(seg.getP2()) < NEARNESS_THRESHOLD) {
          ret = makeEndpointItem(seg, false);
          ok = true;
        } else if (seg.isNear(loc, NEARNESS_THRESHOLD)) {
          ret = makeNearItem(seg, loc);
          ok = true;
        }
        if (ok) {
          break;
        }
      }
      // case 3 yet to do when I have suggestions working
      // case 4 yet to do when I have guides working
      if (!ok) {
        ret = makeItem(dot.getP1());
      }
    }
    return ret;
  }

  private RecognizedRawItem makeNearItem(final Segment seg, Pt loc) {
    Pt nearPt = seg.getNearestPoint(loc);
    Line line = seg.asLine();
    Vec lineVec = new Vec(seg.getP1(), seg.getP2());
    final Vec param = SegmentDelegate.calculateParameterForPoint(lineVec.mag(), line, nearPt);
    
    RecognizedRawItem ret = new RecognizedRawItem(true, RecognizedRawItem.FAT_DOT_REFERENCE_POINT,
        RecognizedRawItem.OVERTRACE_TO_SELECT_SEGMENT,
        RecognizedRawItem.ENCIRCLE_ENDPOINTS_TO_MERGE) {
      public void activate(SketchBook model) {
        model.addGuidePoint(new GuidePoint(seg, param));
      }
    };
    return ret;
  }

  private RecognizedRawItem makeItem(final Pt pt) {
    RecognizedRawItem ret = new RecognizedRawItem(true, RecognizedRawItem.FAT_DOT_REFERENCE_POINT,
        RecognizedRawItem.OVERTRACE_TO_SELECT_SEGMENT,
        RecognizedRawItem.ENCIRCLE_ENDPOINTS_TO_MERGE) {
      public void activate(SketchBook model) {
        model.addGuidePoint(new GuidePoint(pt));
      }
    };
    return ret;
  }

  private RecognizedRawItem makeEndpointItem(final Segment seg, final boolean b) {
    RecognizedRawItem ret = new RecognizedRawItem(true, RecognizedRawItem.FAT_DOT_REFERENCE_POINT,
        RecognizedRawItem.OVERTRACE_TO_SELECT_SEGMENT,
        RecognizedRawItem.ENCIRCLE_ENDPOINTS_TO_MERGE) {
      public void activate(SketchBook model) {
        if (b) {
          model.addGuidePoint(new GuidePoint(seg, new Vec(0, 0)));
        } else {
          model.addGuidePoint(new GuidePoint(seg, new Vec(1, 0)));
        }
      }
    };
    return ret;
  }

}
