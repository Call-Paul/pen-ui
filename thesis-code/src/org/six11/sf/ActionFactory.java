package org.six11.sf;

public class ActionFactory {
  
  private SketchBook model;
  
  public ActionFactory(SketchBook model) {
    this.model = model;
  }

  public SafeAction addInk(final Ink ink) {
    SafeAction ret = new SafeAction("Add Ink") {
      public void forward() {
        model.addInk(ink);
      }
      public void backward() {
        model.removeInk(ink);
      }
    };
    return ret;
  }
}
