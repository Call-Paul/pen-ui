package org.six11.sf.rec;

import org.six11.sf.SketchBook;
import org.six11.sf.rec.RecognizerPrimitive.Type;

public class Arrow extends RecognizedItemTemplate {

  public Arrow(SketchBook model) {
    super(model, "Arrow");
    addPrimitive("shaft", Type.Line);
    addPrimitive("head1", Type.Line);
    addPrimitive("head2", Type.Line);
    addConstraint(new Coincident("c1", "shaft.p2", "head1.p2"));
    addConstraint(new Coincident("c2", "shaft.p2", "head2.p2"));
    addConstraint(new AcuteAngle("c3", "head1", "shaft"));
    addConstraint(new AcuteAngle("c4", "head2", "shaft"));
    addConstraint(new EqualLength("c5", "head1", "head2"));
    addConstraint(new Larger("c6", "shaft", "head1"));

  }

}
