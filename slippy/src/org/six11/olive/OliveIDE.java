package org.six11.olive;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.*;

import org.antlr.runtime.tree.Tree;
import org.six11.slippy.*;
import org.six11.util.Debug;
import org.six11.util.gui.ColoredTextPane;
import org.six11.util.lev.NamedAction;

import static java.awt.event.InputEvent.*;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class OliveIDE extends JPanel {

  private static final String SCRATCH_NAME = "*scratch*";
  protected Object fileName;

  private Map<String, NamedAction> actions;
  private JPanel buttons;
  private Map<String, JButton> slippyButtons;
  private Map<String, JMenuItem> slippyMenuItems;
  private Map<String, String> slippySourceFiles; // records where button/menu actions are defined
  private JMenu slippyMenu;
  private SlippyInterpreter interp;
  private ColoredTextPane stdout;
  private JTabbedPane editorTabs;
  private OliveSoup soup;
  private OliveMouseThing mouseThing;
  private OliveDrawingSurface surface;
  private Environment env;

  public OliveIDE() {
    super();
    env = new DiskEnvironment();
  }

  public OliveIDE(boolean inBrowser, String slippySourcePath) {
    super();
    if (inBrowser) {
      env = new WebEnvironment();
    } else {
      env = new DiskEnvironment();
    }
    env.setLoadPath(slippySourcePath);
    init();
  }

  /**
   * Maps fully-qualified class names to program strings.
   */
  private Map<String, String> buffers;

  /**
   * Maps fully-qualified class names to editor components.
   */
  private Map<String, SlippyEditor> editors;

  /**
   * Should always point to whichever editor is currently on top.
   */
  private SlippyEditor activeEditor;

  private WhackTabWhenDirty dirtyWhacker;

  private void init() {
    setLayout(new BorderLayout());
    initActions();
    slippyButtons = new HashMap<String, JButton>();
    slippyMenuItems = new HashMap<String, JMenuItem>();
    slippySourceFiles = new HashMap<String, String>();
    buffers = new HashMap<String, String>();
    editors = new HashMap<String, SlippyEditor>();

    JPanel topLeft = new JPanel();
    topLeft.setLayout(new BorderLayout());
    JScrollPane stdoutScroller = new JScrollPane();
    stdoutScroller.getVerticalScrollBar().setUnitIncrement(16);

    stdout = new ColoredTextPane(stdoutScroller.getViewport());
    stdout.setFont(new Font("Courier New", Font.PLAIN, 12));
    stdoutScroller.setViewportView(stdout);
    stdout.setBackground(new Color(240, 240, 240));
    stdout.setEditable(false);
    stdout.setFocusable(true);
    stdout.setAutoscroll(true);

    Debug.useColor = false;
    Debug.useTime = false;
    Debug.outputStream = new PrintStream(new RedirectedOutputStream(stdout), true);
    initMenu();
    buttons = new JPanel();
    initButtons(buttons);
    topLeft.add(buttons, BorderLayout.NORTH);
    surface = new OliveDrawingSurface(this);
    topLeft.add(surface, BorderLayout.CENTER);
    // -- bottom left
    JPanel diagnostics = new JPanel();
    diagnostics.setLayout(new BorderLayout());
    diagnostics.add(stdoutScroller, BorderLayout.CENTER);
    // -- combine in a split pane
    JSplitPane left = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topLeft, diagnostics);
    left.setDividerLocation(330);

    // configure the right side: text editor on the top/middle, and a little status on the bottom
    editorTabs = new JTabbedPane();

    dirtyWhacker = new WhackTabWhenDirty();
    activeEditor = new SlippyEditor(SCRATCH_NAME);
    activeEditor.addDirtyListener(dirtyWhacker);
    editorTabs.addTab(SCRATCH_NAME, null, activeEditor,
        "The happy funtime ballroom of editing buffers");
    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, editorTabs);
    split.setDividerLocation(400);
    add(split, BorderLayout.CENTER);
    initSlippy();
    resetJavaBindings();
    Debug.out("OliveIDE", "Initialized!");
  }

  private class WhackTabWhenDirty implements PropertyChangeListener {
    public void propertyChange(PropertyChangeEvent ev) {
      SlippyEditor ed = (SlippyEditor) ev.getSource();
      int idx = editorTabs.indexOfComponent(ed);
      if (idx > 0) {
        String mod = "";
        if (((Boolean) ev.getNewValue()).booleanValue()) {
          mod = "*";
        }
        String className = SlippyUtils.getClassName(ed.getFQClassName());
        editorTabs.setTitleAt(idx, mod + className);
      }
    }
  }

  public Dimension getPreferredSize() {
    bug("Returning my preferred size: " + Debug.num(super.getPreferredSize()));
    return super.getPreferredSize();
  }
  
  public Dimension getMinimumSize() {
    bug("Returning my preferred size: " + Debug.num(super.getMinimumSize()));
    return super.getMinimumSize();
  }
  public Dimension getMaximumSize() {
    bug("Returning my preferred size: " + Debug.num(super.getMaximumSize()));
    return super.getMaximumSize();
  }
  
  private final void initSlippy() {
    SlippyMachine.outputStream = Debug.outputStream;
    interp = new SlippyInterpreter();
    interp.getMachine().setEnvironment(env);
    interp.getMachine().pushFileName("(new or unsaved file)");

    // Slippy code can use the 'signal' function to pass events and arbitrary Thing objects
    // back into Java. This is done using a message bus.
    interp.getMachine().setMessageBus(new OliveIDEMessageBus(this));
  }

  private final void initActions() {
    int weirdMod = CTRL_MASK | SHIFT_MASK;
    int mod = CTRL_MASK;

    actions = new HashMap<String, NamedAction>();
    actions.put("Run", new NamedAction("Run", KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, mod)) {
      public void activate() {
        interpret();
      }
    });
    actions.put("Save", new NamedAction("Save", KeyStroke.getKeyStroke(KeyEvent.VK_S, mod)) {
      public void activate() {
        try {
          save();
        } catch (IOException ex) {
          bug("Error saving: " + ex.getMessage());
          ex.printStackTrace();
        }
      }
    });
    actions.put("Save As", new NamedAction("Save As", KeyStroke.getKeyStroke(KeyEvent.VK_S,
        weirdMod)) {
      public void activate() {
        try {
          saveAs();
        } catch (IOException ex) {
          ex.printStackTrace();
        }
      }
    });
    actions.put("Open", new NamedAction("Open", KeyStroke.getKeyStroke(KeyEvent.VK_O, mod)) {
      public void activate() {
        open();
      }
    });
    NamedAction newAction = new NamedAction("New", KeyStroke.getKeyStroke(KeyEvent.VK_N, CTRL_MASK)) {
      public void activate() {
        newFile();
      }
    };
    actions.put("New", newAction);
  }

  public final void attachKeyListener(JRootPane rp) {
    for (Action action : actions.values()) {
      KeyStroke s = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
      if (s != null) {
        rp.registerKeyboardAction(action, s, JComponent.WHEN_IN_FOCUSED_WINDOW);
      }
    }
  }
  private final void initButtons(JPanel buttons) {
    buttons.setLayout(new GridLayout(0, 3));

    buttons.add(new JButton(actions.get("Run")));
  }

  private final void initMenu() {
    JMenuBar bar = new JMenuBar();
    JMenu fileMenu = new JMenu("File");
    fileMenu.add(actions.get("New"));
    fileMenu.add(actions.get("Open"));
    fileMenu.add(actions.get("Save"));
    fileMenu.add(actions.get("Save As"));
    fileMenu.add(actions.get("Save Sketch"));
    fileMenu.add(actions.get("Browse"));
    bar.add(fileMenu);
    // setJMenuBar(bar); // This got whacked when I moved to OliveIDE
  }

  private static void bug(String what) {
    Debug.out("OliveIDE", what);
  }

  protected void newFile() {
    String fqClassName = JOptionPane
        .showInputDialog("What's the fully qualified class name?\n\ne.g. org.mypeople.MyThing");
    if (fqClassName != null && fqClassName.length() > 0 && interp.isValidClassName(fqClassName)) {
      bug("Great, make a new file for " + fqClassName);
      makeNewBuffer(fqClassName, null, true);
      showBuffer(fqClassName);
    } else {
      bug("Bummer. " + fqClassName + " is bogus.");
    }
  }

  /**
   * Create a new buffer for the given fully-qualified class. This will not overwrite an existing
   * buffer. If an initialValue is provided, the buffer will display it. Optionally, you may specify
   * writeCode = true to add a default stub that puts the file in the right codeset with a skeleton
   * class definition. If both an initialValue is provided and writeCode is true, the initialValue
   * is ignored.
   * 
   * @return true if a new buffer was made, or false if an existing buffer was found.
   */
  protected boolean makeNewBuffer(String fqClassName, String initialValue, boolean writeCode) {
    boolean ret = false;
    if (!buffers.containsKey(fqClassName)) {
      String codeset = null;
      String className = null;
      if (fqClassName.lastIndexOf('.') > 0) {
        codeset = fqClassName.substring(0, fqClassName.lastIndexOf('.'));
        className = fqClassName.substring(fqClassName.lastIndexOf('.') + 1);
      } else {
        className = fqClassName;
      }
      if (initialValue != null) {
        buffers.put(fqClassName, initialValue);
      }

      if (writeCode) {
        StringBuilder buf = new StringBuilder();
        buf.append("; " + className + ".slippy --\n");
        buf.append("\n");
        if (codeset != null) {
          buf.append("codeset " + codeset + "\n\n");
        }
        buf.append("class " + className + "\n  \ndone");
        buffers.put(fqClassName, buf.toString());
      }
      makeNewEditor(fqClassName);
      ret = true;
    }
    return ret;
  }

  /**
   * Makes a new editor for the given class if there isn't one already. This has no effect if there
   * is already an editor for said class.
   */
  protected void makeNewEditor(String fqClassName) {
    if (!editors.containsKey(fqClassName)) {
      String className = fqClassName;
      if (fqClassName.lastIndexOf('.') > 0) {
        className = fqClassName.substring(fqClassName.lastIndexOf('.') + 1);
      }
      SlippyEditor editor = new SlippyEditor(fqClassName);
      editor.getTextPane().setText(buffers.get(fqClassName));
      editor.colorize();
      editor.getTextPane().setCaretPosition(buffers.get(fqClassName).length() - 4);
      editors.put(fqClassName, editor);
      editorTabs.addTab(className, editor);
      editor.setDirty(false);
      editor.addDirtyListener(dirtyWhacker);
    }
  }

  /**
   * Downloads the source code for the given class and puts it in the buffers map. This has no
   * effect if the buffer has already been loaded.
   */
  protected void loadBuffer(String fqClassName) throws MalformedURLException, IOException {
    bug("loadBuffer gets: " + fqClassName);
    if (!buffers.containsKey(fqClassName)) {
      String programSource = env.loadStringFromFile(env.classNameToFileName(fqClassName));

      // String where = interp.getMachine().getLoadPath();
      // if (where.startsWith("http")) {
      // String url = where + "request/" + fqClassName;
      // programSource = interp.getMachine().getWebClassLoader().downloadUrlToString(url);
      // } else {
      // String fileName = where + File.separator + SlippyUtils.codesetStrToFileStr(fqClassName);
      // bug("Attempting to load from file: " + fileName);
      // programSource = FileUtil.loadStringFromFile(fileName);
      // }
      buffers.put(fqClassName, programSource);
    }
  }

  /**
   * Shows the buffer for the given fully qualified class.
   */
  protected void showBuffer(String fqClassName) {
    SlippyEditor ed = editors.get(fqClassName);
    ed.colorize();
    editorTabs.setSelectedComponent(ed);
    editorTabs.getSelectedComponent().requestFocus();
  }

  protected void open() {
    bug("The OliveIDE.open() function should use an environment method. Fix this.");
    String where = interp.getMachine().getEnvironment().getLoadPath();
    bug("Looking for files here: " + where);
    String fqClassName = ClassChooser.showClassChooser(this, where);
    bug("Class chooser replies with: " + fqClassName);
    if (fqClassName != null && fqClassName.length() > 0 && interp.isValidClassName(fqClassName)) {
      try {
        loadBuffer(fqClassName);
        makeNewEditor(fqClassName);
        showBuffer(fqClassName);
      } catch (MalformedURLException ex) {
        bug("Bummer. Couldn't open " + fqClassName + ". It might not exist.");
        ex.printStackTrace();
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    } else {
      bug("Bummer. " + fqClassName + " is bogus.");
    }
    
  }

  protected void save() throws IOException {
    // 1. if this is the scratch buffer, use saveAs()
    SlippyEditor active = (SlippyEditor) editorTabs.getSelectedComponent();
    if (active.getFQClassName().equals(SCRATCH_NAME)) {
      saveAs();
    }

    // 2. Ask the environment to save the program string.
    String programString = URLEncoder.encode(active.getTextPane().getText(), "UTF-8");
    buffers.put(active.getFQClassName(), programString);
    env.save(active.getFQClassName(), programString);

    // 3. Colorize and un-dirty the buffer.
    active.colorize();
    active.setDirty(false);
  }

  protected void saveAs() throws IOException {
    String fqClassName = JOptionPane
        .showInputDialog("What's the fully qualified class name?\n\ne.g. org.mypeople.MyThing");
    if (fqClassName != null && fqClassName.length() > 0 && interp.isValidClassName(fqClassName)) {
      bug("Great, make a new file for " + fqClassName);
      SlippyEditor active = (SlippyEditor) editorTabs.getSelectedComponent();
      String programString = active.getTextPane().getText();
      if (makeNewBuffer(fqClassName, programString, false)) {
        showBuffer(fqClassName);
        save();
      } else {
        bug("Couldn't make a buffer with that name. Is it taken?");
      }
    } else {
      bug("Bummer. " + fqClassName + " is bogus.");
    }
  }

  private final void resetJavaBindings() {
    new Affine(interp);
    if (mouseThing != null) {
      surface.removeMouseListener(mouseThing);
      surface.removeMouseMotionListener(mouseThing);
    }
    try {
      Thing t = interp.importFromFile("OliveSoup", "org.six11.olive");
      SlippyObjectType type = new SlippyObjectType(t, interp);
      soup = new OliveSoup(type.make(new ArrayList<Object>()));
      interp.getMachine().addGlobal("soup", soup.getSlippyThing());
    } catch (Exception ex) {
      bug("Couldn't load org.six11.olive.OliveSoup. Continuing...");
    }
    mouseThing = new OliveMouseThing(soup);
    surface.addMouseMotionListener(mouseThing);
    surface.addMouseListener(mouseThing);
    new BoundDrawingBuffer(interp); // TODO: what is the point of this line?
  }

  protected void interpret() {
    Debug.outputStream.println("------- (interpreting at " + new Date() + ") ---");
    interp.getMachine().resetRuntimeState();
    resetJavaBindings();
    SlippyEditor editor = (SlippyEditor) editorTabs.getSelectedComponent();
    String fqClassName = editor.getFQClassName();
    String codesetStr = SlippyUtils.getCodesetName(fqClassName);
    String className = SlippyUtils.getClassName(fqClassName);
    interp.getMachine().pushFileName(env.getFullFileName(className, codesetStr));
    String programString = editor.getTextPane().getText();
    try {
      interp.handleInput(programString);
      List<String> buttonFunctionNames = interp.getMachine().getGlobalTable()
          .getNamesWithAnnotation("button");
      for (String s : buttonFunctionNames) {
        buttonize(s);
      }
      List<String> menuFunctionNames = interp.getMachine().getGlobalTable().getNamesWithAnnotation(
          "menu");
      for (String s : menuFunctionNames) {
        menuize(s);
      }
      editor.colorize();
    } finally {
      interp.getMachine().popFileName(false); // the false allows future invocations
    }
    surface.repaint();
  }

  protected void interpretSelection() {
    bug("interpretSelection() not implemented.");
  }

  protected void menuize(String name) {
    NamedAction slippyAction = makeSlippyAction(name, "menu");
    JMenuItem m = slippyMenuItems.get(slippyAction.getName());
    if (m != null) {
      getSlippyMenu().remove(m);
    }
    m = new JMenuItem(slippyAction);
    slippyMenuItems.put(slippyAction.getName(), m);
    getSlippyMenu().add(m);
    getSlippyMenu().revalidate();
    getSlippyMenu().repaint();
  }

  protected JMenu getSlippyMenu() {
    if (slippyMenu == null) {
      slippyMenu = new JMenu("Slippy Functions");
      // TODO: when moving to non-Applet, the getJMenuBar() went MIA. Fix this.
      // getJMenuBar().add(slippyMenu);
    }
    return slippyMenu;
  }

  protected void buttonize(String name) {
    NamedAction slippyAction = makeSlippyAction(name, "button");
    JButton b = slippyButtons.get(slippyAction.getName());
    if (b != null) {
      buttons.remove(b);
    }
    b = new JButton(slippyAction);
    slippyButtons.put(slippyAction.getName(), b);
    buttons.add(b);
    buttons.revalidate();
    buttons.repaint();
  }

  protected NamedAction makeSlippyAction(String name, String type) {
    NamedAction ret = null;
    Thing.Annotation annotation = interp.getMachine().getGlobalTable().getAnnotation(name, type);
    List<Tree> expressions = annotation.getExpressions();
    if (expressions.size() > 0) {
      Thing labelThing = interp.eval(expressions.get(0));
      bug("The label is: " + labelThing + " of type: " + labelThing.type);
      if (labelThing.type == Thing.Type.String) {
        Thing maybeFunction = interp.getMachine().getGlobalTable().getThing(name);
        if (maybeFunction.type == Thing.Type.Function) {
          NamedAction slippyAction = makeSlippyActionFromLabelAndFunction(labelThing.toString(),
              (Thing.Function) maybeFunction);
          slippySourceFiles.put(labelThing.toString(), interp.getMachine().getCurrentFile());
          bug("Action " + labelThing.toString() + " was defined in "
              + slippySourceFiles.get(labelThing.toString()));
          if (actions.containsKey(slippyAction.getName())) {
            bug("Warning: Overwriting action '" + slippyAction.getName() + "'");
          }
          actions.put(slippyAction.getName(), slippyAction);
          ret = slippyAction;
        } else {
          bug("Thing '" + name + "' should be a function but it is of type: " + maybeFunction.type);
        }
      }
    } else {
      bug("Annotation type '" + type
          + "' should have a string argument. It will serve as the button/menu label.");
    }
    return ret;
  }

  private NamedAction makeSlippyActionFromLabelAndFunction(final String label,
      final Thing.Function function) {
    NamedAction ret = new NamedAction(label) {
      public void activate() {
        String fn = slippySourceFiles.get(label);
        bug("Invoking '" + label + "', defined originally in " + fn);
        interp.getMachine().pushFileName(fn);
        try {
          interp.invokeFunction(function, new ArrayList<Thing>(), null);
        } finally {
          interp.getMachine().popFileName(false);
        }
      }
    };
    return ret;
  }

  public OliveSoup getSoup() {
    return soup;
  }

  public OliveDrawingSurface getDrawingSurface() {
    return surface;
  }

  private class RedirectedOutputStream extends ByteArrayOutputStream {

    private String lineSeparator;
    private ColoredTextPane text;

    RedirectedOutputStream(ColoredTextPane text) {
      super();
      lineSeparator = System.getProperty("line.separator");
      this.text = text;
    }

    public void flush() throws IOException {

      String record;
      synchronized (this) {
        super.flush();
        record = this.toString();
        super.reset();

        if (record.length() == 0 || record.equals(lineSeparator)) {
          return;
        }

        text.append(Color.BLACK, record + "\n");
      }
    }
  }
}
