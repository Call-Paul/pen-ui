package org.six11.skrui.script;

import java.util.Set;

import org.six11.skrui.script.Neanderthal.Certainty;
import org.six11.util.Debug;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

/**
 * Parent class of Neanderthal primitive types: dot, ellipse, polyline components (lines and arcs).
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public abstract class Primitive implements Comparable<Primitive> {
  private static int ID_COUNTER = 0;

  Sequence seq;
  int startIdx;
  int endIdx;
  Certainty cert;
  int id = ID_COUNTER++;

  private boolean subshapeBindingFlipped;
  private boolean subshapeBindingFixed;

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
  @SuppressWarnings("unchecked")
  public Primitive(Sequence seq, int startIdx, int endIdx, Certainty cert) {
    this.seq = seq;
    this.startIdx = startIdx;
    this.endIdx = endIdx;
    this.cert = cert;

    this.subshapeBindingFlipped = false;
    this.subshapeBindingFixed = false;

    if (seq.getAttribute(Neanderthal.PRIMITIVES) != null) {
      Set<Primitive> primitives = (Set<Primitive>) seq.getAttribute(Neanderthal.PRIMITIVES);
      primitives.add(this);
    }
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

  public Double getLength() {
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

}
