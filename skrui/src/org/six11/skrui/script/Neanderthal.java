package org.six11.skrui.script;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.six11.skrui.BoundedParameter;
import org.six11.skrui.DrawingBufferRoutines;
import org.six11.skrui.SkruiScript;
import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.pen.Antipodal;
import org.six11.util.pen.ConvexHull;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.SequenceEvent;
import org.six11.util.pen.SequenceListener;

/**
 * This is a 'hello world' implementation of DrawingScript. To use it, just mention it on the Main
 * command line.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Neanderthal extends SkruiScript implements SequenceListener {

  private static final String K_SOME_THING = "some-thing";
  private static final String K_ANOTHER_THING = "another-thing";
  private static final String SCRAP = "Sequence already dealt with";

  enum Certainty {
    Yes, No, Maybe, Unknown
  }

  public static Arguments getArgumentSpec() {
    Arguments args = new Arguments();
    args.setProgramName("A Nice Hello World");
    args.setDocumentationProgram("This serves as a template for new scripts. Copy away!");

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
    defs.put(K_SOME_THING, new BoundedParameter.Double(K_SOME_THING, "Some Thing",
        "This is some sort of double-precision floating point parameter.", 0, 1, 0.75));
    defs.put(K_ANOTHER_THING, new BoundedParameter.Double(K_ANOTHER_THING, "Another Thing",
        "This is another parameter.", 1, 5, 2.5));
    return defs;
  }

  private static void bug(String what) {
    Debug.out("Neanderthal", what);
  }

  // ///
  //
  // * Put member variable declarations here. *
  //
  // //

  @Override
  public void initialize() {
    bug("Neanderthal is alive!");
    main.getDrawingSurface().getSoup().addSequenceListener(this);
    System.out.println("seq_id,n_points,area,aspect,density,area_div_aspect,class");
  }

  private double updatePathLength(Sequence seq) {
    int cursor = seq.size() - 1;
    // back up to the last point that has the 'path-length' value set, or to 0.
    while (cursor > 0 && !seq.get(cursor).hasAttribute("path-length"))
      cursor--;
    // if we're at 0, set the path-length to 0.
    if (cursor == 0) {
      seq.get(cursor).setDouble("path-length", 0);
    }
    // now zip to the end and set the cumulative path lenth based on the last known value.
    for (int i = cursor; i < seq.size() - 1; i++) {
      double dist = seq.get(cursor).distance(seq.get(cursor + 1));
      seq.get(cursor + 1).setDouble("path-length", seq.get(cursor).getDouble("path-length") + dist);
    }
    return seq.get(seq.size() - 1).getDouble("path-length");
  }

  public void handleSequenceEvent(SequenceEvent seqEvent) {
    SequenceEvent.Type type = seqEvent.getType();
    Sequence seq = seqEvent.getSeq();
    Certainty dotCertainty;
    switch (type) {
      case PROGRESS:
        double howLong = updatePathLength(seq);
        break;
      case END:
        if (seq.getAttribute(SCRAP) == null) {
          dotCertainty = detectDot(seq);
        }
        break;
    }

  }

  private Certainty detectDot(Sequence seq) {

    ConvexHull hull = new ConvexHull(seq.getPoints());
    Antipodal antipodes = new Antipodal(hull.getHull());
    List<Pt> rect = antipodes.getMinimumBoundingRect();
    rect.add(rect.get(0));
    double density = (double) seq.size() / antipodes.getArea();
    double areaPerAspect = antipodes.getArea() / antipodes.getAspectRatio();
    String clz = "Unknown";
    if (main.getArguments().hasValue("class")) {
      clz = main.getArguments().getValue("class");
    }
    System.out.print("" + seq.getId());
    System.out.print("," + seq.size());
    System.out.print("," + Debug.num(antipodes.getArea()));
    System.out.print("," + Debug.num(antipodes.getAspectRatio()));
    System.out.print("," + Debug.num(density));
    System.out.print("," + areaPerAspect);
    System.out.print("," + clz);

    Certainty ret = Certainty.Unknown;

    if (areaPerAspect < 58) {
      ret = Certainty.Yes;
    } else if (areaPerAspect < 120) {
      ret = Certainty.Maybe;
    } else if (areaPerAspect / (0.3 + density) < 120) {
      ret = Certainty.Maybe;
    } else {
      ret = Certainty.No;
    }

    DrawingBuffer db = new DrawingBuffer();
    Pt centroid = antipodes.getCentroid();
    DrawingBufferRoutines.text(db, new Pt(centroid.x + 10, centroid.y + 10), "(" + ret + ")",
        Color.GREEN.darker());
    main.getDrawingSurface().getSoup().addBuffer("dot", db);
    if (main.getArguments().hasFlag("show-result")) {
      System.out.print(" (" + ret + ")");
    }
    // System.out.print(" (" + ret + ")");
    System.out.print("\n");
    return ret;
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
}
