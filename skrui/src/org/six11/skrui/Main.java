package org.six11.skrui;

import static java.awt.event.InputEvent.CTRL_MASK;
import static java.awt.event.InputEvent.SHIFT_MASK;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.*;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.DefaultFontMapper;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

import org.json.JSONException;
import org.six11.skrui.data.Journal;
import org.six11.skrui.shape.Stroke;
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

  private static final String PROP_SKETCH_DIR = "sketchDir";
  private static final String PROP_PDF_DIR = "pdfDir";

  public static final String PEN_COLOR = "pen color";
  public static final String PEN_THICKNESS = "pen thickness";

  private static final String[] DEFAULT_COMMAND_LINE_ARGS = {
      "Neanderthal", "FlowSelection", "GestureRecognizer", "Scribbler", "--debugging",
      "--debug-color"
  };

  private static final String PROP_MUZZLE_HELP = "muzzleHelp";

  private static boolean firstInstance = true;

  private Stroke seq;
  private Color penColor;
  private double penThickness = 1;

  // The currentSeq and last index are for managing the currently-in-progress ink stroke
  private GeneralPath inProgSeqGP;
  private boolean gpVisible;
  private int lastCurrentSequenceIdx;

  // data structure holding past user-drawn things in order
  private DrawnStuff drawnStuff;

  // sequence listeners are interested in pen activity
  private Set<SequenceListener> sequenceListeners;

  // hover listeners are interested in pen hover (in/out/move) activity
  private Set<HoverListener> hoverListeners;

  private Map<String, List<Long>> whackData;

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
  private SkruiScript transientMode;

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
    firstInstance = false;
    return inst;
  }

  private Main(Arguments args) throws JSONException {
    this.args = args;
    drawnStuff = new DrawnStuff(this);

    whackData = new HashMap<String, List<Long>>();
    sequenceListeners = new HashSet<SequenceListener>();
    hoverListeners = new HashSet<HoverListener>();
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
      JPanel lameButtonBar = makeLameButtonBar();
      if (lameButtonBar != null) {
        af.add(lameButtonBar, BorderLayout.SOUTH);
      }
      if (args.hasFlag("big")) {
        af.setExtendedState(java.awt.Frame.MAXIMIZED_BOTH);
      } else {
        af.setSize(500, 400);
      }
      af.center();
      af.setVisible(true);
      ds.setCursor(colorBar.getCursor());
      if (firstInstance) {
        String muzzle = getProperty(PROP_MUZZLE_HELP);
        if (muzzle == null || "No".equals(muzzle)) {
          showNewbieHelp();
        }
      }
    }
  }

  private JPanel makeLameButtonBar() {
    JPanel ret = new JPanel();
    boolean addedSomething = false;
    ret.setLayout(new FlowLayout());
    if (getScript("PrintRecognizer") != null) {
      JButton trainerButton = new JButton("Handwriting Recognizer");
      trainerButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {

        }
      });
      ret.add(trainerButton);
      addedSomething = true;
    }
    if (addedSomething == false) {
      ret = null;
    }
    return ret;
  }

  private void showNewbieHelp() {
    final JDialog help = new JDialog(af, "Welcome to Skrui Draw", false);
    JPanel helpPanel = new JPanel();
    String msg = "Howdy. Thanks for downloading Skrui Draw.\n\n"
        + "This is a research system to explore interaction techniques and interface widgets "
        + "that are appropriate for pen input. If you are using a mouse with Skrui Draw, you "
        + "will probably be disappointed.\n\n" //
        + "You can click the \"Show Homepage\" button below to visit the home page, which has "
        + "current documentation of Skrui Draw's current and planned features.";
    final JCheckBox muzzleCB = new JCheckBox("Don't show this friendly message again.");
    JTextArea msgText = new JTextArea(msg);
    msgText.setWrapStyleWord(true);
    msgText.setLineWrap(true);
    helpPanel.setLayout(new BorderLayout());
    JScrollPane textScroller = new JScrollPane(msgText, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    helpPanel.add(textScroller, BorderLayout.CENTER);
    JPanel dismissPanel = new JPanel();
    dismissPanel.setLayout(new BorderLayout());
    dismissPanel.add(muzzleCB, BorderLayout.NORTH);
    JPanel buttons = new JPanel();
    JButton homePageButton = new JButton("Show Homepage");
    homePageButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        try {
          Desktop.getDesktop().browse(new URI("http://six11.org/skrui"));
        } catch (IOException ex) {
          ex.printStackTrace();
        } catch (URISyntaxException ex) {
          ex.printStackTrace();
        }
      }
    });
    buttons.add(homePageButton);
    JButton whatever = new JButton("Start Sketching!");
    whatever.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        boolean shouldMuzzle = muzzleCB.getModel().isSelected();
        setProperty(PROP_MUZZLE_HELP, shouldMuzzle ? "Yes" : "No");
        help.setVisible(false);
      }
    });
    buttons.add(whatever);
    dismissPanel.add(buttons, BorderLayout.SOUTH);
    helpPanel.add(dismissPanel, BorderLayout.SOUTH);

    help.add(helpPanel);
    help.setSize(380, 260);
    Components.centerComponent(help);

    help.setVisible(true);
  }

  public DrawnStuff getDrawnStuff() {
    return drawnStuff;
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
    return inProgSeqGP;
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

  @SuppressWarnings("null")
  public void updateFinishedSequence(Stroke s) {
    DrawingBuffer db = s.getDrawingBuffer();
    if (db != null) {
      db.setVisible(false);
    }
    removeFinishedSequence(s);
    s.redraw();
    if (s != null && s.size() > 1) {
      drawnStuff.add(s);
    }
  }

  public void removeFinishedSequence(Stroke s) {
    if (s != null) {
      drawnStuff.remove(s);
    }
  }

  public Color getPenColor() {
    return penColor;
  }

  public double getPenThickness() {
    return penThickness;
  }

  public void addRawInputBegin(int x, int y, long t) {
    seq = new Stroke();
    if (penColor != null) {
      seq.setAttribute("pen color", penColor);
    }
    seq.setAttribute("pen thickness", penThickness);

    Pt pt = new Pt(x, y, t);
    seq.add(pt);
    gpVisible = true;
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
      inProgSeqGP = null;
      gpVisible = false;
    }
  }

  public void addFinishedSequenceNoninteractively(Stroke s) {
    DrawingBuffer buf = DrawingBufferRoutines.makeSequenceBuffer(s);
    s.setDrawingBuffer(buf);
    drawnStuff.add(s);
  }

  public void addFinishedSequence(Stroke s) {
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
          inProgSeqGP = new GeneralPath();
          inProgSeqGP.moveTo((float) pt.x, (float) pt.y);
        } else {
          inProgSeqGP.lineTo((float) pt.x, (float) pt.y);
        }
        lastCurrentSequenceIdx = i;
      }
    }
    ds.repaint();
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
          whackCursor();
        } else if (evt.getPropertyName().equals("pen thickness")) {
          setPenThickness((Double) evt.getNewValue());
          whackCursor();
        } else if (evt.getPropertyName().equals("undoEvent")) {
          if (evt.getNewValue().equals("undo")) {
            drawnStuff.undo();
          } else if (evt.getNewValue().equals("redo")) {
            drawnStuff.redo();
          } else {
            bug("eh? " + evt.getNewValue());
          }
        } else {
          bug("Got weird property event: " + evt.getPropertyName());
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

    return pop;
  }

  protected void whackLayer(int i) {
    DrawingBuffer db = drawnStuff.getNamedBuffer("" + i);
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
        for (int idx = 0; idx < timestamps.size() - 1; idx++) {
          diffs.addData(timestamps.get(idx + 1) - timestamps.get(idx));
        }
        if (diffs.getMean() < 400) {
          db = new DrawingBuffer();
          drawnStuff.addNamedBuffer(key, db, false);
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

  public List<Stroke> getSequences() {
    List<DrawnThing> manyThings = drawnStuff.getDrawnThings();
    List<Stroke> ret = new ArrayList<Stroke>();
    for (DrawnThing dt : manyThings) {
      if (dt instanceof Stroke) {
        ret.add((Stroke) dt);
      }
    }
    return ret;
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
        BoundingBox bb = new BoundingBox();
        for (DrawingBuffer layer : drawnStuff.getDrawingBuffers()) {
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
              db.addText(buf.toString().trim(), Color.BLACK, DrawingBufferRoutines.defaultFont);
              db.up();
              yCursor = yCursor + 16;
              buf.setLength(0);
            }
            buf.append(s + " ");
          }
          if (buf.length() > 0) {
            db.moveTo(20, yCursor);
            db.down();
            db.addText(buf.toString().trim(), Color.BLACK, DrawingBufferRoutines.defaultFont);
            db.up();
          }
          db.drawToGraphics(null);
          bb.add(db.getBoundingBox());
          drawnStuff.addNamedBuffer("arguments", db, false);
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
    for (DrawingBuffer buf : drawnStuff.getDrawingBuffers()) {
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
    drawnStuff.clear();
  }

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

  public static void savePDF(DrawingBuffer db, File file) throws FileNotFoundException {
    FileOutputStream out = new FileOutputStream(file);
    db.update();
    BoundingBox bb = db.getBoundingBox();
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
      db.drawToGraphics(g2);
      g2.dispose();
      cb.addTemplate(tp, 0, 0);
    } catch (DocumentException ex) {
      bug(ex.getMessage());
    }
    document.close();
    System.out.println("Wrote " + file.getAbsolutePath());
  }

  public void setTransientMode(SkruiScript skript) {
    transientMode = skript;
    whackCursor();
  }

  public SkruiScript getTransientMode() {
    return transientMode;
  }

  public void clearTransientMode() {
    transientMode = null;
    whackCursor();
  }

  private void whackCursor() {
    if (transientMode != null) {
      ds.setCursor(transientMode.getCursor());
    } else {
      ds.setCursor(colorBar.getCursor());
    }
  }
}
