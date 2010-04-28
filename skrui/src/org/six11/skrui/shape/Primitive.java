package org.six11.skrui.shape;

import java.util.Comparator;
import java.util.Set;

import org.six11.skrui.script.Neanderthal;
import org.six11.skrui.script.Neanderthal.Certainty;
import org.six11.util.Debug;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.Vec;

/**
 * Parent class of Neanderthal primitive types: dot, ellipse, polyline components (lines and arcs).
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public abstract class Primitive implements Comparable<Primitive> {
  private static int ID_COUNTER = 0;

  public Sequence seq;
  int startIdx;
  int endIdx;
  Certainty cert;
  final int id; // = ID_COUNTER++;

  private boolean subshapeBindingFlipped;
  private boolean subshapeBindingFixed;
  private boolean fixedAngleValid;
  private double fixedAngle;
  private Vec fixedVector;
  private Line geometryLine;

  /**
   * Sorts first by start/end index, then by its default comparable(Primitive) result, which is
   * based on Primitive ID. This is necessary because there might be two primitives for the same
   * start (and end) index, e.g. Line[0, 40] and Arc[0, 40].
   */
  public static Comparator<Primitive> sortByIndex = new Comparator<Primitive>() {

    public int compare(Primitive o1, Primitive o2) {
      int ret = ((Integer) o1.startIdx).compareTo(o2.startIdx);
      if (ret == 0) {
        ret = o1.compareTo(o2);
      }
      return ret;
    }

  };

  /**
   * Makes a primitive. Note the start and end indices are INCLUSIVE.
   * 
   * @param seq
   *          The original sequence. If the sequence has an attribute Neanderthal.PRIMITIVES, it is
   *          assumed to be of type Set<Primitive>, this object is added to it.
   * @param startIdx
   * @param endIdx
   * @param cert
   *          the certainty that the user intended to draw what it is advertised to be.
   */
  public Primitive(Sequence seq, int startIdx, int endIdx, Certainty cert) {
    this(++ID_COUNTER, seq, startIdx, endIdx, cert);
  }
  
  public Primitive(int id, Sequence seq, int startIdx, int endIdx, Certainty cert) {
    this.id = id;
    this.seq = seq;
    this.startIdx = startIdx;
    this.endIdx = endIdx;
    this.cert = cert;

    this.subshapeBindingFlipped = false;
    this.subshapeBindingFixed = false;
    this.fixedAngleValid = false;

    if (seq.getAttribute(Neanderthal.PRIMITIVES) != null) {
      Set<Primitive> primitives = (Set<Primitive>) seq.getAttribute(Neanderthal.PRIMITIVES);
      primitives.add(this);
    }
    ID_COUNTER = Math.max(id, ID_COUNTER);
  }
  
  public int getId() {
    return id;
  }

  public int compareTo(Primitive other) {
    return (((Integer) id).compareTo(other.id));
  }

  public Sequence getSeq() {
    return seq;
  }

  public int getStartIdx() {
    return startIdx;
  }

  public Pt getStartPt() {
    return seq.get(startIdx);
  }

  public int getEndIdx() {
    return endIdx;
  }

  public Pt getEndPt() {
    return seq.get(endIdx);
  }

  public Pt getMidPt() {
    return getGeometryLine().getMidpoint();
  }
  
  public Pt getSubshape(String which) {
    if (which.equals("p1")) {
      return getP1();
    } else {
      return getP2();
    }
  }

  public Certainty getCert() {
    return cert;
  }

  public String toString() {
    return typeStr() + "[" + startIdx + ", " + endIdx + "]" + (cert == Certainty.Maybe ? "?" : "");
  }

  public String getShortStr() {
    return shortTypeStr() + id;
  }

  public abstract String typeStr();

  public abstract String shortTypeStr();

  public double getLength() {
    double ret = 0;
    if (endIdx - startIdx > 1) {
      ret = seq.getPathLength(startIdx, endIdx);
    }
    return ret;
  }

  public void flipSubshapeBinding() {
    if (isFlippable()) {
      subshapeBindingFlipped = !subshapeBindingFlipped;
    }
  }

  public void resetFlip() {
    subshapeBindingFlipped = false;
    subshapeBindingFixed = false;
  }

  public void setSubshapeBindingFixed(boolean v) {
    subshapeBindingFixed = v;
  }

  @SuppressWarnings("unused")
  private static void bug(String what) {
    Debug.out("Primitive", what);
  }

  public Pt getP1() {
    int idx = subshapeBindingFlipped ? endIdx : startIdx;
    return seq.get(idx);
  }

  public Pt getP2() {
    int idx = subshapeBindingFlipped ? startIdx : endIdx;
    return seq.get(idx);
  }

  public boolean isFlippable() {
    return !subshapeBindingFixed;
  }

  public boolean getFlipState() {
    return subshapeBindingFlipped;
  }

  public Vec getFixedVector() {
    if (fixedVector == null) {
      Pt start = seq.get(startIdx);
      Pt end = seq.get(endIdx);
      Pt left = start;
      Pt right = end;
      if (Pt.sortByX.compare(start, end) == 1) {
        left = end;
        right = start;
      }
      double dx = right.x - left.x;
      double dy = right.y - left.y;
      fixedVector = new Vec(dx, dy);
    }
    return fixedVector;
  }

  public double getFixedAngle() {
    if (!fixedAngleValid) {
      Vec fv = getFixedVector();
      fixedAngle = Math.atan2(fv.getY(), fv.getX());
      fixedAngleValid = true;
    }
    return fixedAngle;
  }

  public Line getGeometryLine() {
    if (geometryLine == null) {
      geometryLine = new Line(getStartPt(), getEndPt());
    }
    return geometryLine;
  }

}
