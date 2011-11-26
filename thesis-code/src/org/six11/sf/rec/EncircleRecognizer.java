package org.six11.sf.rec;

import java.util.Collection;
import java.util.HashSet;

import org.six11.sf.SketchBook;
import org.six11.sf.SketchRecognizer;
import org.six11.sf.SketchRecognizer.Type;

import static org.six11.util.Debug.bug;

public class EncircleRecognizer extends SketchRecognizer {

  public EncircleRecognizer(SketchBook model) {
    super(model, Type.SingleRaw);
  }

  public Collection<RecognizedItem> apply(Collection<RecognizerPrimitive> in) {
    Collection<RecognizedItem> ret = new HashSet<RecognizedItem>();
    bug("I'm called! They call me George");
    return ret;
  }

}
