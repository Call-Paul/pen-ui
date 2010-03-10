package org.six11.skrui.script;

import java.util.Set;

import org.six11.skrui.script.Neanderthal.Certainty;
import org.six11.util.pen.Sequence;

/**
 * Parent class of Neanderthal primitive types: dot, ellipse, polyline components (lines and arcs).
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public abstract class Primitive {
  Sequence originalSequence;
  int startIdx;
  int endIdx;
  Certainty cert;

  /**
   * Makes a primitive. Note the start and end indices are INCLUSIVE.
   * 
   * @param seq
   *          The original sequence. If the sequence has an attribute Neanderthal.PRIMITIVES, it is
   *          assumed to be of type Set<Primitive>, this object is added to it.
   * @param startIdx
   * @param endIdx
   * @param cert the certainty that the user intended to draw what it is advertised to be.
   */
  @SuppressWarnings("unchecked")
  public Primitive(Sequence seq, int startIdx, int endIdx, Certainty cert) {
    this.originalSequence = seq;
    this.startIdx = startIdx;
    this.endIdx = endIdx;
    this.cert = cert;

    if (seq.getAttribute(Neanderthal.PRIMITIVES) != null) {
      Set<Primitive> primitives = (Set<Primitive>) seq.getAttribute(Neanderthal.PRIMITIVES);
      primitives.add(this);
    }
  }
  
  public String toString() {
    return typeStr() + "[" + startIdx + ", " + endIdx + "]" + (cert == Certainty.Maybe ? "?" : "");
  }
  
  public abstract String typeStr();
}
