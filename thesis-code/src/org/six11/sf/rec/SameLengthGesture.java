package org.six11.sf.rec;

import java.util.Collection;
import java.util.Set;
import java.util.Stack;

import org.six11.sf.Segment;
import org.six11.sf.SegmentFilter;
import org.six11.sf.SketchBook;
import org.six11.sf.rec.RecognizerPrimitive.Certainty;
import org.six11.sf.rec.RecognizerPrimitive.Type;
import org.six11.util.math.Interval;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;

public class SameLengthGesture extends RecognizedItemTemplate {

  public SameLengthGesture(SketchBook model) {
    super(model, "SameLengthGesture");
    addPrimitive("line1", Type.Line);
    addPrimitive("line2", Type.Line);
    addConstraint(new LineLengthConstraint("c1", new Interval(0, 100), "line1"));
    addConstraint(new LineLengthConstraint("c2", new Interval(0, 100), "line2"));
    addConstraint(new EqualLength("c3", "line1", "line2"));
  }

  @Override
  public RecognizedItem makeItem(Stack<String> slots, Stack<RecognizerPrimitive> prims) {
    RecognizedItem item = new RecognizedItem(this, slots, prims);
    // nothing interesting required.
    return item;
  }

  @Override
  public Certainty checkContext(RecognizedItem item, Collection<RecognizerPrimitive> in) {
    Certainty ret = Certainty.No;
    Set<Segment> segs = model.getGeometry();
    segs = SegmentFilter.makeCohortFilter(in).filter(segs);

    // 1) use a filter that only selects lines whose midpoint is near line1 or line2's midpoint

    // 2) use a filter that only selects lines that are substantially longer than line1/line2

    // 3) use a filter that only selects lines that are the sole intersecter of line1/line2.
    // (so if line1 intersects more than one thing, nothing passes. it must intersect exactly one thing.)

    return ret;
  }

}
