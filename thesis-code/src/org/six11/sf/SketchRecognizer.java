package org.six11.sf;

public abstract class SketchRecognizer {

  protected SketchBook model;

  public SketchRecognizer(SketchBook model) {
    this.model = model;
  }

  public abstract void analyze();
  
}
