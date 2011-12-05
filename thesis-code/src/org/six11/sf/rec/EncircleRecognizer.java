package org.six11.sf.rec;

import static org.six11.util.Debug.num;

import java.awt.geom.Area;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import javax.naming.OperationNotSupportedException;

import org.six11.sf.Ink;
import org.six11.sf.Segment;
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

  //  public Collection<RecognizedItem> apply(Collection<RecognizerPrimitive> in) {
  //    Collection<RecognizedItem> ret = new HashSet<RecognizedItem>();
  //    
  //    
  //    return ret;
  //  }

  private double getNearestEncircleDist2(Sequence seq) {
    double ret = Double.MAX_VALUE;
    double len = seq.length();
    Collection<Pt> start = new HashSet<Pt>();
    Collection<Pt> end = new HashSet<Pt>();
    double dist = 0;
    Pt prev = null;
    for (int i = 0; i < seq.size() && dist < (len * 0.2); i++) {
      Pt pt = seq.get(i);
      if (dist < len * 0.2) {
        start.add(pt);
        if (prev != null) {
          dist = dist + prev.distance(pt);
        }
        prev = pt;
      }
    }
    dist = 0;
    prev = null;
    for (int i = seq.size() - 1; i >= 0 && dist < (len * 0.2); i--) {
      Pt pt = seq.get(i);
      if (dist < len * 0.2) {
        end.add(pt);
        if (prev != null) {
          dist = dist + prev.distance(pt);
        }
        prev = pt;
      }
    }
    Pt[] pair = null;    
    for (Pt s : start) {
      for (Pt e : end) {
        if (pair == null) {
          pair = new Pt[] {
              s, e
          };
          ret = pair[0].distance(pair[1]);
        } else {
          if (s.distance(e) < ret) {
            pair[0] = s;
            pair[1] = e;
            ret = pair[0].distance(pair[1]);
          }
        }
      }
    }
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
  public Collection<RecognizedItem> applyTemplate(Collection<RecognizerPrimitive> in)
      throws OperationNotSupportedException {
    throw new OperationNotSupportedException("This recognizer can't do templates.");
  }

  @Override
  public RecognizedRawItem applyRaw(Ink ink) throws OperationNotSupportedException {
    RecognizedRawItem ret = RecognizedRawItem.noop();
    Sequence seq = ink.getSequence();
    final Collection<Stencil> stencilsInside = new HashSet<Stencil>();
    double len = seq.length();
    if (len > 200) {
      int bestIdx = getNearestEncircleDist(seq);
      double bestDist = seq.get(bestIdx).distance(seq.get(0));
      if (bestDist < 50) {
        Area area = new Area(seq);
        stencilsInside.addAll(model.findStencil(area, 0.8));
        if (stencilsInside.size() > 0) {
          ret = new RecognizedRawItem(stencilsInside.size() > 0) {
            public void activate(SketchBook model) {
              model.setSelected(stencilsInside);
            };
          };
        }
      }
    }

    if (len <= 200 && getNearestEncircleDist2(seq) < 6.5) {
      Area area = new Area(seq);
      final Collection<Pt> points = model.findPoints(area);
      if (points.size() > 0) {
        ret = new RecognizedRawItem(true) {
          public void activate(SketchBook model) {
            Pt centroid = Functions.getMean(points);
            bug("Replacing " + points.size() + " points with " + num(centroid));
            Collection<Segment> related = new HashSet<Segment>();
            for (Pt pt : points) {
              model.replace(pt, centroid);
              related.addAll(model.findRelatedSegments(centroid));
            }
            bug("Found " + related.size() + " related segments.");
            model.getEditor().findStencils(related);
            model.getEditor().drawStuff();
          }
        };
      }
    }

    return ret;
  }
}
