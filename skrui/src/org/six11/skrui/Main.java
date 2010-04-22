package org.six11.skrui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import static java.awt.event.InputEvent.CTRL_MASK;
import static java.awt.event.InputEvent.SHIFT_MASK;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.DefaultFontMapper;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

import org.json.JSONException;
import org.six11.skrui.data.Journal;
import org.six11.skrui.ui.ColorBar;
import org.six11.skrui.ui.DrawingSurface;
import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.data.Statistics;
import org.six11.util.gui.ApplicationFrame;
import org.six11.util.gui.BoundingBox;
import org.six11.util.gui.Components;
import org.six11.util.io.FileUtil;
import org.six11.util.io.Preferences;
import org.six11.util.lev.NamedAction;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.HoverEvent;
import org.six11.util.pen.HoverListener;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.SequenceEvent;
import org.six11.util.pen.SequenceListener;

/**
 * 
 **/
public class Main {

  private static Set<Main> instances = new HashSet<Main>();
  private static final String ACTION_SAVE_AS = "save as";
  private static final String ACTION_SAVE = "save";
  private static final String ACTION_OPEN = "open";
  private static final String ACTION_SAVE_PDF = "save-pdf";
  private static final String ACTION_NEW = "new";
  private static final String ACTION_GRAPH_SPEED = "graph speed";
  private static final String ACTION_GRAPH_CURVATURE_ABS = "graph curvature (absolute value)";
  private static final String ACTION_GRAPH_ANGLE_ABS = "graph angle (absolute value)";
  private static final String ACTION_GRAPH_CURVATURE_SIGNED = "graph curvature (signed value)";
  private static final String ACTION_GRAPH_ANGLE_SIGNED = "graph angle (signed value)";

  private static final String PROP_SKETCH_DIR = "sketchDir";
  private static final String PROP_PDF_DIR = "pdfDir";
  private static final String PROP_GRAPH_DIR = "graphDir";

  private static final String[] DEFAULT_COMMAND_LINE_ARGS = {
      "Neanderthal", "FlowSelection", "GestureRecognizer", "Scribbler", "--debugging",
      "--debug-color"
  };

  private List<Sequence> pastSequences;
  private Sequence seq;
  private Color penColor;
  private double penThickness = 1;

  // The currentSeq and last index are for managing the currently-in-progress ink stroke
  private GeneralPath gp;
  private boolean gpVisible;
  private int lastCurrentSequenceIdx;

  // change listeners are interested in visual changes
  private List<ChangeListener> changeListeners;

  private List<DrawingBuffer> combinedBuffers;
  private List<DrawingBuffer> drawingBuffers;

  // sequence listeners are interested in pen activity
  private Set<SequenceListener> sequenceListeners;

  // hover listeners are interested in pen hover (in/out/move) activity
  private Set<HoverListener> hoverListeners;

  private Map<String, List<Long>> whackData;
  private Map<String, DrawingBuffer> namedBuffers;
  private Map<String, List<DrawingBuffer>> layers;

  private Map<Sequence, DrawingBuffer> seqToDrawBuf;

  private DrawingSurface ds;
  private ColorBar colorBar;
  private ApplicationFrame af;
  private File currentFile;
  private String batchModePdfFileName;
  private Preferences prefs;
  private Arguments args;
  private Map<String, Action> actions = new HashMap<String, Action>();
  private Set<Action> anonActions = new HashSet<Action>();
  private Map<String, BoundedParameter> params = new HashMap<String, BoundedParameter>();
  private Map<String, SkruiScript> scripts = new HashMap<String, SkruiScript>();

  public static void main(String[] in) throws IOException, JSONException {
    Arguments args = getArgumentSpec();
    if (in.length == 0) {
      args.parseArguments(DEFAULT_COMMAND_LINE_ARGS);
    } else {
      args.parseArguments(in);
    }
    if (args.hasFlag("help")) {
      if (args.hasValue("help")) {
        String who = args.getValue("help");
        try {
          Class<SkruiScript> clazz = loadScript(who);
          Method mainMethod = clazz.getMethod("getArgumentSpec", new Class[] {});
          Arguments sgra = (Arguments) mainMethod.invoke(null, (Object[]) null);
          System.out.println(sgra.getDocumentation());
        } catch (ClassNotFoundException ex) {
          System.out.println(ex.getMessage());
        } catch (NoSuchMethodException ex) {
          System.out.println("Found script for '" + who + "' but no documentation is found:");
          System.out.println(ex.getMessage());
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      } else {
        System.out.println(args.getDocumentation());
        // TODO: Look for other subclasses of SkruiScript and mention their names.
      }
      System.exit(0);
    }
    args.validate();
    Debug.enabled = args.hasFlag("debugging");
    Debug.useColor = args.hasFlag("debug-color");
    makeInstance(args);
  }

  @SuppressWarnings("unchecked")
  public static Class<SkruiScript> loadScript(String name) throws ClassNotFoundException {
    Class<SkruiScript> ret = null;
    String thisPackage = Main.class.getPackage().getName();
    String scriptPackage = thisPackage + ".script";

    try {
      ret = (Class<SkruiScript>) Class.forName(name);
    } catch (ClassNotFoundException ignore) {
    }
    if (ret == null && !name.startsWith(scriptPackage)) {
      try {
        ret = (Class<SkruiScript>) Class.forName(scriptPackage + "." + name);
      } catch (ClassNotFoundException ignore) {
      }
    }
    if (ret == null && !name.startsWith(thisPackage)) {
      try {
        ret = (Class<SkruiScript>) Class.forName(thisPackage + "." + name);
      } catch (ClassNotFoundException ignore) {
      }
    }
    if (ret == null) {
      throw new ClassNotFoundException("Unable to find a class for the script '" + name + "'");
    }
    return ret;
  }

  public static Arguments getArgumentSpec() {
    Arguments args = new Arguments();
    args.setProgramName("Skrui Hacks!");
    args.setDocumentationProgram("Runs various demos of the Skrui Hacks project.");

    args.addFlag("load-sketch", ArgType.ARG_OPTIONAL, ValueType.VALUE_REQUIRED,
        "Indicate a sketch file to load.");
    args.addFlag("help", ArgType.ARG_OPTIONAL, ValueType.VALUE_OPTIONAL,
        "Requests extended help. Use --help=corner-finder (for example) to "
            + "get help on a particular command.");
    args.addFlag("pdf-output", ArgType.ARG_OPTIONAL, ValueType.VALUE_REQUIRED,
        "Specify a PDF file to automatically output. Only useful with --load-sketch.");
    args.addFlag("no-ui", ArgType.ARG_OPTIONAL, ValueType.VALUE_IGNORED,
        "Supresses the user interface, which is useful in batch mode.");
    args.addFlag("big", ArgType.ARG_OPTIONAL, ValueType.VALUE_IGNORED,
        "Makes the user interface large.");
    args.addFlag("debugging", ArgType.ARG_OPTIONAL, ValueType.VALUE_OPTIONAL,
        "Enable semi-helpful console text output");
    args.addFlag("debug-color", ArgType.ARG_OPTIONAL, ValueType.VALUE_OPTIONAL,
        "If debugging, use ANSI colors for a trippy experience");
    args.addFlag("show-arguments", ArgType.ARG_OPTIONAL, ValueType.VALUE_OPTIONAL,
        "Shows the command line options onscreen (and/or the PDF), which is convenient for "
            + "recreating this parameterization later on.");

    return args;
  }

  public static Main makeInstance() throws JSONException {
    return makeInstance(new Arguments());
  }

  public static Main makeInstance(Arguments args) throws JSONException {
    final Main inst = new Main(args);
    instances.add(inst);
    inst.af.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        instances.remove(inst);
        if (instances.size() == 0) {
          System.exit(0);
        }
      }
    });
    return inst;
  }

  private Main(Arguments args) throws JSONException {
    this.args = args;
    drawingBuffers = new ArrayList<DrawingBuffer>();
    namedBuffers = new HashMap<String, DrawingBuffer>();
    whackData = new HashMap<String, List<Long>>();
    layers = new HashMap<String, List<DrawingBuffer>>();
    pastSequences = new ArrayList<Sequence>();
    sequenceListeners = new HashSet<SequenceListener>();
    hoverListeners = new HashSet<HoverListener>();
    seqToDrawBuf = new HashMap<Sequence, DrawingBuffer>();

    String title = "Skrui";
    if (args.hasValue("title")) {
      title = args.getValue("title");
    }
    af = new ApplicationFrame(title);
    af.setNoQuitOnClose();
    ds = new DrawingSurface(this);
    try {
      prefs = Preferences.makePrefs("skrui");
    } catch (IOException ex) {
      bug("Got IOException when making prefs object. This is going to ruin your day.");
      ex.printStackTrace();
    }
    for (int i = 0; i < args.getPositionCount(); i++) {
      try {
        Class<? extends SkruiScript> clazz = loadScript(args.getPosition(i));
        if (clazz != null) {
          SkruiScript script = SkruiScript.load(clazz, this);
          scripts.put(args.getPosition(i), script);
          bug("Loaded script: " + args.getPosition(i));
        }
      } catch (ClassNotFoundException ex) {
        warn("Can't load drawing script: " + args.getPosition(i));
      } catch (InstantiationException ex) {
        ex.printStackTrace();
      } catch (IllegalAccessException ex) {
        ex.printStackTrace();
      }
    }

    if (args.hasFlag("load-sketch")) {
      open(new File(args.getValue("load-sketch")));
    }

    if (args.hasFlag("pdf-output")) {
      batchModePdfFileName = args.getValue("pdf-output");
      savePdf();
    }

    if (args.hasFlag("no-ui") == false) {
      ds.setComponentPopupMenu(makePopup());
      makeAnonActions();
      attachKeyboardAccelerators(af.getRootPane());
      af.setLayout(new BorderLayout());
      colorBar = makeColorBar();
      setPenColor(colorBar.getCurrentColor());
      setPenThickness(colorBar.getCurrentThickness());
      af.add(colorBar, BorderLayout.NORTH);
      af.add(ds, BorderLayout.CENTER);
      if (args.hasFlag("big")) {
        af.setExtendedState(java.awt.Frame.MAXIMIZED_BOTH);
      } else {
        af.setSize(500, 400);
      }
      af.center();
      af.setVisible(true);
    }
  }

  public Set<String> getScriptNames() {
    return scripts.keySet();
  }
  
  public void setPenColor(Color pc) {
    penColor = pc;
  }

  public void setPenThickness(double thick) {
    penThickness = thick;
  }

  /**
   * Returns a reference to the currently in-progress scribble, suitable for efficient drawing.
   */
  public Shape getCurrentSequenceShape() {
    return gp;
  }

  public boolean isCurrentSequenceShapeVisible() {
    return gpVisible;
  }

  public void setCurrentSequenceShapeVisible(boolean vis) {
    gpVisible = vis;
  }

  public void addSequenceListener(SequenceListener lis) {
    sequenceListeners.add(lis);
  }

  public void removeSequenceListener(SequenceListener lis) {
    sequenceListeners.remove(lis);
  }

  public void addBuffer(String name, DrawingBuffer buf) {
    namedBuffers.put(name, buf);
    combinedBuffers = null;
    fireChange();
  }

  public void removeBuffer(String name) {
    if (namedBuffers.containsKey(name)) {
      namedBuffers.remove(name);
      combinedBuffers = null;
      fireChange();
    }
  }

  public DrawingBuffer getDrawingBufferForSequence(Sequence s) {
    return seqToDrawBuf.get(s);
  }

  public void updateFinishedSequence(Sequence s) {
    DrawingBuffer db = getDrawingBufferForSequence(s);
    if (db != null) {
      db.setVisible(false);
    }
    removeFinishedSequence(s);

    if (s != null && s.size() > 1) {
      DrawingBuffer buf = new DrawingBuffer();
      seqToDrawBuf.put(s, buf);
      if (s.getAttribute("pen color") != null) {
        buf.setColor((Color) s.getAttribute("pen color"));
      } else {
        buf.setColor(DrawingBuffer.getBasicPen().color);
      }
      if (s.getAttribute("pen thickness") != null) {
        buf.setThickness((Double) s.getAttribute("pen thickness"));
      } else {
        buf.setThickness(DrawingBuffer.getBasicPen().thickness);
      }
      buf.up();
      buf.moveTo(s.get(0).x, s.get(0).y);
      buf.down();
      for (Pt pt : s) {
        buf.moveTo(pt.x, pt.y);
      }
      buf.up();
      drawingBuffers.add(buf);
      combinedBuffers = null;
      pastSequences.add(s);
    }
  }

  public void removeFinishedSequence(Sequence s) {
    if (s != null) {
      drawingBuffers.remove(s);
      pastSequences.remove(s);
      combinedBuffers = null;
    }
  }

  public DrawingBuffer getBuffer(String name) {
    return namedBuffers.get(name);
  }

  public Color getPenColor() {
    return penColor;
  }

  public double getPenThickness() {
    return penThickness;
  }

  public void addToLayer(String str, DrawingBuffer buf) {
    if (!layers.containsKey(str)) {
      layers.put(str, new ArrayList<DrawingBuffer>());
    }
    layers.get(str).add(buf);
    combinedBuffers = null;
    fireChange();
  }

  /**
   * Registers a change listener, which is whacked every time some (potentially) visual aspect of
   * the soup has changed and the GUI should be repainted.
   */
  public void addChangeListener(ChangeListener lis) {
    if (changeListeners == null) {
      changeListeners = new ArrayList<ChangeListener>();
    }
    if (!changeListeners.contains(lis)) {
      changeListeners.add(lis);
    }
  }

  public void addRawInputBegin(int x, int y, long t) {
    seq = new Sequence();
    if (penColor != null) {
      seq.setAttribute("pen color", penColor);
    }
    seq.setAttribute("pen thickness", penThickness);

    Pt pt = new Pt(x, y, t);
    seq.add(pt);

    gpVisible = true;
    // addRawInputProgress(x, y, t);
    SequenceEvent sev = new SequenceEvent(this, seq, SequenceEvent.Type.BEGIN);
    fireSequenceEvent(sev);
  }

  public void addRawInputProgress(int x, int y, long t) {
    // Avoid adding duplicate points to the end of the sequence.
    Pt pt = new Pt(x, y, t);
    if (seq.size() == 0 || !seq.getLast().isSameLocation(pt)) {
      seq.add(pt);
      SequenceEvent sev = new SequenceEvent(this, seq, SequenceEvent.Type.PROGRESS);
      fireSequenceEvent(sev);
      drawSequence();
    }
  }

  public void addRawInputEnd() {
    if (seq == null) {
      return;
    } else {
      addFinishedSequence(seq);
      if (seq != null) {
        SequenceEvent sev = new SequenceEvent(this, seq, SequenceEvent.Type.END);
        fireSequenceEvent(sev);
      }
      seq = null;
      lastCurrentSequenceIdx = 0;
      gp = null;
      gpVisible = false;
      fireChange();
    }
  }

  public void addFinishedSequenceNoninteractively(Sequence s) {
//    bug("making a buffer and adding it to data structures.");
    DrawingBuffer buf = DrawingBufferRoutines.makeSequenceBuffer(s);
    seqToDrawBuf.put(s, buf);
    drawingBuffers.add(buf);
    combinedBuffers = null;
    pastSequences.add(s);
    fireChange();
  }

  public void addFinishedSequence(Sequence s) {
    if (s != null && s.size() > 1 && gpVisible) {
      addFinishedSequenceNoninteractively(s);
    }
  }

  public void addHover(int x, int y, long when, HoverEvent.Type type) {
    fireHoverEvent(new HoverEvent(this, new Pt(x, y, when), type));
  }

  public void addHoverListener(HoverListener lis) {
    hoverListeners.add(lis);
  }

  public void removeHoverListener(HoverListener lis) {
    hoverListeners.remove(lis);
  }

  private void fireHoverEvent(HoverEvent ev) {
    for (HoverListener lis : hoverListeners) {
      lis.handleHoverEvent(ev);
    }
  }

  /**
   * Draws the portion of the current sequence that has not yet been drawn.
   */
  protected void drawSequence() {
    if (seq != null) {
      for (int i = lastCurrentSequenceIdx; i < seq.size(); i++) {
        Pt pt = seq.get(i);
        if (i == 0) {
          gp = new GeneralPath();
          gp.moveTo((float) pt.x, (float) pt.y);
        } else {
          gp.lineTo((float) pt.x, (float) pt.y);
        }
        lastCurrentSequenceIdx = i;
      }
    }
    fireChange();
  }

  private void fireSequenceEvent(SequenceEvent ev) {
    for (SequenceListener lis : sequenceListeners) {
      lis.handleSequenceEvent(ev);
    }
  }

  private ColorBar makeColorBar() {
    ColorBar ret = new ColorBar();
    ret.addPropertyChangeListener(new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals("pen color")) {
          setPenColor((Color) evt.getNewValue());
        } else if (evt.getPropertyName().equals("pen thickness")) {
          setPenThickness((Double) evt.getNewValue());
        }
      }
    });
    return ret;
  }

  /**
   * Add actions to the anonActions list, which are accessed only via key presses.
   */
  private void makeAnonActions() {
    // pressing 0..9 whacks a visible layer, which are all debugging things.
    for (int i = 0; i < 10; i++) {
      final int which = i;
      anonActions.add(new NamedAction("Whack Layer " + i, KeyStroke.getKeyStroke("" + which)) {
        public void activate() {
          whackLayer(which);
        }
      });
    }
  }

  /**
   * Set the file name for a batch-mode pdf output. This is passed directly to a File constructor,
   * so if it is a relative string, it will be interpreted as such.
   */
  public void setBatchModePdfFileName(String pdfName) {
    batchModePdfFileName = pdfName;
  }

  public SkruiScript getScript(String name) {
    return scripts.get(name);
  }

  public boolean isScriptLoaded(String name) {
    return scripts.containsKey(name);
  }

  public DrawingSurface getDrawingSurface() {
    return ds;
  }

  private JPopupMenu makePopup() {
    JPopupMenu pop = new JPopupMenu("Skrui Hacks");

    int mod = CTRL_MASK;
    int shiftMod = CTRL_MASK | SHIFT_MASK;

    // Save Action
    actions.put(ACTION_SAVE, new NamedAction("Save", KeyStroke.getKeyStroke(KeyEvent.VK_S, mod)) {
      public void activate() {
        save();
      }
    });
    pop.add(actions.get(ACTION_SAVE));

    // Save As Action
    actions.put(ACTION_SAVE_AS, new NamedAction("Save As..", KeyStroke.getKeyStroke(KeyEvent.VK_S,
        shiftMod)) {
      public void activate() {
        saveAs();
      }
    });
    pop.add(actions.get(ACTION_SAVE_AS));

    // Open Action
    actions.put(ACTION_OPEN,
        new NamedAction("Open...", KeyStroke.getKeyStroke(KeyEvent.VK_O, mod)) {
          public void activate() {
            try {
              open();
            } catch (JSONException ex) {
              ex.printStackTrace();
            }
          }
        });
    pop.add(actions.get(ACTION_OPEN));

    // Save PDF
    actions.put(ACTION_SAVE_PDF, new NamedAction("Save PDF...", KeyStroke.getKeyStroke(
        KeyEvent.VK_P, mod)) {
      public void activate() {
        savePdf();
      }
    });
    pop.add(actions.get(ACTION_SAVE_PDF));

    // New
    actions.put(ACTION_NEW, new NamedAction("New Sketch", KeyStroke
        .getKeyStroke(KeyEvent.VK_N, mod)) {
      public void activate() {
        try {
          newSketch();
        } catch (JSONException ex) {
          ex.printStackTrace();
        }
      }
    });
    pop.add(actions.get(ACTION_NEW));

    // Graph operations
    JMenu graphMenu = new JMenu("Graph");
    actions.put(ACTION_GRAPH_SPEED, new NamedAction("Graph Speed") {
      public void activate() {
        graph("speed", false);
      }
    });
    graphMenu.add(actions.get(ACTION_GRAPH_SPEED));

    actions.put(ACTION_GRAPH_CURVATURE_ABS, new NamedAction("Graph Curvature (Absolute)") {
      public void activate() {
        graph("curvature", true);
      }
    });
    graphMenu.add(actions.get(ACTION_GRAPH_CURVATURE_ABS));

    actions.put(ACTION_GRAPH_CURVATURE_SIGNED, new NamedAction("Graph Curvature (Signed)") {
      public void activate() {
        graph("curvature", false);
      }
    });
    graphMenu.add(actions.get(ACTION_GRAPH_CURVATURE_SIGNED));

    actions.put(ACTION_GRAPH_ANGLE_ABS, new NamedAction("Graph Angle") {
      public void activate() {
        graph("angle", true);
      }
    });
    graphMenu.add(actions.get(ACTION_GRAPH_ANGLE_ABS));

    actions.put(ACTION_GRAPH_ANGLE_SIGNED, new NamedAction("Graph Angle (Signed)") {
      public void activate() {
        graph("angle", false);
      }
    });
    graphMenu.add(actions.get(ACTION_GRAPH_ANGLE_SIGNED));

    pop.add(graphMenu);
    return pop;
  }

  protected void whackLayer(int i) {
    DrawingBuffer db = getBuffer("" + i);
    if (db != null) {
      boolean currentValue = db.isVisible();
      String key = "" + i;
      if (!whackData.containsKey(key)) {
        whackData.put(key, new ArrayList<Long>());
      }
      List<Long> timestamps = whackData.get(key);
      timestamps.add(System.currentTimeMillis());
      if (timestamps.size() == 4) {
        Statistics diffs = new Statistics();
        for (int idx=0; idx < timestamps.size() - 1; idx++) {
          diffs.addData(timestamps.get(idx+1) - timestamps.get(idx));
        }
        if (diffs.getMean() < 400) {
          db = new DrawingBuffer();
          addBuffer(key, db);
        }
        timestamps.remove(0);
      }
      bug("Whacking layer " + key);
      db.setVisible(!currentValue);
      getDrawingSurface().repaint();

    } else {
      bug("Can't find buffer for layer: " + i);
    }
  }

  /**
   * Asks the given root pane to listen for keystroke actions associated with our actions (for those
   * that have keyboard accellerators).
   */
  public final void attachKeyboardAccelerators(JRootPane rp) {
    for (Action action : actions.values()) {
      KeyStroke s = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
      if (s != null) {
        rp.registerKeyboardAction(action, s, JComponent.WHEN_IN_FOCUSED_WINDOW);
      }
    }
    for (Action action : anonActions) {
      KeyStroke s = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
      if (s != null) {
        rp.registerKeyboardAction(action, s, JComponent.WHEN_IN_FOCUSED_WINDOW);
      }
    }
  }

  /**
   * Writes numeric data to standard output suitable for graphing in GNUPlot.
   * 
   * @param attribute
   *          the name of the attribute to graph. This must be set on each point in each sequence.
   *          For example, "curvature" will give you the signed curvature at each point.
   * @param useAbsValue
   *          will cause the absolute value of each value to be output.
   */
  protected void graph(String attribute, boolean useAbsValue) {
    int seqCount = 0;
    File outFile = null;
    File initialDir = maybeGetInitialDir(PROP_GRAPH_DIR);
    JFileChooser chooser = FileUtil.makeFileChooser(initialDir, "graph", "Graph Files");
    if (currentFile != null) {
      String suggestedFileName = removeSuffix(currentFile.getName()) + "-" + attribute + ".graph";
      chooser.setSelectedFile(new File(initialDir, suggestedFileName));
    }
    int result = chooser.showSaveDialog(ds);
    if (result == JFileChooser.APPROVE_OPTION) {
      outFile = chooser.getSelectedFile();
      if (!outFile.getName().endsWith(".graph")) {
        outFile = new File(outFile.getParentFile(), outFile.getName() + ".graph");
      }
    }
    if (outFile != null) {
      try {
        FileWriter out = new FileWriter(outFile);
        for (Sequence seq : getSequences()) {
          out.write("# Sequence " + seqCount + "\n");
          int ptCount = 0;
          for (Pt pt : seq) {
            if (useAbsValue) {
              out.write(ptCount + " " + Math.abs(pt.getDouble(attribute)) + "\n");
            } else {
              out.write(ptCount + " " + pt.getDouble(attribute) + "\n");
            }
            ptCount++;
          }
          seqCount++;
        }
        out.close();
        System.out.println("Wrote " + outFile.getAbsolutePath());
        prefs.setProperty(PROP_GRAPH_DIR, outFile.getParentFile().getAbsolutePath());
        prefs.save();
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    }
  }

  public List<Sequence> getSequences() {
    return pastSequences;
  }

  public void setProperty(String key, String value) {
    prefs.setProperty(key, value);
    try {
      prefs.save();
    } catch (FileNotFoundException ex) {
      ex.printStackTrace();
    } catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  public String getProperty(String key) {
    return prefs.getProperty(key);
  }

  private static String removeSuffix(String name) {
    String ret = name;
    if (name.lastIndexOf(".") > 0) {
      ret = name.substring(0, name.lastIndexOf("."));
    }
    return ret;
  }

  public void savePdf(String filenameNoDotPdf, boolean overwrite) {
    String fileName = filenameNoDotPdf + ".pdf";
    File f = new File(fileName);
    int n = 2;
    while (f.exists() && !overwrite) {
      f = new File(filenameNoDotPdf + "-" + n + ".pdf");
      n++;
    }
    this.batchModePdfFileName = f.getAbsolutePath();
    savePdf();
  }

  private void savePdf() {
    File outFile = null;
    if (batchModePdfFileName != null) {
      outFile = new File(batchModePdfFileName);
    } else {
      File initialDir = maybeGetInitialDir(PROP_PDF_DIR);
      JFileChooser chooser = FileUtil.makeFileChooser(initialDir, "pdf", "PDF Files");
      int result = chooser.showSaveDialog(ds);
      if (result == JFileChooser.APPROVE_OPTION) {
        outFile = chooser.getSelectedFile();
        if (!outFile.getName().endsWith(".pdf")) {
          outFile = new File(outFile.getParentFile(), outFile.getName() + ".pdf");
        }
      }
    }

    if (outFile != null) {
      outFile = outFile.getAbsoluteFile(); // sidestep any 'abstract file name' silliness
      FileOutputStream out;
      try {
        out = new FileOutputStream(outFile);
        List<DrawingBuffer> layers = getDrawingBuffers();
        BoundingBox bb = new BoundingBox();
        for (DrawingBuffer layer : layers) {
          layer.update();
          bb.add(layer.getBoundingBox());
        }
        if (args.hasFlag("show-arguments")) {
          DrawingBuffer db = new DrawingBuffer();
          double yCursor = bb.getHeight() + 80;
          StringBuilder buf = new StringBuilder();
          for (String s : args.getOriginalArgs()) {
            int chars = buf.length() + s.length();
            if (chars > 80) {
              db.moveTo(20, yCursor);
              db.down();
              db.addText(buf.toString().trim(), Color.BLACK);
              db.up();
              yCursor = yCursor + 16;
              buf.setLength(0);
            }
            buf.append(s + " ");
          }
          if (buf.length() > 0) {
            db.moveTo(20, yCursor);
            db.down();
            db.addText(buf.toString().trim(), Color.BLACK);
            db.up();
          }
          db.drawToGraphics(null);
          bb.add(db.getBoundingBox());
          addBuffer(db);
        }

        int w = bb.getWidthInt();
        int h = bb.getHeightInt();
        Rectangle size = new Rectangle(w, h);
        Document document = new Document(size, 0, 0, 0, 0);
        try {
          PdfWriter writer = PdfWriter.getInstance(document, out);
          document.open();
          DefaultFontMapper mapper = new DefaultFontMapper();
          PdfContentByte cb = writer.getDirectContent();
          PdfTemplate tp = cb.createTemplate(w, h);
          Graphics2D g2 = tp.createGraphics(w, h, mapper);
          tp.setWidth(w);
          tp.setHeight(h);
          g2.translate(-bb.getX(), -bb.getY());
          ds.paintContent(g2, false);
          g2.dispose();
          cb.addTemplate(tp, 0, 0);
        } catch (DocumentException ex) {
          bug(ex.getMessage());
        }
        document.close();
        System.out.println("Wrote " + outFile.getAbsolutePath());
        prefs.setProperty(PROP_PDF_DIR, outFile.getParentFile().getAbsolutePath());
        prefs.save();
      } catch (FileNotFoundException ex1) {
        ex1.printStackTrace();
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    }
  }

  /**
   * Adds a complete visual element: perhaps a raw Sequence, but it could be something else such as
   * a filled region or a rectified shape.
   */
  public void addBuffer(DrawingBuffer buf) {
    if (!drawingBuffers.contains(buf)) {
      drawingBuffers.add(buf);
      combinedBuffers = null;
    }
    fireChange();
  }

  /**
   * Fires a simple event indicating some (potentially) visual aspect of the data has changed.
   */
  public void fireChange() {
    if (changeListeners != null) {
      ChangeEvent ev = new ChangeEvent(this);
      for (ChangeListener cl : changeListeners) {
        cl.stateChanged(ev);
      }
    }
  }

  /**
   * Returns a list of all cached drawing buffers.
   */
  public List<DrawingBuffer> getDrawingBuffers() {
    if (combinedBuffers == null) {
      combinedBuffers = new ArrayList<DrawingBuffer>();
      combinedBuffers.addAll(drawingBuffers);
      combinedBuffers.addAll(namedBuffers.values());
      for (List<DrawingBuffer> layer : layers.values()) {
        combinedBuffers.addAll(layer);
      }
    }
    return combinedBuffers;
  }

  public BufferedImage getContentImage() {
    BufferedImage image = new BufferedImage(ds.getWidth(), ds.getHeight(),
        BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    Components.antialias(g);
    ds.paintContent(g, false);
    return image;
  }

  public BufferedImage getRawInkImage() {
    BufferedImage image = new BufferedImage(ds.getWidth(), ds.getHeight(),
        BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    Components.antialias(g);
    List<DrawingBuffer> all = getDrawingBuffers();
    for (DrawingBuffer buf : all) {
      buf.paste(g);
    }
    return image;
  }

  private File maybeGetInitialDir(String propKey) {
    File ret = null;
    String path = prefs.getProperty(propKey);
    if (path != null) {
      ret = new File(path);
    }
    bug("Initial dir for '" + propKey + "' is " + (ret == null ? "''" : ret.getAbsolutePath()));
    return ret;
  }

  private void saveAs() {
    File initialDir = maybeGetInitialDir(PROP_SKETCH_DIR);
    JFileChooser chooser = FileUtil.makeFileChooser(initialDir, "sketch", "Raw Sketch Files");
    int result = chooser.showSaveDialog(ds);
    if (result == JFileChooser.APPROVE_OPTION) {
      File outFile = chooser.getSelectedFile();
      if (!outFile.getName().endsWith(".sketch")) {
        outFile = new File(outFile.getParentFile(), outFile.getName() + ".sketch");
      }
      bug("You chose " + outFile.getAbsolutePath());
      try {
        FileUtil.createIfNecessary(outFile);
        setCurrentFile(outFile);
        save();
      } catch (IOException ex) {
        bug("Error creating file: " + outFile.getAbsolutePath());
      }
    }
  }

  private void save() {
    bug("save...");
    if (currentFile == null) {
      saveAs();
    } else {
      try {
        FileUtil.createIfNecessary(currentFile);
        FileUtil.complainIfNotWriteable(currentFile);
        Journal jnl = new Journal(this);
        bug("made journal. saving...");
        jnl.save();
        bug("journal save returned.");
        System.out.println("Saved " + currentFile.getName());
      } catch (IOException ex) {
        ex.printStackTrace();
      } catch (JSONException ex) {
        ex.printStackTrace();
      }
    }
  }

  private void open() throws JSONException {
    File initialDir = maybeGetInitialDir(PROP_SKETCH_DIR);
    JFileChooser chooser = FileUtil.makeFileChooser(initialDir, "sketch", "Raw Sketch Files");
    int result = chooser.showOpenDialog(ds);
    if (result == JFileChooser.APPROVE_OPTION) {
      File inFile = chooser.getSelectedFile();
      open(inFile);
    }
  }

  private void open(File inFile) throws JSONException {
    try {
      FileUtil.complainIfNotReadable(inFile);
      setCurrentFile(inFile);
      clearDrawing();
      Journal jnl = new Journal(this);
      jnl.open();
      System.out.println("Opened " + inFile.getAbsolutePath());
    } catch (IOException ex) {
      bug("Can't read file: " + inFile.getAbsolutePath());
      ex.printStackTrace();
    }
  }

  public void clearDrawing() {
    seq = null;
    drawingBuffers = new ArrayList<DrawingBuffer>();
    combinedBuffers = null;
    pastSequences.clear();
    fireChange();
  }

  @SuppressWarnings("unused")
  private void setCurrentFile(File f) throws FileNotFoundException, IOException {
    if (f != null && f.exists()) {
      currentFile = f.getAbsoluteFile();
      af.setTitle(currentFile.getName());
      prefs.setProperty(PROP_SKETCH_DIR, currentFile.getParentFile().getAbsolutePath());
      prefs.save();
    } else if (f == null) {
      bug("I won't null out the current file!");
    } else if (!f.exists()) {
      bug("I won't set the current file to something that doesn't exist!");
    }
  }

  public File getCurrentFile() {
    return currentFile;
  }

  private void newSketch() throws JSONException {
    makeInstance(args);
  }

  public Arguments getArguments() {
    return args;
  }

  public static void bug(String what) {
    Debug.out("Main", what);
  }

  public static void warn(String what) {
    System.out.println("  **WARNING**  " + what);
  }

  public BoundedParameter getParam(String key) {
    return params.get(key);
  }

  public void addParameters(Map<String, BoundedParameter> orig) {
    if (orig != null) {
      for (String k : orig.keySet()) {
        params.put(k, orig.get(k).copy());
        bug(k + " : " + params.get(k).getValueStr());
      }
    }
  }
}
