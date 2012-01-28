package org.six11.sf;

import java.util.Collection;
import static org.six11.util.Debug.bug;

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

  public SafeAction addSegments(final Collection<SegmentDelegate> segs) {
    return new SafeAction("Add Segments") {
      public void forward() {
        for (SegmentDelegate seg : segs) {
          model.addGeometry(seg);
        }
      }

      public void backward() {
        for (SegmentDelegate seg : segs) {
          model.removeGeometry(seg);
        }
      }
    };

  }
}
