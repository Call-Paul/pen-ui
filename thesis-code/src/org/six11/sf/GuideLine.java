package org.six11.sf;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.List;

import static java.lang.Math.ceil;
import static java.lang.Math.toDegrees;

import org.six11.sf.rec.RecognizedItemTemplate;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.Vec;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

public class GuideLine extends Guide {

  Pt a, b;
  Line myLine;

  public GuideLine(Pt a, Pt b) {
    this.a = a;
    this.b = b;
    if (b != null) {
      myLine = new Line(a, b);
    }
  }

  @Override
  public void draw(DrawingBuffer buf, Pt hoverPoint, Color in, Rectangle bounds) {
    if (hoverPoint != null) {
      double residual = 0;
      if (b != null) {
        residual = Functions.getDistanceBetweenPointAndLine(hoverPoint, myLine);
      }
      float alpha = (float) RecognizedItemTemplate.getAlpha(residual, 5, 30, 0.1);
      Color c = new Color(in.getRed(), in.getGreen(), in.getBlue(), (int) (alpha * 255f));
      if (b == null) {
        myLine = new Line(a, hoverPoint);
      }
      DrawingBufferRoutines.screenLine(buf, bounds, myLine, c, 1.2);
    }
  }

  public boolean claims(Sequence seq, int start, int end) {
    boolean ret = false;
    if (b == null) {
      myLine = new Line(a, fixedHover);
    }
    Vec myVec = new Vec(myLine.getStart(), myLine.getEnd());
    Vec seqVec = new Vec(seq.get(start), seq.get(end));
    double ang1 = Math.abs(Functions.getSignedAngleBetween(myVec, seqVec));
    double ang2 = Math.abs(ang1 - Math.PI);
    double ang = Math.min(ang1, ang2);
    if (toDegrees(ang) < 4.0) {
      double totalError = 0;
      for (int i = start; i <= end; i++) {
        Pt pt = seq.get(i);
        double dist = Functions.getDistanceBetweenPointAndLine(pt, myLine);
        totalError = totalError + (dist * dist);
      }
      double sampleError = totalError / ((end - start) + 1);
      if (sampleError < 20) {
        bug("guide line can claim this");
        ret = true;
      }
    }
    return ret;
  }

  public String toString() {
    return "line: " + num(a) + " to " + num(b);
  }

  public boolean isDynamic() {
    return (b == null);
  }

  public Guide getFixedCopy() {
    return new GuideLine(a, isDynamic() ? fixedHover : b);
  }

  public Segment adjust(Ink ink, int start, int end) {
    double segLength = ink.seq.getPathLength(start, end);
    int numPatches = (int) ceil(segLength / CornerFinder.minPatchSize);
    double patchLength = segLength / (double) numPatches;
    List<Pt> patch = Functions.getCurvilinearNormalizedSequence(ink.seq, start, end, patchLength)
        .getPoints();
    Segment ret = new LineSegment(ink, patch, start == 0, end == ink.seq.size() - 1);
    if (isDynamic()) {
      myLine = new Line(a, fixedHover);
    }
    Pt nearestP1 = Functions.getNearestPointOnLine(ret.getP1(), myLine);
    Pt nearestP2 = Functions.getNearestPointOnLine(ret.getP2(), myLine);
    ret.replace(ret.getP1(), nearestP1);
    ret.replace(ret.getP2(), nearestP2);
    return ret;
  }
}
