package org.six11.sf;

import java.util.Collection;
import java.util.Set;

import javax.naming.OperationNotSupportedException;

import org.six11.sf.rec.RecognizedItem;
import org.six11.sf.rec.RecognizedRawItem;
import org.six11.sf.rec.RecognizerPrimitive;

public abstract class SketchRecognizer {

  protected SketchBook model;

  public enum Type {
    /**
     * A standard recognizer works on heterogenous, multi-stroke primitives.
     */
    Standard,

    /**
     * A single raw recognizer expects to be used (apply()) with a single RecognizerPrimitive that
     * is Raw. These are used immediately after the pen is lifted to recognize things that should be
     * acted on immediately, such as encircle gestures.
     */
    SingleRaw,

    /**
     * A 'progress raw' recognizer is called as the pen is in motion. Because analysis happens in
     * the swing thread this should execute very quickly.
     */
    ProgressRaw
  }

  protected Type type;
  
  public SketchRecognizer(SketchBook model, Type t) {
    this.model = model;
    this.type = t;
  }
  
  public Type getType() {
    return type;
  }

  public abstract Collection<RecognizedItem> applyTemplate(Collection<RecognizerPrimitive> in) throws OperationNotSupportedException;
  
  public abstract RecognizedRawItem applyRaw(Ink ink) throws OperationNotSupportedException;

}
