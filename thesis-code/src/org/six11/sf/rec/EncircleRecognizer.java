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

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

public class EncircleRecognizer extends SketchRecognizer {

  private static final double END_FRACTION = 0.2;

  public EncircleRecognizer(SketchBook model) {
    super(model, Type.SingleRaw);
  }

  /**
   * Examines the first and last END_FRACTION of the sequence and determines the shortest distance
   * between them. So if your sequence loops around on itself it will return a small value. If it
   * does not the ends are far away from each other.
   * 
   * @return
   */
  private double getNearestEncircleDistShortSequence(Sequence seq) {
    double ret = Double.MAX_VALUE;
    double len = seq.length();
    Collection<Pt> start = new HashSet<Pt>();
    Collection<Pt> end = new HashSet<Pt>();
    double dist = 0;
    Pt prev = null;
    for (int i = 0; (i < seq.size()) && (dist < (len * END_FRACTION)); i++) {
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
    for (int i = seq.size() - 1; (i >= 0) && (dist < (len * END_FRACTION)); i--) {
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
    // Only look at the last END_FRACTION (e.g. 20%) of the sequence
    Pt start = seq.getFirst();
    int cursor = seq.size() - 1;
    int bestIdx = cursor;
    double bestDist = Double.MAX_VALUE;
    double distToEnd = 0;
    double stopDist = seq.length() * END_FRACTION;
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
    float zoom = model.getCamera().getZoom();
    // ---------------------------------------------------------------- Select Stencil
    float minLen = 200 / zoom; // minimum length for the stencil encircle gesture to be
    if (len > minLen) {
      int bestIdx = getNearestEncircleDistLongSequence(seq);
      double bestDist = seq.get(bestIdx).distance(seq.get(0));
      if (bestDist < (50 / zoom)) { // closest parts of ends have to be closer than this
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
    // ----------------------------------------------------------------- Tiny circles (e.g. latch)
    double tinyCircleDistTarget = 6.5 / zoom;
    bug("len <= minLen: " + num(len) + " <= " + num(minLen) + ": " + (len <= minLen));
    bug("roughDensity: " + num(ink.getSequence().getRoughDensity(zoom)) + " <= 2: "
        + (ink.getSequence().getRoughDensity(zoom) <= 2.0));
    bug("nearestCircDist: " + num(getNearestEncircleDistShortSequence(seq)) + " < "
        + num(tinyCircleDistTarget) + ": "
        + (getNearestEncircleDistShortSequence(seq) < tinyCircleDistTarget));
    if ((len <= minLen) && (ink.getSequence().getRoughDensity(zoom) <= 2.0)
        && (getNearestEncircleDistShortSequence(seq) < tinyCircleDistTarget)) {
      bug("doing it.");
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
        double fuzzyFactor = 4 / zoom; // area around the segment
        Collection<Segment> segs = model.findSegments(area, fuzzyFactor);
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
            model.getEditor().findStencils();
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
        model.splitSegment(seg, pt);
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
