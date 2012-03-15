package org.six11.sf.rec;

import java.util.Collection;
import java.util.Stack;

import org.six11.sf.SketchBook;
import org.six11.sf.rec.RecognizerPrimitive.Certainty;

public class Arrow extends RecognizedItemTemplate {

  public static String TIP = "arrowTip";
  public static String START = "arrowStart";

  public Arrow(SketchBook model) {
    super(model, "Arrow");
    addPrimitive("shaft", RecognizerPrimitive.Type.Line);
    addPrimitive("head1", RecognizerPrimitive.Type.Line);
    addPrimitive("head2", RecognizerPrimitive.Type.Line);
    addConstraint(new Coincident("c1", "shaft.p2", "head1.p2"));
    addConstraint(new Coincident("c2", "shaft.p2", "head2.p2"));
    addConstraint(new AcuteAngle("c3", "head1", "shaft"));
    addConstraint(new AcuteAngle("c4", "head2", "shaft"));
    // maybe add a constraint that says the heads should have opposite signs
    addConstraint(new EqualLength("c5", "head1", "head2"));
    addConstraint(new Larger("c6", "shaft", "head1"));
  }

  public Certainty checkContext(RecognizedItem item, Collection<RecognizerPrimitive> in) {
    return Certainty.Yes;
  }
  
  public RecognizedItem makeItem(Stack<String> slots, Stack<RecognizerPrimitive> prims) {
    RecognizedItem item = new RecognizedItem(this, slots, prims);
    RecognizerPrimitive prim = search(slots, prims, "shaft");
    if (prim != null) { 
      item.setFeaturedPoint(START, prim.getSubshape("p1"));
      item.setFeaturedPoint(TIP, prim.getSubshape("p2"));
    }
    return item;
  }

  @Override
  public void create(RecognizedItem item, SketchBook model) {

  }


}
