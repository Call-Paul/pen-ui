package org.six11.sf;

import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import static org.six11.util.Debug.num;
import static org.six11.util.Debug.bug;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class EncircleGestureFinder implements GestureFinder {

  public Gesture findGesture(Sequence seq) {
    double len = seq.length();
    int bestIdx = getNearestEncircleDist(seq);
    Pt bestPt = seq.get(bestIdx);
    double terminalDist = bestPt.distance(seq.getFirst());
    double ratio = terminalDist / len;
    
    double likelihood = 0;
    if (ratio < 0.15) {
      // return with nonzero likelihood
      likelihood = (0.15 - ratio) / 0.15;
    }
    bug("Ratio of " + num(terminalDist) + " / " + num(len) + " = " + ratio + ". Likelihood: "
        + num(likelihood));
    EncircleGesture ret = new EncircleGesture(likelihood);
    ret.setPoints(seq, 0, bestIdx);
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

}
