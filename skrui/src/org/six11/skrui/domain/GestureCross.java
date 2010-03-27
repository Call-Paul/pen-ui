package org.six11.skrui.domain;

import java.util.List;

import org.six11.skrui.DrawingBufferRoutines;
import org.six11.skrui.constraint.EqualLength;
import org.six11.skrui.constraint.Intersects;
import org.six11.skrui.constraint.Near;
import org.six11.skrui.script.Dot;
import org.six11.skrui.script.LineSegment;
import org.six11.skrui.script.Neanderthal;
import org.six11.skrui.script.Primitive;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class GestureCross extends GestureShapeTemplate {

  public GestureCross(Domain domain) {
    super(domain, "Gesture Cross");

    addPrimitive("dot", Dot.class);
    addPrimitive("line1", LineSegment.class);
    addPrimitive("line2", LineSegment.class);
    addConstraint("c1", new EqualLength("c1", domain.getData(), "line1", "line2"));
    addConstraint("c2", new Intersects("c2", domain.getData(), "line1", "line2"));
    addConstraint("c3", new Near("c3", domain.getData(), "dot", "line1"));

//    domain.addRenderer("Gesture Cross", new ShapeRenderer() {
//      public void draw(DrawingBuffer db, Shape s) {
//        Primitive line1 = s.subshapes.get("line1");
//        Primitive line2 = s.subshapes.get("line2");
//        Pt spot = GestureCross.getPt(line1, line2);
//        DrawingBufferRoutines.cross(db, spot);
//      }
//    });
    resetValid(true);
  }

  public static Pt getPt(Primitive line1, Primitive line2) {
    return Functions.getIntersectionPoint(line1.getGeometryLine(), line2.getGeometryLine());
  }

  @Override
  public void trigger(Neanderthal data, Shape s, List<Primitive> matches) {
    data.addStructurePoint(GestureCross.getPt(s.subshapes.get("line1"), s.subshapes.get("line2")),
        matches);
//    if (domain.renderers.get(getName()) != null) {
//      DrawingBuffer db = data.getSoup().getBuffer("structure");
//      if (db == null) {
//        db = new DrawingBuffer();
//        data.getSoup().addBuffer("structure", db);
//      }
//      domain.renderers.get(getName()).drawContextually(db, s, data);
//    }

  }

}
