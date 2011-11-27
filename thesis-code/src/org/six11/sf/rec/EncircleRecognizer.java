package org.six11.sf.rec;

import static org.six11.util.Debug.num;

import java.awt.geom.Area;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import javax.naming.OperationNotSupportedException;

import org.six11.sf.Ink;
import org.six11.sf.SketchBook;
import org.six11.sf.SketchRecognizer;
import org.six11.sf.SketchRecognizer.Type;
import org.six11.sf.Stencil;
import org.six11.sf.StencilFinder;
import org.six11.util.pen.ConvexHull;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

import static org.six11.util.Debug.bug;

public class EncircleRecognizer extends SketchRecognizer {

  public EncircleRecognizer(SketchBook model) {
    super(model, Type.SingleRaw);
  }

  public Collection<RecognizedItem> apply(Collection<RecognizerPrimitive> in) {
    Collection<RecognizedItem> ret = new HashSet<RecognizedItem>();
    
    
    return ret;
  }

  private int getNearestEncircleDist(Sequence seq) {
    // start at the end and look for the point seq[i] that is closest to the first point seq[0].
    // Only look at the last 20% of the sequence
    Pt start = seq.getFirst();
    int cursor = seq.size() - 1;
    int bestIdx = cursor;
    double bestDist = Double.MAX_VALUE;
    double distToEnd = 0;
    double stopDist = seq.length() * 0.2;
    Pt prev = null;
    while (distToEnd < stopDist) {
      Pt here = seq.get(cursor);
      double thisDist = here.distance(start);
      if (thisDist < bestDist) {
        bestDist = thisDist;
        bestIdx = cursor;
      }
      if (prev != null) {
        distToEnd = distToEnd + prev.distance(here);
      }
      prev = here;
      cursor--;
    }
    return bestIdx;
  }

  @Override
  public Collection<RecognizedItem> applyTemplate(Collection<RecognizerPrimitive> in) throws OperationNotSupportedException {
    throw new OperationNotSupportedException("This recognizer can't do templates.");
  }

  @Override
  public RecognizedRawItem applyRaw(Ink ink)  throws OperationNotSupportedException {
    Sequence seq = ink.getSequence();
    final Collection<Stencil> inside = new HashSet<Stencil>();
    if (seq.length() > 200) {
      int bestIdx = getNearestEncircleDist(seq);
      double bestDist = seq.get(bestIdx).distance(seq.get(0));
      if (bestDist < 50) {
        Area area = new Area(seq);
        inside.addAll(model.findStencil(area, 0.8));
      }
    }
    RecognizedRawItem ret = new RecognizedRawItem(inside.size() > 0) {
      public void activate(SketchBook model) {
        bug("OK, select these " + inside.size() + " stencils:");
        for (Stencil s : inside) {
          bug("  " + StencilFinder.n(s.getPath()));
        }
        model.setSelected(inside);
      };
    };
    return ret;
  }
}
