package org.six11.skrui.script;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import org.six11.skrui.BoundedParameter;
import org.six11.skrui.SkruiScript;
import org.six11.skrui.domain.Domain;
import org.six11.skrui.domain.Shape;
import org.six11.skrui.domain.ShapeRenderer;
import org.six11.skrui.domain.ShapeTemplate;
import org.six11.skrui.domain.SimpleDomain;
import org.six11.skrui.shape.Primitive;
import org.six11.skrui.shape.PrimitiveEvent;
import org.six11.skrui.shape.PrimitiveListener;
import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

/**
 * This is a 'hello world' implementation of DrawingScript. To use it, just mention it on the Main
 * command line.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Recognition extends SkruiScript implements PrimitiveListener {

  private static final String K_COHORT_TIMEOUT = "cohort-timeout"; // 1300;
  private static final String K_COHORT_LENGTH_MULT = "cohort-length-mult"; // 0.3

  // ///
  //
  // * Put member variable declarations here. *
  //
  // //
  Domain domain;
  Neanderthal data;
  List<Shape> recognizedShapes;

  /**
   * After a strokes primitives have been found, we can try to do recognition. In order to do that
   * we need a reasonably inclusive set of related primitives. This includes all primitives that
   * were just made (in the input 'recent' set), as well as previously drawn elements that are
   * spatially or temporally close.
   */
  private Set<Primitive> getCohorts(Set<Primitive> recent) {
    Set<Primitive> ret = new HashSet<Primitive>();
    ret.addAll(recent);
    for (Primitive source : recent) {
      ret.addAll(getNear(source, source.getLength()
          * main.getParam(K_COHORT_LENGTH_MULT).getDouble()));
    }
    List<Sequence> timeRecent = data.getTimeGraph().getRecent(
        main.getParam(K_COHORT_TIMEOUT).getInt());
    for (Sequence s : timeRecent) {
      ret.addAll(Neanderthal.getPrimitiveSet(s));
    }
    return ret;
  }

  @SuppressWarnings("unchecked")
  private Set<Primitive> getNear(Primitive source, double dist) {
    Set<Primitive> ret = new HashSet<Primitive>();
    Set<Pt> out = new HashSet<Pt>();
    for (int i = source.getStartIdx(); i <= source.getEndIdx(); i++) {
      out.addAll(data.getAllPoints().getNear(source.getSeq().get(i), dist));
    }
    for (Pt pt : out) {
      SortedSet<Primitive> pointPrims = (SortedSet<Primitive>) pt
          .getAttribute(Neanderthal.PRIMITIVES);
      if (pointPrims != null) {
        ret.addAll(pointPrims);
      }
    }
    return ret;
  }

  private Set<Shape> merge(List<Shape> results) {
    Set<Shape> unique = new HashSet<Shape>();
    for (Shape s : results) {
      if (!recognizedShapes.contains(s)) {
        unique.add(s);
        recognizedShapes.add(s);
      }
    }
    return unique;
  }

  @Override
  public void initialize() {
    bug("Recognition is alive!");
    recognizedShapes = new ArrayList<Shape>();
    data = (Neanderthal) main.getScript("Neanderthal");
    domain = new SimpleDomain("Simple domain", data);
    data.addPrimitiveListener(this);
  }

  public Map<String, BoundedParameter> initializeParameters(Arguments args) {
    Map<String, BoundedParameter> params = copyParameters(getDefaultParameters());
    for (String k : params.keySet()) {
      if (args.hasFlag(k)) {
        if (args.hasValue(k)) {
          params.get(k).setValue(args.getValue(k));
          bug("Set " + params.get(k).getHumanReadableName() + " to " + params.get(k).getValueStr());
        } else {
          params.get(k).setValue("true");
          bug("Set " + params.get(k).getHumanReadableName() + " to " + params.get(k).getValueStr());
        }
      }
    }
    return params;
  }

  public static Arguments getArgumentSpec() {
    Arguments args = new Arguments();
    args.setProgramName("Sketch Recognition");
    args.setDocumentationProgram("Turns on box-and-arrow sketch recognition.");

    Map<String, BoundedParameter> defs = getDefaultParameters();
    for (String k : defs.keySet()) {
      BoundedParameter p = defs.get(k);
      args.addFlag(p.getKeyName(), ArgType.ARG_OPTIONAL, ValueType.VALUE_REQUIRED, p
          .getDocumentation()
          + " Defaults to " + p.getValueStr() + ". ");
    }
    return args;
  }

  public static Map<String, BoundedParameter> getDefaultParameters() {
    Map<String, BoundedParameter> defs = new HashMap<String, BoundedParameter>();
    defs.put(K_COHORT_TIMEOUT, new BoundedParameter.Integer(K_COHORT_TIMEOUT, "Cohort timeout",
        "Maximum time (milliseconds) that elapses between strokes that are "
            + "considered together for recognition", 1300));
    defs.put(K_COHORT_LENGTH_MULT, new BoundedParameter.Double(K_COHORT_LENGTH_MULT,
        "Cohort distance multiplier", "Multiplier of distance between strokes that are "
            + "considered together for recognition. This is multiplied with "
            + "the length of a pen stroke.", 0.01, 1.0, 0.3));
    return defs;
  }

  private static void bug(String what) {
    Debug.out("Recognition", what);
  }

  private void drawNewShapes(Collection<Shape> newShapes) {
    if (newShapes.size() > 0) {
      DrawingBuffer db = main.getBuffer("2");
      if (db == null) {
        db = new DrawingBuffer();
        main.addBuffer("2", db);
      }
      for (Shape s : newShapes) {
        ShapeRenderer ren = domain.getRenderer(s.getName());
        if (ren != null) {
          ren.draw(db, s);
        }
      }
    }
  }

  public void handlePrimitiveEvent(PrimitiveEvent ev) {
    Set<Primitive> recent = ev.getPrims();
    Set<Primitive> cohorts = getCohorts(recent);
    long start = System.currentTimeMillis();
    List<Shape> results;
    Set<Shape> newShapes = new HashSet<Shape>();
    for (ShapeTemplate st : domain.getTemplates()) {
      results = st.apply(cohorts);
      newShapes.addAll(merge(results));
    }
    long end = System.currentTimeMillis();
    bug("Applied " + domain.getTemplates().size() + " templates to " + cohorts.size()
        + " primitive shapes in " + (end - start) + " ms.");
    drawNewShapes(newShapes);

  }
}
