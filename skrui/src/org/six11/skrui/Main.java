package org.six11.skrui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
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

import static java.awt.event.InputEvent.CTRL_MASK;
import static java.awt.event.InputEvent.SHIFT_MASK;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.DefaultFontMapper;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

import org.six11.skrui.ui.ColorBar;
import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.gui.ApplicationFrame;
import org.six11.util.gui.BoundingBox;
import org.six11.util.gui.Components;
import org.six11.util.io.FileUtil;
import org.six11.util.io.Preferences;
import org.six11.util.lev.NamedAction;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.OliveDrawingSurface;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.SequenceIO;

/**
 * 
 **/
public class Main {

  private OliveDrawingSurface ds;
  private ColorBar colorBar;
  private ApplicationFrame af;
  private File currentFile;
  private String batchModePdfFileName;
  private static Set<Main> instances = new HashSet<Main>();
  private Preferences prefs;
  private Arguments args;
  private Map<String, Action> actions = new HashMap<String, Action>();
  private Set<Action> anonActions = new HashSet<Action>();
  private Map<String, BoundedParameter> params = new HashMap<String, BoundedParameter>();
  private Map<String, SkruiScript> scripts = new HashMap<String, SkruiScript>();

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
    "CornerFinder"
  };

  public static void main(String[] in) throws IOException {
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

  public static Main makeInstance() {
    return makeInstance(new Arguments());
  }

  public static Main makeInstance(Arguments args) {
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

  private Main(Arguments args) {
    this.args = args;
    af = new ApplicationFrame("Olive Test GUI");
    af.setNoQuitOnClose();
    ds = new OliveDrawingSurface();
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
        bug("Can't load drawing script: " + args.getPosition(i));
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
      ds.setPenColor(colorBar.getCurrentColor());
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

  private ColorBar makeColorBar() {
    ColorBar ret = new ColorBar();
    ret.addPropertyChangeListener(new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent evt) {
        bug("Pen color changed to " + evt.getNewValue());
        ds.setPenColor((Color) evt.getNewValue());
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

  public SkruiScript getScript(String name) {
    return scripts.get(name);
  }

  public boolean isScriptLoaded(String name) {
    return scripts.containsKey(name);
  }

  public OliveDrawingSurface getDrawingSurface() {
    return ds;
  }

  private JPopupMenu makePopup() {
    JPopupMenu pop = new JPopupMenu("Skrui Hacks");

    int mod = CTRL_MASK;
    int shiftMod = CTRL_MASK | SHIFT_MASK;

    // Save Action
    actions.put(ACTION_SAVE, new NamedAction("Save", KeyStroke.getKeyStroke(KeyEvent.VK_S, mod)) {
      public void activate() {
        save(currentFile);
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
            open();
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
        newSketch();
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
    if (i == 1) {
      List<DrawingBuffer> dbs = getDrawingSurface().getSoup().getAnonymousDrawingBuffers();
      if (dbs.size() > 0) {
        boolean destState = !dbs.get(0).isVisible();
        for (DrawingBuffer db : dbs) {
          db.setVisible(destState);
        }
        getDrawingSurface().repaint();
      }
    } else {
      DrawingBuffer db = getDrawingSurface().getSoup().getBuffer("" + i);
      if (db != null) {
        db.setVisible(!db.isVisible());
        getDrawingSurface().repaint();
      }
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
        for (Sequence seq : ds.getSoup().getSequences()) {
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
        List<DrawingBuffer> layers = ds.getSoup().getDrawingBuffers();
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
          ds.getSoup().addBuffer(db);
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
    List<DrawingBuffer> all = ds.getSoup().getDrawingBuffers();
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
        save(currentFile);
      } catch (IOException ex) {
        bug("Error creating file: " + outFile.getAbsolutePath());
      }
    }
  }

  private void save(File outFile) {
    if (outFile == null) {
      saveAs();
    } else {
      try {
        FileUtil.createIfNecessary(outFile);
        FileUtil.complainIfNotWriteable(outFile);
        FileWriter fw = new FileWriter(outFile);
        List<Sequence> sequences = ds.getSoup().getSequences();
        SequenceIO.writeAll(sequences, fw);
        System.out.println("Saved " + outFile.getName());
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    }
  }

  private void open() {
    File initialDir = maybeGetInitialDir(PROP_SKETCH_DIR);
    JFileChooser chooser = FileUtil.makeFileChooser(initialDir, "sketch", "Raw Sketch Files");
    int result = chooser.showOpenDialog(ds);
    if (result == JFileChooser.APPROVE_OPTION) {
      File inFile = chooser.getSelectedFile();
      open(inFile);
    }
  }

  private void open(File inFile) {
    try {
      FileUtil.complainIfNotReadable(inFile);
      BufferedReader in = new BufferedReader(new FileReader(inFile));
      List<Sequence> sequences = SequenceIO.readAll(in);
      bug("Read " + sequences.size() + " sequences from " + inFile.getAbsolutePath());
      ds.getSoup().clearDrawing();
      for (Sequence seq : sequences) {
        ds.getSoup().addFinishedSequence(seq);
      }
      setCurrentFile(inFile);
      bug("Reset drawing surface with new data.");
      System.out.println("Opened " + inFile.getAbsolutePath());
    } catch (IOException ex) {
      bug("Can't read file: " + inFile.getAbsolutePath());
      ex.printStackTrace();
    }
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

  private void newSketch() {
    makeInstance(args);
  }

  public Arguments getArguments() {
    return args;
  }

  public static void bug(String what) {
    Debug.out("Main", what);
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
