package org.six11.sf;

import java.util.Collection;
import java.util.Set;

import org.six11.sf.rec.RecognizedItem;
import org.six11.sf.rec.RecognizerPrimitive;

public abstract class SketchRecognizer {

  protected SketchBook model;

  public SketchRecognizer(SketchBook model) {
    this.model = model;
  }

  public abstract Collection<RecognizedItem> apply(Collection<RecognizerPrimitive> in);
  
}
