package org.six11.sf.rec;

import org.six11.sf.SketchBook;

public abstract class ContextConstraint extends RecognizerConstraint {

  protected SketchBook model;
  
  public ContextConstraint(String name, SketchBook model, String... names) {
    super(name, names);
    this.model = model;
  }

}
