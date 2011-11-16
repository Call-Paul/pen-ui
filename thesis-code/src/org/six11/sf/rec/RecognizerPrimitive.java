package org.six11.sf.rec;

import org.six11.sf.Ink;
import org.six11.util.pen.Pt;
import org.six11.util.pen.RotatedEllipse;

import static org.six11.util.Debug.bug;

public class RecognizerPrimitive implements Comparable<RecognizerPrimitive> {

  private static int ID_COUNTER = 0;

  public static enum Type {
    Line, Arc, Dot, Ellipse
  }

  public static enum Certainty {
    /**
     * The proposition is most likely true.
     */
    Yes, 
    
    /**
     * The proposition is most likely false.
     */
    No, 
    
    /**
     * The proposition might be true. This is a weaker form of 'Yes'.
     */
    Maybe, 
    
    /**
     * The proposition can't be evaluated.
     */
    Unknown
  }

  public final int id; // = ID_COUNTER++;
  
  private Type type;
  private Ink ink;
  private int start;
  private int end;
  private Certainty cert;
  private RotatedEllipse ellipse;
  private boolean subshapeBindingFixed;
  private boolean subshapeBindingFlipped;

  private RecognizerPrimitive(Type t, Ink ink, int startIdx, int endIdx, Certainty cert) {
    this.id = ID_COUNTER++;
    this.type = t;
    this.ink = ink;
    this.start = startIdx;
    this.end = endIdx;
    this.cert = cert;
    this.subshapeBindingFlipped = false;
    this.subshapeBindingFixed = false;

    if (cert == Certainty.Yes) {
      bug(type.toString() + "!");
    }
  }

  public static RecognizerPrimitive makeLine(Ink ink, int a, int b, Certainty certainty) {
    return new RecognizerPrimitive(Type.Line, ink, a, b, certainty);
  }

  public static RecognizerPrimitive makeArc(Ink ink, int a, int b, Certainty certainty) {
    return new RecognizerPrimitive(Type.Arc, ink, a, b, certainty);
  }

  public static RecognizerPrimitive makeDot(Ink ink, Certainty certainty) {
    return new RecognizerPrimitive(Type.Dot, ink, 0, ink.getSequence().size() - 1, certainty);
  }

  public static RecognizerPrimitive makeEllipse(Ink ink, RotatedEllipse re, Certainty certainty) {
    RecognizerPrimitive ret = new RecognizerPrimitive(Type.Ellipse, ink, 0, ink.getSequence()
        .size() - 1, certainty);
    ret.setEllipse(re);
    return ret;
  }

  private void setEllipse(RotatedEllipse re) {
    this.ellipse = re;
  }

  public Type getType() {
    return type;
  }

  public Ink getInk() {
    return ink;
  }

  public int getStart() {
    return start;
  }

  public int getEnd() {
    return end;
  }

  public Certainty getCert() {
    return cert;
  }

  public RotatedEllipse getEllipse() {
    return this.ellipse;
  }

  public int compareTo(RecognizerPrimitive other) {
    return (((Integer) id).compareTo(other.id));
  }

  public Pt getP1() {
    int idx = subshapeBindingFlipped ? getEnd() : getStart();
    return ink.getSequence().get(idx);
  }
  
  public Pt getP2() {
    int idx = subshapeBindingFlipped ? getStart() : getEnd();
    return ink.getSequence().get(idx);
  }
  
  public Pt getSubshape(String which) {
    if (which.equals("p1")) {
      return getP1();
    } else {
      return getP2();
    }
  }

  public String toString() {
    return type + "_" + id;
  }

  public boolean isFlippable() {
    return !subshapeBindingFixed;
  }
  
  public boolean getFlipState() {
    return subshapeBindingFlipped;
  }

  public void setSubshapeBindingFixed(boolean b) {
    subshapeBindingFixed = b;
  }
  
  public void flipSubshapeBinding() {
    if (isFlippable()) {
      subshapeBindingFlipped = !subshapeBindingFlipped;
    }
  }

  public double getLength() {
    return getP1().distance(getP2());
  }

}
