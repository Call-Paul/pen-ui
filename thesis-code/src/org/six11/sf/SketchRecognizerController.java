package org.six11.sf;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.OperationNotSupportedException;

import org.six11.sf.rec.RecognizedItem;
import org.six11.sf.rec.RecognizedRawItem;
import org.six11.sf.rec.RecognizerPrimitive;
import org.six11.sf.rec.RecognizerPrimitive.Certainty;
import org.six11.util.pen.Antipodal;
import org.six11.util.pen.ConvexHull;
import org.six11.util.pen.Pt;
import org.six11.util.pen.RotatedEllipse;
import org.six11.util.pen.Sequence;

public class SketchRecognizerController {

  private SketchBook model;
  private List<SketchRecognizer> recognizers;
  private List<SketchRecognizer> rawRecognizersFinished;
  private Map<Ink, Set<RecognizerPrimitive>> allPrimitives;

  public SketchRecognizerController(SketchBook model) {
    this.model = model;
    this.recognizers = new ArrayList<SketchRecognizer>();
    this.rawRecognizersFinished = new ArrayList<SketchRecognizer>();
    this.allPrimitives = new HashMap<Ink, Set<RecognizerPrimitive>>();
  }

  public Collection<RecognizedItem> analyzeRecent() {
    allPrimitives.clear();
    // Create a pool of primitives for recognizers to use based on current rough ink.
    for (Ink ink : model.ink) {
      extractPrimitives(ink); // appends to allPrimitives
    }
    Set<RecognizedItem> success = new HashSet<RecognizedItem>();
    Set<RecognizerPrimitive> prims = extractPrimitives();
    try {
      for (SketchRecognizer rec : recognizers) {
        success.addAll(rec.applyTemplate(prims));
      }
    } catch (OperationNotSupportedException e) {
      e.printStackTrace();
    }
    return success;
  }

  private Set<RecognizerPrimitive> extractPrimitives() {
    Set<RecognizerPrimitive> everybody = new HashSet<RecognizerPrimitive>();
    for (Set<RecognizerPrimitive> prims : allPrimitives.values()) {
      everybody.addAll(prims);
    }
    return everybody;
  }

  private Set<RecognizerPrimitive> extractPrimitives(Ink ink) {
    if (!allPrimitives.containsKey(ink)) {
      Set<RecognizerPrimitive> prims = new HashSet<RecognizerPrimitive>();
      prims.addAll(extractLinesAndArcs(ink));
      prims.addAll(extractDot(ink));
      prims.addAll(extractEllipse(ink));
      allPrimitives.put(ink, prims);
    }
    return allPrimitives.get(ink);
  }

  private Collection<RecognizerPrimitive> extractEllipse(Ink ink) {
    Set<RecognizerPrimitive> ret = new HashSet<RecognizerPrimitive>();

    // only identify ellipses that are past a certain size
    Pt start = ink.seq.getFirst();
    updatePathLength(ink.seq);
    double totalLength = ink.seq.getLast().getDouble("path-length");
    if (totalLength > 50) {
      // Determine if this is a closed shape.
      double lengthThreshold = totalLength * 0.5;
      double endpointDistThreshold = totalLength * 0.30;
      double closestDist = Double.MAX_VALUE;
      Pt closestPoint = null;
      for (Pt pt : ink.seq) {
        double toThisPoint = pt.getDouble("path-length");
        if (toThisPoint > lengthThreshold) {
          double thisDist = start.distance(pt);
          if (thisDist < endpointDistThreshold && thisDist < closestDist) {
            closestPoint = pt;
            closestDist = thisDist;
          }
        }
      }

      if (closestPoint != null) { // is only non-null if it is a closed shape.
        ConvexHull hull = new ConvexHull(ink.seq.getPoints());
        Antipodal antipodes = new Antipodal(hull.getHull());
        Pt centroid = antipodes.getCentroid();
        double angle = antipodes.getAngle();
        double a = antipodes.getFirstDimension() / 2;
        double b = antipodes.getSecondDimension() / 2;
        RotatedEllipse re = new RotatedEllipse(centroid, a, b, angle);
        double errorSum = 0;
        for (Pt pt : ink.seq) {
          Pt intersect = re.getCentroidIntersect(pt);
          double intersectDist = intersect.distance(pt);
          errorSum += intersectDist * intersectDist;
        }
        double normalizedError = errorSum / totalLength;
        double area = re.getArea();
        double areaError = errorSum / area;
        boolean punishLazyPen = closestDist > (endpointDistThreshold / 2);
        if (areaError < 0.4) {
          if (punishLazyPen) {
            ret.add(RecognizerPrimitive.makeEllipse(ink, re, Certainty.Maybe));
          } else {
            ret.add(RecognizerPrimitive.makeEllipse(ink, re, Certainty.Yes));
          }
        } else if (normalizedError < 9) {
          if (punishLazyPen) {
            ret.add(RecognizerPrimitive.makeEllipse(ink, re, Certainty.Maybe));
          } else {
            ret.add(RecognizerPrimitive.makeEllipse(ink, re, Certainty.Yes));
          }
        } else if (areaError < 0.9) {
          ret.add(RecognizerPrimitive.makeEllipse(ink, re, Certainty.Maybe));
        } else if (normalizedError < 20) {
          ret.add(RecognizerPrimitive.makeEllipse(ink, re, Certainty.Maybe));
        } else {
          // no action
        }
      }
    }

    return ret;
  }

  private Collection<RecognizerPrimitive> extractDot(Ink ink) {
    Set<RecognizerPrimitive> ret = new HashSet<RecognizerPrimitive>();
    ConvexHull hull = new ConvexHull(ink.seq.getPoints());
    Antipodal antipodes = new Antipodal(hull.getHull());
    double density = (double) ink.seq.size() / antipodes.getArea();
    double areaPerAspect = antipodes.getArea() / antipodes.getAspectRatio();
    if (areaPerAspect < 58) {
      ret.add(RecognizerPrimitive.makeDot(ink, Certainty.Yes));
    } else if (areaPerAspect < 120) {
      ret.add(RecognizerPrimitive.makeDot(ink, Certainty.Maybe));
    } else if (areaPerAspect / (0.3 + density) < 120) {
      ret.add(RecognizerPrimitive.makeDot(ink, Certainty.Maybe));
    } else {
      // no action
    }
    return ret;
  }

  /**
   * Extract portions of ink strokes that look like lines.
   */
  private Collection<RecognizerPrimitive> extractLinesAndArcs(Ink ink) {
    Set<RecognizerPrimitive> ret = new HashSet<RecognizerPrimitive>();
    List<Integer> corners = (List<Integer>) ink.seq.getAttribute(CornerFinder.SEGMENT_JUNCTIONS);
    for (int i = 0; i < corners.size() - 1; i++) {
      int a = corners.get(i);
      int b = corners.get(i + 1);
      Pt ptA = ink.seq.get(a);
      Pt ptB = ink.seq.get(b);
      double straightLength = ptA.distance(ptB);
      double curvilinearLength = ink.seq.getPathLength(a, b);
      double lineLenDivCurviLen = curvilinearLength / straightLength;
      if (lineLenDivCurviLen < 1.07) {
        ret.add(RecognizerPrimitive.makeLine(ink, a, b, Certainty.Yes));
        ret.add(RecognizerPrimitive.makeArc(ink, a, b, Certainty.Maybe));
      } else if (lineLenDivCurviLen < 1.27) {
        ret.add(RecognizerPrimitive.makeLine(ink, a, b, Certainty.Maybe));
        ret.add(RecognizerPrimitive.makeArc(ink, a, b, Certainty.Maybe));
      } else {
        ret.add(RecognizerPrimitive.makeArc(ink, a, b, Certainty.Yes));
      }
    }
    return ret;
  }

  private double updatePathLength(Sequence seq) {
    int cursor = seq.size() - 1;
    while (cursor > 0 && !seq.get(cursor).hasAttribute("path-length")) {
      cursor--;
    }
    if (cursor == 0) {
      seq.get(cursor).setDouble("path-length", 0);
    }
    for (int i = cursor; i < seq.size() - 1; i++) {
      double dist = seq.get(i).distance(seq.get(i + 1));
      double v = seq.get(i).getDouble("path-length") + dist;
      seq.get(i + 1).setDouble("path-length", v);
    }
    return seq.get(seq.size() - 1).getDouble("path-length");
  }

  public void add(SketchRecognizer rec) {
    switch (rec.getType()) {
      case Standard:
        recognizers.add(rec);
        break;
      case SingleRaw:
        rawRecognizersFinished.add(rec);
        break;
      default:
        bug("No bucket for sketch recognizer of type " + rec.getType());
    }
  }

  public Collection<RecognizedRawItem> analyzeSingleRaw(Ink ink) {
    Collection<RecognizedRawItem> results = new HashSet<RecognizedRawItem>();
    try {
      for (SketchRecognizer rec : rawRecognizersFinished) {
        results.add(rec.applyRaw(ink));
      }
    } catch (OperationNotSupportedException e) {
      e.printStackTrace();
    }
    return results;
  }

}
