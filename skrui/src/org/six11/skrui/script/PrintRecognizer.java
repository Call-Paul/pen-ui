package org.six11.skrui.script;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
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
import org.six11.skrui.charrec.NBestList;
import org.six11.skrui.charrec.OuyangRecognizer;
import org.six11.skrui.charrec.RasterDisplay;
import org.six11.skrui.charrec.NBestList.NBest;
import org.six11.skrui.charrec.OuyangRecognizer.Callback;
import org.six11.skrui.shape.Stroke;
import org.six11.skrui.ui.LooseDrawingSurface;
import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.gui.ApplicationFrame;
import org.six11.util.gui.Components;
import org.six11.util.layout.FrontEnd;
import org.six11.util.layout.Holder;

import static org.six11.util.layout.FrontEnd.ROOT;
import static org.six11.util.layout.FrontEnd.N;
import static org.six11.util.layout.FrontEnd.E;
import static org.six11.util.layout.FrontEnd.W;
import static org.six11.util.layout.FrontEnd.S;
import org.six11.util.pen.SequenceEvent;
import org.six11.util.pen.SequenceListener;

public class PrintRecognizer extends SkruiScript implements SequenceListener, Callback {

  private static int N_BEST_DISPLAY = 5;

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
  OuyangRecognizer symbolRecognizer;
  JTextField inputText;
  Map<String, RasterDisplay> rasters;
  NBestHit[] bestHits;
  Random random = new Random(System.currentTimeMillis());

  @Override
  public void initialize() {
    bug("PrintRecognizer is alive!");
    symbolRecognizer = new OuyangRecognizer(); // TODO: supply a database
    symbolRecognizer.addCallback(this);
    if (main.getProperty("symbolCorpusFile") == null) {
      main.setProperty("symbolCorpusFile", new File("symbol-corpus.data").getAbsolutePath());
    }
    String fileName = main.getProperty("symbolCorpusFile");
    symbolRecognizer.setCorpus(new File(fileName));
    if (symbolRecognizer.getNumSymbols() > 100) {
      symbolRecognizer.calculatePrincipleComponents();
    } else {
      bug("Not enough symbols to calculate PCA. Need " + (100 - symbolRecognizer.getNumSymbols()) + " more.");
    }
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
    ActionListener clickez = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        setUserLabel(e.getActionCommand());
      }
    };
    bestHits = new NBestHit[N_BEST_DISPLAY];
    for (int i = 0; i < N_BEST_DISPLAY; i++) {
      bestHits[i] = new NBestHit("?", 0, false);
      bestHits[i].addActionListener(clickez);
      nBestPanel.add(bestHits[i]);
    }
    JPanel featureImagePanel = new JPanel();

    rasters = new HashMap<String, RasterDisplay>();
    int gridSize = OuyangRecognizer.DOWNSAMPLE_GRID_SIZE * 2;
    rasters.put("present", new RasterDisplay(gridSize, "As Drawn"));
    rasters.put("endpoint", new RasterDisplay(gridSize, "End Points"));
    rasters.put("dir0", new RasterDisplay(gridSize, "0 degrees"));
    rasters.put("dir1", new RasterDisplay(gridSize, "45 degrees"));
    rasters.put("dir2", new RasterDisplay(gridSize, "90 degrees"));
    rasters.put("dir3", new RasterDisplay(gridSize, "135 degrees"));
    GridLayout grid = new GridLayout(1, rasters.size());
    grid.setHgap(6);
    grid.setVgap(6);
    featureImagePanel.setLayout(grid);
    featureImagePanel.add(new Holder(rasters.get("present")));
    featureImagePanel.add(new Holder(rasters.get("endpoint")));
    featureImagePanel.add(new Holder(rasters.get("dir0")));
    featureImagePanel.add(new Holder(rasters.get("dir1")));
    featureImagePanel.add(new Holder(rasters.get("dir2")));
    featureImagePanel.add(new Holder(rasters.get("dir3")));

    JPanel inputPanel = new JPanel();
    inputPanel.setLayout(new FlowLayout());
    inputPanel.add(new JLabel("Label:"));
    inputText = new JTextField(5);
    JButton saveButton = new JButton("Save");
    saveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        saveLabeledSymbol();
        inputText.setText("");
        inputText.requestFocus();
        lds.clear();
      }
    });
    inputPanel.add(inputText);
    inputPanel.add(saveButton);

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
    af.setSize(740, 210);
    Components.centerComponent(af);
  }

  protected void setUserLabel(String val) {
    inputText.setText(val);
  }

  protected void saveLabeledSymbol() {
    String label = inputText.getText();
    List<Stroke> strokes = lds.getStrokes();
    if (label.length() > 0 && strokes.size() > 0) {
      double[] endpoint = rasters.get("endpoint").getData();
      double[] dir0 = rasters.get("dir0").getData();
      double[] dir1 = rasters.get("dir1").getData();
      double[] dir2 = rasters.get("dir2").getData();
      double[] dir3 = rasters.get("dir3").getData();

      symbolRecognizer.store(label, endpoint, dir0, dir1, dir2, dir3);
    }
  }

  public void recognize() {
    List<Stroke> strokes = lds.getStrokes();
    symbolRecognizer.recognize(strokes);
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

  public void recognitionBegun() {

  }

  public void recognitionComplete(double[] present, double[] endpoint, double[] dir0,
      double[] dir1, double[] dir2, double[] dir3, NBestList nBestList) {
    rasters.get("present").setData(present);
    rasters.get("endpoint").setData(endpoint);
    rasters.get("dir0").setData(dir0);
    rasters.get("dir1").setData(dir1);
    rasters.get("dir2").setData(dir2);
    rasters.get("dir3").setData(dir3);
    List<NBest> best = nBestList.getNBest(N_BEST_DISPLAY);
    for (int i = 0; i < N_BEST_DISPLAY; i++) {
      if (i >= best.size()) {
        bestHits[i].setData("?", 0);
      } else {
        NBest b = best.get(i);
        bestHits[i].setData(b.label, b.score);
      }
    }
  }
}
