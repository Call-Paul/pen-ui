package org.six11.sf.rec;

import java.util.Collection;
import java.util.List;

import org.six11.sf.Dot;
import org.six11.sf.GuidePoint;
import org.six11.sf.Ink;
import org.six11.sf.Segment;
import org.six11.sf.SketchBook;
import org.six11.sf.SketchRecognizer;
import org.six11.util.pen.Pt;

import static org.six11.util.Debug.bug;

public class DotSelectGestureRecognizer extends SketchRecognizer {

  public DotSelectGestureRecognizer(SketchBook model) {
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
    if (segs != null && segs.size() == 1 && segs.get(0).getType() == Segment.Type.Dot) {
      final Segment dot = segs.get(0);
      Pt loc = dot.getP1();
      for (GuidePoint g : model.getGuidePoints()) {
        if (g.getLocation().distance(loc) < DotReferenceGestureRecognizer.NEARNESS_THRESHOLD) {
          ret = makeToggleItem(g);
        }
      }
    }
    return ret;
  }

  private RecognizedRawItem makeToggleItem(final GuidePoint gpt) {
    RecognizedRawItem ret = new RecognizedRawItem(true, RecognizedRawItem.FAT_DOT_SELECT,
        RecognizedRawItem.OVERTRACE_TO_SELECT_SEGMENT,
        RecognizedRawItem.ENCIRCLE_ENDPOINTS_TO_MERGE, RecognizedRawItem.FAT_DOT_REFERENCE_POINT) {
      public void activate(SketchBook model) {
        model.toggleGuidePoint(gpt);
      }
    };
    return ret;
  }

}
