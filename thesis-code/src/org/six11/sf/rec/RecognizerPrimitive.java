package org.six11.sf.rec;

import org.six11.sf.Ink;
import org.six11.sf.rec.RecognizerPrimitive.Certainty;
import org.six11.sf.rec.RecognizerPrimitive.Type;
import org.six11.util.pen.RotatedEllipse;

import static org.six11.util.Debug.bug;

public class RecognizerPrimitive {

  public static enum Type {
    Line, Arc, Dot, Ellipse
  }

  public static enum Certainty {
    Yes, No, Maybe
  }

  private Type type;
  private Ink ink;
  private int start;
  private int end;
  private Certainty cert;
  private RotatedEllipse ellipse;

  private RecognizerPrimitive(Type t, Ink ink, int startIdx, int endIdx, Certainty cert) {
    this.type = t;
    this.ink = ink;
    this.start = startIdx;
    this.end = endIdx;
    this.cert = cert;
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
    RecognizerPrimitive ret = new RecognizerPrimitive(Type.Ellipse, ink, 0, ink.getSequence().size() - 1, certainty);
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

}
