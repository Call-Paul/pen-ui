package org.six11.sf;

import java.util.Collection;
import java.util.List;

import org.six11.sf.rec.RecognizedItem;
import org.six11.sf.rec.RecognizedRawItem;
import org.six11.sf.rec.RecognizerPrimitive;
import org.six11.util.pen.Pt;

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
    if (segs.size() == 1 && segs.get(0).getType() == Segment.Type.Dot) {
      final Dot dot = (Dot) segs.get(0);
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
          bug("dot is near segment " + seg.id + " endpoint 1");
          ret = makeItem(seg.getP1());
          ok = true;
        } else if (loc.distance(seg.getP2()) < NEARNESS_THRESHOLD) {
          bug("dot is near segment " + seg.id + " endpoint 2");
          ret = makeItem(seg.getP2());
          ok = true;
        } else if (seg.isNear(loc, NEARNESS_THRESHOLD)) {
          bug("dot is near segment " + seg.id + " interior");
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
        bug("dot is not near anything.");
        ret = makeItem(dot.getP1());
      }
    }
    return ret;
  }

  private RecognizedRawItem makeNearItem(Segment seg, Pt loc) {
    Pt nearPt = seg.getNearestPoint(loc);
    double param = seg.getPointParam(nearPt);
    
    RecognizedRawItem ret = new RecognizedRawItem(true, RecognizedRawItem.FAT_DOT_REFERENCE_POINT,
        RecognizedRawItem.OVERTRACE_TO_SELECT_SEGMENT,
        RecognizedRawItem.ENCIRCLE_ENDPOINTS_TO_MERGE) {
      public void activate(SketchBook model) {
//        model.addGuidePoint(new GuidePoint());
      }
    };
    return ret;
  }

  private RecognizedRawItem makeItem(final Pt pt) {
    RecognizedRawItem ret = new RecognizedRawItem(true, RecognizedRawItem.FAT_DOT_REFERENCE_POINT,
        RecognizedRawItem.OVERTRACE_TO_SELECT_SEGMENT,
        RecognizedRawItem.ENCIRCLE_ENDPOINTS_TO_MERGE) {
      public void activate(SketchBook model) {
        model.addGuidePoint(pt);
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
          model.addGuidePoint(seg.getP1().copyXYT());
        } else {
          model.addGuidePoint(seg.getP2().copyXYT());
        }
      }
    };
    return ret;
  }

}
