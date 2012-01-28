package org.six11.sf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.RotatedEllipse;
import static org.six11.util.Debug.bug;

public class EllipseArcSegment extends SegmentDelegate {

  RotatedEllipse ellie;

  public EllipseArcSegment(Ink ink, List<Pt> points, boolean termA, boolean termB) {
    // The ellipse is fit using regression, and does not necessarily begin and end at the first 
    // and last points in the list. So get the elliptical region, and transform it two times so it
    // does begin and end at those points.
    ellie = Functions.createEllipse(points, true);
    int n = points.size();
    ellie.setArcRegion(points.get(0), points.get(n / 2), points.get(n - 1));
    List<Pt> surface = ellie.initArc();
    init(ink, surface, termA, termB, Segment.Type.EllipticalArc);
  }


}
