package org.six11.sf.rec;

import org.six11.sf.SketchBook;
import org.six11.sf.rec.RecognizerPrimitive.Type;

public class Arrow extends RecognizedItemTemplate {

  public Arrow(SketchBook model) {
    super(model, "Arrow");
    addPrimitive("shaft", Type.Line);
    addPrimitive("head1", Type.Line);
    addPrimitive("head1", Type.Line);
  }

}
