package org.six11.sf.rec;

import org.six11.sf.SketchBook;

public abstract class RecognizedRawItem {
  
  private boolean ok;

  public RecognizedRawItem(boolean ok) {
    this.ok = ok;
  }
  
  public boolean isOk() {
    return ok;
  }
  
  public abstract void activate(SketchBook model);
}
