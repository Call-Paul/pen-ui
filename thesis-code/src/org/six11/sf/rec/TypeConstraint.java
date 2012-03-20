package org.six11.sf.rec;

import org.six11.sf.SketchBook;
import org.six11.sf.rec.RecognizerPrimitive.Certainty;
import org.six11.sf.rec.RecognizerPrimitive.Type;

public class TypeConstraint extends RecognizerConstraint {

  private Type type;

  public TypeConstraint(SketchBook model, String slotName, RecognizerPrimitive.Type type) {
    super(model, slotName, slotName);
    this.type = type;
  }

  @Override
  public Certainty check(RecognizerPrimitive... p) {
    Certainty cert = Certainty.No;
    if ((p.length == 1) && (p[0].getType() == type)) {
      cert = Certainty.Yes;
    }
    return cert;
  }

}
