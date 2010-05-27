package org.six11.skrui.script;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.six11.skrui.BoundedParameter;
import org.six11.skrui.SkruiScript;
import org.six11.skrui.charrec.NBestHit;
import org.six11.skrui.charrec.RasterDisplay;
import org.six11.skrui.shape.Stroke;
import org.six11.skrui.ui.LooseDrawingSurface;
import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.data.GaussianHat;
import org.six11.util.gui.ApplicationFrame;
import org.six11.util.gui.Components;
import org.six11.util.gui.Placeholder;
import org.six11.util.layout.FrontEnd;
import org.six11.util.layout.Holder;

import static org.six11.util.layout.FrontEnd.ROOT;
import static org.six11.util.layout.FrontEnd.N;
import static org.six11.util.layout.FrontEnd.E;
import static org.six11.util.layout.FrontEnd.W;
import static org.six11.util.layout.FrontEnd.S;
import org.six11.util.pen.SequenceEvent;
import org.six11.util.pen.SequenceListener;

public class PrintRecognizer extends SkruiScript implements SequenceListener {

  public static Arguments getArgumentSpec() {
    Arguments args = new Arguments();
    args.setProgramName("Handwriting (print) recognizer");
    args.setDocumentationProgram("A handwriting recognizer that handles printed symbols "
        + "(alphanumerics and punctuation).");

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
    return defs;
  }

  private static void bug(String what) {
    Debug.out("PrintRecognizer", what);
  }

  // ///
  //
  // * Put member variable declarations here. *
  //
  // //
  ApplicationFrame af;
  LooseDrawingSurface lds;
  Random random = new Random(System.currentTimeMillis());

  @Override
  public void initialize() {
    bug("PrintRecognizer is alive!");
  }

  public void showUI() {
    if (af == null) {
      createUI(); // creates and populates af
    }
    af.setVisible(true);
  }

  private void createUI() {
    af = new ApplicationFrame("Handwriting Recognizer Training Tool");
    af.setNoQuitOnClose();
    lds = new LooseDrawingSurface();
    lds.setPreferredSize(new Dimension(128, 128));
    lds.addPropertyChangeListener(LooseDrawingSurface.PEN_STROKE_FINISHED,
        new PropertyChangeListener() {
          public void propertyChange(PropertyChangeEvent evt) {
            recognize();
          }
        });
    JLabel resultsLabel = new JLabel("Recognition Results:");
    JPanel nBestPanel = new JPanel();
    nBestPanel.setLayout(new FlowLayout());
    String chars = "RAHBPQO";
    for (int i = 0; i < chars.length(); i++) {
      nBestPanel.add(new NBestHit("" + chars.charAt(i), 0.83434289347));// new JButton("" +
                                                                        // chars.charAt(i)));
    }
    JPanel featureImagePanel = new JPanel();
    GridLayout grid = new GridLayout(1, 5);
    grid.setHgap(6);
    grid.setVgap(6);
    featureImagePanel.setLayout(grid);
    for (int i = 1; i <= 5; i++) {
      RasterDisplay rastah = new RasterDisplay();
      rastah.setData(getDebugRaster(24));
      featureImagePanel.add(new Holder(rastah));
    }
    JPanel inputPanel = new JPanel();
    inputPanel.setLayout(new FlowLayout());
    inputPanel.add(new JLabel("Label:"));
    inputPanel.add(new JTextField(5));
    inputPanel.add(new JButton("Save"));

    FrontEnd fe = new FrontEnd();

    // ----------------------------------- drawing surface on left side, centered in a holder
    Holder ldsHolder = new Holder(lds);
    fe.add(ldsHolder, "lds");
    fe.addRule(ROOT, N, "lds", N);
    fe.addRule(ROOT, W, "lds", W);
    fe.addRule(ROOT, S, "lds", S);

    // ----------------------------------- "Recognition Results:" label at top right
    fe.add(resultsLabel, "reco label");
    fe.addRule(ROOT, N, "reco label", N);
    fe.addRule("lds", E, "reco label", W);

    // ----------------------------------- nBest list below recognition results label
    fe.add(nBestPanel, "nBest");
    fe.addRule("reco label", S, "nBest", N);
    fe.addRule("lds", E, "nBest", W);

    // ----------------------------------- feature images can stretch a bit between nBest and input
    fe.add(featureImagePanel, "features");
    fe.addRule("lds", E, "features", W);
    fe.addRule("nBest", S, "features", N);
    fe.addRule("input", N, "features", S);

    // ----------------------------------- input with text box and Save button at bottom right
    fe.add(inputPanel, "input");
    fe.addRule("lds", E, "input", W);
    fe.addRule(ROOT, S, "input", S);

    af.add(fe);
    af.setSize(640, 210);
    Components.centerComponent(af);
  }

  private double[] getDebugRaster(int n) {
    double[] ret = new double[n * n];
    for (int i = 0; i < n * n; i++) {
      ret[i] = random.nextDouble();
    }
    return ret;
  }

  public void recognize() {
    List<Stroke> strokes = lds.getStrokes();
    bug("Attempt recognition involving the " + strokes.size() + " strokes in the drawing area.");
  }

  public void handleSequenceEvent(SequenceEvent seqEvent) {
    if (seqEvent.getType() == SequenceEvent.Type.END) {

    }
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
