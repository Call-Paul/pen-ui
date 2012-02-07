package org.six11.sf;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

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

  public SafeAction addSegments(final Collection<Segment> segs) {
    return new SafeAction("Add Segments") {
      public void forward() {
        add(segs);
      }

      public void backward() {
        axe(segs);
      }
    };
  }
  
  
  private void axe(Collection<Segment> segs) {
    for (Segment seg : segs) {
      if (!seg.isSingular()) {
        model.getConstraints().removePoint(seg.getP1());//addPoint(model.nextPointName(), seg.getP1());
        model.getConstraints().removePoint(seg.getP2());//addPoint(model.nextPointName(), seg.getP2());
      }
    }
    for (Segment seg : segs) {
      model.removeGeometry(seg);
    }
  }
  
  private void add(Collection<Segment> segs) {
    for (Segment seg : segs) {
      if (!seg.isSingular()) {
        if (!SketchBook.hasName(seg.getP1())) {
          model.getConstraints().addPoint(model.nextPointName(), seg.getP1());
        } else {
          model.getConstraints().addPoint(seg.getP1());
        }
        if (!SketchBook.hasName(seg.getP2())) {
          model.getConstraints().addPoint(model.nextPointName(), seg.getP2());
        } else {
          model.getConstraints().addPoint(seg.getP2());
        }
      }
    }
    for (Segment seg : segs) {
      model.addGeometry(seg);
    }
  }
  
  public SafeAction removeSegments(final Collection<Segment> segs) {
    return new SafeAction("Remove Segments") {
      public void forward() {
        axe(segs);
      }
      
      public void backward() {
        add(segs);
      }
    };
  }

  public SafeAction split(final Segment oldSeg, final Set<Segment> newSegs) {
    SafeAction ret = new SafeAction("Split Segment") {
      public void forward() {
//        bug("split action!");
//        bug("remove old seg: " + oldSeg.typeIdPtStr());
//        bug("new segs:");
//        for (Segment ns : newSegs) {
//          bug("-- " + ns.typeIdPtStr());
//        }
        axe(Collections.singleton(oldSeg));
        add(newSegs);
      }

      public void backward() {
        axe(newSegs);
        add(Collections.singleton(oldSeg));
      }
    };
    return ret;
    
  }
}
