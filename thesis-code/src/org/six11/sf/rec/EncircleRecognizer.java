package org.six11.sf.rec;

import java.awt.geom.Area;
import java.util.Collection;
import java.util.HashSet;

import org.six11.sf.GuidePoint;
import org.six11.sf.Ink;
import org.six11.sf.Segment;
import org.six11.sf.SketchBook;
import org.six11.sf.SketchRecognizer;
import org.six11.sf.Stencil;
import org.six11.util.data.Lists;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

public class EncircleRecognizer extends SketchRecognizer {

  public EncircleRecognizer(SketchBook model) {
    super(model, Type.SingleRaw);
  }

  private double getNearestEncircleDistShortSequence(Sequence seq) {
    double ret = Double.MAX_VALUE;
    double len = seq.length();
    Collection<Pt> start = new HashSet<Pt>();
    Collection<Pt> end = new HashSet<Pt>();
    double dist = 0;
    Pt prev = null;
    for (int i = 0; (i < seq.size()) && (dist < (len * 0.2)); i++) {
      Pt pt = seq.get(i);
      if (dist < (len * 0.2)) {
        start.add(pt);
        if (prev != null) {
          dist = dist + prev.distance(pt);
        }
        prev = pt;
      }
    }
    dist = 0;
    prev = null;
    for (int i = seq.size() - 1; (i >= 0) && (dist < (len * 0.2)); i--) {
      Pt pt = seq.get(i);
      if (dist < (len * 0.2)) {
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

  private int getNearestEncircleDistLongSequence(Sequence seq) {
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
      throws UnsupportedOperationException {
    throw new UnsupportedOperationException("This recognizer can't do templates.");
  }

  @Override
  public RecognizedRawItem applyRaw(Ink ink) throws UnsupportedOperationException {
    RecognizedRawItem ret = RecognizedRawItem.noop();
    Sequence seq = ink.getSequence();
    final Collection<Stencil> stencilsInside = new HashSet<Stencil>();
    double len = seq.length();
    // ---------------------------------------------------------------- Select Stencil
    if (len > 200) {
      int bestIdx = getNearestEncircleDistLongSequence(seq);
      double bestDist = seq.get(bestIdx).distance(seq.get(0));
      if (bestDist < 50) {
        // TODO: should also check the relative sizes of these areas. Don't want 
        // to accidentally select something when you're really trying to create 
        // a surrounding stencil, or making a hole inside one.
        Area area = new Area(seq);
        stencilsInside.addAll(model.findStencil(area, 0.8));
        if (stencilsInside.size() > 0) {
          ret = new RecognizedRawItem(true, RecognizedRawItem.ENCIRCLE_STENCIL_TO_SELECT,
              RecognizedRawItem.FAT_DOT_REFERENCE_POINT, RecognizedRawItem.FAT_DOT_SELECT) {
            public void activate(SketchBook model) {
              model.setSelectedStencils(stencilsInside);
            };
          };
        }
      }
    }
    if ((len <= 200) && (ink.getSequence().getRoughDensity() <= 2.0)
        && (getNearestEncircleDistShortSequence(seq) < 6.5)) {
      Area area = new Area(seq);
      final Collection<Pt> points = model.findPoints(area);
      final Collection<GuidePoint> guides = model.findGuidePoints(area);
      boolean eraseGuide = false;
      if ((points.size() > 0) && (guides.size() == 1)) {
        // one or more endpoints and exactly one guide point.

        // see if all the points are coincident with the single guide point.
        final GuidePoint singleGuide = guides.toArray(new GuidePoint[1])[0];
        Pt loc = singleGuide.getLocation();
        boolean ok = true;
        for (Pt pt : points) {
          if (!pt.isSameLocation(loc)) {
            ok = false;
            break;
          }
        }
        if (ok) {
          // ---------------------------------------------------------------- Remove guide point
          // all endpoints are already at the guide point. In this case 
          // the gesture means the user wants to remove the guide point.
          ret = makeRemoveGuidePoint(singleGuide);
          eraseGuide = true;
        }
      }
      if (!eraseGuide && (points.size() == 1)) {
        // ------------------------------------------------------------------ T-junction
        Collection<Segment> segs = model.findSegments(area, 4);
        Pt pt = Lists.getOne(points);
        Collection<Segment> tabu = model.findRelatedSegments(pt);
        segs.removeAll(tabu);
        if (!segs.isEmpty()) {
          ret = makeTJunction(Lists.getOne(segs), Lists.getOne(points));
        }
      }
      if (!eraseGuide && (points.size() > 1)) {
        // ------------------------------------------------------------------ Merge points
        ret = new RecognizedRawItem(true, RecognizedRawItem.ENCIRCLE_ENDPOINTS_TO_MERGE,
            RecognizedRawItem.ENCIRCLE_STENCIL_TO_SELECT,
            RecognizedRawItem.OVERTRACE_TO_SELECT_SEGMENT) {
          public void activate(SketchBook model) {
            Pt centroid = null;
            if (guides.size() == 1) {
              GuidePoint gp = guides.toArray(new GuidePoint[1])[0];
              centroid = gp.getLocation();
              if (gp.isPinnedToSegment()) {
                Collection<Segment> related = new HashSet<Segment>();
                for (Pt pt : points) {
                  related.addAll(model.findRelatedSegments(pt));
                }
                boolean isRelated = false;
                for (Segment s : related) {
                  if (s == gp.getSegment()) {
                    isRelated = true;
                    break;
                  }
                }
                if (isRelated) {
                  gp.setLocation(centroid);
                }
              }
            } else {
              centroid = Functions.getMean(points);
            }
            Collection<Segment> related = new HashSet<Segment>();
            for (Pt pt : points) {
              model.replace(pt, centroid);
              related.addAll(model.findRelatedSegments(centroid));
            }
            model.removeSingularSegments();
            model.getConstraintAnalyzer().mergeSegments(related);
            model.getEditor().findStencils(related);
          }
        };
      } else if (guides.size() > 0) {
        ret = makeRemoveGuidePoint(guides.toArray(new GuidePoint[0]));
      }
    }

    return ret;
  }

  private RecognizedRawItem makeTJunction(final Segment seg, final Pt pt) {
    return new RecognizedRawItem(true, RecognizedRawItem.ENCIRCLE_TO_T_MERGE) {
      @Override
      public void activate(SketchBook model) {
        /*Set<Segment> babies = */model.splitSegment(seg, pt);
//        bug("* Old:" + seg.typeIdStr());
//        for (Segment babe : babies) {
//          bug("* New: " + babe.typeIdStr());
//        }
      }
    };
  }

  private RecognizedRawItem makeRemoveGuidePoint(final GuidePoint... guides) {
    return new RecognizedRawItem(true, RecognizedRawItem.ENCIRCLE_GUIDE_POINT_TO_DELETE,
        RecognizedRawItem.ENCIRCLE_STENCIL_TO_SELECT,
        RecognizedRawItem.OVERTRACE_TO_SELECT_SEGMENT, RecognizedRawItem.FAT_DOT_SELECT) {
      public void activate(SketchBook model) {
        for (GuidePoint singleGuide : guides) {
          model.removeGuidePoint(singleGuide);
        }
      }
    };
  }
}
