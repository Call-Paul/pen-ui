package org.six11.skrui;

import java.awt.BorderLayout;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
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

import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.gui.ApplicationFrame;
import org.six11.util.gui.BoundingBox;
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
  private ApplicationFrame af;
  private File currentFile;
  private String batchModePdfFileName;
  private static Set<Main> instances = new HashSet<Main>();
  private Preferences prefs;
  private Arguments args;
  private Map<String, Action> actions = new HashMap<String, Action>();

  private static final String ACTION_SAVE_AS = "save as";
  private static final String ACTION_SAVE = "save";
  private static final String ACTION_OPEN = "open";
  private static final String ACTION_SAVE_PDF = "save-pdf";
  private static final String ACTION_NEW = "new";
  private static final String ACTION_GRAPH_SPEED = "graph speed";
  private static final String ACTION_GRAPH_CURVATURE = "graph curvature";
  private static final String ACTION_GRAPH_ANGLE = "graph angle";

  private static final String PROP_SKETCH_DIR = "sketchDir";
  private static final String PROP_PDF_DIR = "pdfDir";

  private static final String[] DEFAULT_COMMAND_LINE_ARGS = {
    "--corner-finder"
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
        if (who.equals("corner-finder")) {
          System.out.println(CornerFinder.getArgumentSpec().getDocumentation());
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
    args.addFlag("corner-finder", ArgType.ARG_OPTIONAL, ValueType.VALUE_OPTIONAL,
        "Enable the corner-finder.");
    args.addFlag("debugging", ArgType.ARG_OPTIONAL, ValueType.VALUE_OPTIONAL,
        "Enable semi-helpful console text output");
    args.addFlag("debug-color", ArgType.ARG_OPTIONAL, ValueType.VALUE_OPTIONAL,
        "If debugging, use ANSI colors for a trippy experience");
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

    if (args.hasFlag("corner-finder")) {
      new CornerFinder(this, args.getOriginalArgs());
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
      attachKeyboardAccelerators(af.getRootPane());
      af.setLayout(new BorderLayout());
      af.add(ds, BorderLayout.CENTER);
      af.setSize(500, 400);
      af.center();
      af.setVisible(true);
    }
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

    actions.put(ACTION_GRAPH_CURVATURE, new NamedAction("Graph Curvature") {
      public void activate() {
        graph("curvature", true);
      }
    });
    graphMenu.add(actions.get(ACTION_GRAPH_CURVATURE));

    actions.put(ACTION_GRAPH_ANGLE, new NamedAction("Graph Angle") {
      public void activate() {
        graph("angle", true);
      }
    });
    graphMenu.add(actions.get(ACTION_GRAPH_ANGLE));

    pop.add(graphMenu);
    return pop;
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
  }

  protected void graph(String attribute, boolean useAbsValue) {
    int seqCount = 0;
    for (Sequence seq : ds.getSoup().getSequences()) {
      System.out.println("# Sequence " + seqCount);
      int ptCount = 0;
      for (Pt pt : seq) {
        if (useAbsValue) {
          System.out.println(ptCount + " " + Math.abs(pt.getDouble(attribute)));
        } else {
          System.out.println(ptCount + " " + pt.getDouble(attribute));
        }
        ptCount++;
      }
      seqCount++;
    }
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
      FileOutputStream out;
      try {
        out = new FileOutputStream(outFile);
        List<DrawingBuffer> layers = ds.getSoup().getDrawingBuffers();
        BoundingBox bb = new BoundingBox();
        for (DrawingBuffer layer : layers) {
          layer.update();
          bb.add(layer.getBoundingBox());
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
        bug("Wrote " + outFile.getName());
        prefs.setProperty(PROP_PDF_DIR, outFile.getParentFile().getAbsolutePath());
        prefs.save();
      } catch (FileNotFoundException ex1) {
        ex1.printStackTrace();
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    }
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
        bug("Saved " + outFile.getName());
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

  public static void bug(String what) {
    Debug.out("Main", what);
  }
}
