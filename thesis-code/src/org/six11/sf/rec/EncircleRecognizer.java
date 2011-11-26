package org.six11.sf.rec;

import static org.six11.util.Debug.num;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.six11.sf.Ink;
import org.six11.sf.SketchBook;
import org.six11.sf.SketchRecognizer;
import org.six11.sf.SketchRecognizer.Type;
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
    RecognizerPrimitive only = in.toArray(new RecognizerPrimitive[1])[0];
    Ink ink = only.getInk();
    Sequence seq = ink.getSequence();
    double bestDist = Double.MAX_VALUE;
    boolean closed = false;
    if (seq.length() > 200) {
      List<Pt> down = seq.getDownsample(50);
      for (int i=2; i < (down.size() - 1); i++) {
        Pt a = down.get(i);
        Pt b = down.get(i+1);
        Line seg = new Line(a, b);
        double dist = Functions.getDistanceBetweenPointAndSegment(down.get(0), seg);
        bug("dist: " + num(dist));
        if (dist < bestDist) {
          bestDist = dist;
          bug("improved best");
        }
      }
    }
    bug("best dist: " + num(bestDist));
    return ret;
  }

}
