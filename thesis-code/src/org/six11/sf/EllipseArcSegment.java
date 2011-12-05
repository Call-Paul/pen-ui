package org.six11.sf;

import java.util.List;

import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.RotatedEllipse;

public class EllipseArcSegment extends Segment {

  public EllipseArcSegment(Ink ink, List<Pt> points, boolean termA, boolean termB) {
//    super(ink, points, termA, termB);
    // The ellipse is fit using regression, and does not necessarily begin and end at the first 
    // and last points in the list. So get the elliptical region, and transform it two times so it
    // does begin and end at those points.
    RotatedEllipse ellie = Functions.createEllipse(points);
    int n = points.size();
    ellie.setArcRegion(points.get(0), points.get(n / 2), points.get(n - 1));
    int len = (int) Math.ceil(Functions.getCurvilinearLength(points));
    List<Pt> surface = ellie.getRestrictedArcPath(len);

    // first transform: use 0 as hinge
    int idxHinge = 0;
    int idxReference = surface.size() - 1;
    Pt target = points.get(points.size() - 1);
    List<Pt> transformed = Functions.hinge(idxHinge, idxReference, target, surface);
    for (int i=0; i < points.size(); i++) {
      points.get(i).setLocation(transformed.get(i));
    }
    
    // second transform: use last as hinge
    idxHinge = surface.size() - 1;
    idxReference = 0;
    target = points.get(0);
    transformed = Functions.hinge(idxHinge, idxReference, target, surface);
    for (int i=0; i < points.size(); i++) {
      points.get(i).setLocation(transformed.get(i));
    }
//    this.points = surface;
//    this.type = Type.EllipticalArc;
    
    init(ink, surface, termA, termB, Type.EllipticalArc);
  }
  
}
