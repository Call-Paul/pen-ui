package org.six11.sf.rec;

import java.util.Stack;

import org.six11.sf.SketchBook;
import org.six11.sf.rec.RecognizerPrimitive.Type;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;

public class RightAngleBrace extends RecognizedItemTemplate {

  public static String CORNER_A = "cornerA";
  public static String CORNER_B = "cornerB";
  public static String CORNER_C = "cornerC";
  public static String CORNER_D = "cornerD";

  public RightAngleBrace(SketchBook model) {
    super(model, "RightAngleBrace");
    addPrimitive("line1", Type.Line);
    addPrimitive("line2", Type.Line);

    addConstraint(new Coincident("c1", "line1.p2", "line2.p1"));
    addConstraint(new EqualLength("c2", "line1", "line2"));
    addConstraint(new RightAngle("c3", "line1", "line2"));
  }
  
  public RecognizedItem makeItem(Stack<String> slots, Stack<RecognizerPrimitive> prims) {
    RecognizedItem item = new RecognizedItem(this, slots, prims);
    
    RecognizerPrimitive line1 = search(slots, prims, "line1");
    RecognizerPrimitive line2 = search(slots, prims, "line2");
    if (line1 != null && line2 != null) {
      Pt a = line1.getSubshape("p2");
      Pt b = line1.getSubshape("p1");
      Pt c = line2.getSubshape("p2");
      Vec v1 = new Vec(a, b);
      Vec v2 = new Vec(a, c);
      Pt d = new Pt(a.getX() + v1.getX() + v2.getX(), a.getY() + v1.getY() + v2.getY());
      item.setFeaturedPoint(CORNER_A, a);
      item.setFeaturedPoint(CORNER_B, b);
      item.setFeaturedPoint(CORNER_C, c);
      item.setFeaturedPoint(CORNER_D, d);
    }
    return item;
  }

}
