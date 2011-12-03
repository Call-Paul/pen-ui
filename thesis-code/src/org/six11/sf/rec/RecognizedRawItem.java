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

  /**
   * Makes a 'no-op' recognized item. This can be used as a default recognition result in case there
   * aren't any real ones.
   */
  public static RecognizedRawItem noop() {
    return new RecognizedRawItem(false) {
      public void activate(SketchBook model) {
      }
    };
  }
}
