package org.six11.sf;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLContext;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLProfile;
import javax.media.opengl.awt.GLJPanel;
import javax.media.opengl.fixedfunc.GLMatrixFunc;
import javax.media.opengl.glu.GLU;
import javax.swing.Timer;

import org.imgscalr.Scalr;
import org.six11.sf.Drag.Event;
import org.six11.util.data.FSM;
import org.six11.util.data.FSM.Transition;
import org.six11.util.data.Lists;
import org.six11.util.data.Statistics;
import org.six11.util.gui.BoundingBox;
import org.six11.util.gui.shape.ShapeFactory;
import org.six11.util.pen.Functions;
import org.six11.util.pen.PenEvent;
import org.six11.util.pen.PenListener;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.Vec;

import com.jogamp.opengl.util.awt.Screenshot;
import com.jogamp.opengl.util.awt.TextRenderer;

public class DrawingSurface extends GLJPanel implements GLEventListener, PenListener, Drag.Listener {

  // misc shit
  private long lastPenTime;
  private Statistics penLatency;

  // states
  public static final String OP = "op";
  public static final String SMOOTH = "smooth";
  public static final String FLOW = "flow";
  public static final String DRAW = "draw";
  public static final String IDLE = "idle";
  public static final String DRAGGING = "dragging";
  public static final String PAN = "pan";
  public static final String ZOOM = "zoom";

  // events
  private static final String MOVE = "move";
  private static final String PAUSE = "pause";
  private static final String UP = "up";
  private static final String DOWN = "down";
  private static final String TICK = "tick";
  private static final String START_ZOOM = "start zoom";
  private static final String START_PAN = "start pan";
  private static final String BUTTON_DOWN = "magic_button_down";
  private static final String BUTTON_UP = "magic_button_up";
  private static final String ARMED = "armed";
  private static final String SEARCH_DIR = "search_direction";
  private static final String DRAG_BEGIN = "drag_begin";

  public final static Color DEFAULT_DRY_COLOR = Color.GRAY.darker();
  public final static float DEFAULT_DRY_THICKNESS = 1.4f;

  protected static final int UNDO_REDO_THRESHOLD = 40;
  private static final double FS_SMOOTH_DAMPING = 0.25;
  private static final long TAP_DUR_THRESH = 350;
  private static final double TAP_DIST_THRESH = 7;
  private static final long TAP_TIMEOUT = 500;
  private static final long PAN_ZOOM_WIDGET_TIMEOUT = 2500;
  protected static final float ZOOM_SENSITIVITY = 0.015f; // larger == faster zoom, could get out of hand

  // flow selection variables.
  private List<Pt> fsRecent;
  private FSM fsFSM;
  private Timer fsTimer;
  private int fsPauseTimeout = 900;
  private Timer fsTickTimer;
  private int fsTickTimeout = 45;
  private Pt fsDown;
  private Segment fsNearestSeg; // segment currently flow-selected
  private Pt fsNearestPt; // point on segment currently selected
  private Pt fsTransitionPt;
  private Pt fsRecentPt;
  private int fsSmoothIndex; // epicenter index. valid after fsInitSelection(), invalid after pen-up.
  private double fsBubble = 10;
  private long fsStartTime;
  private Pt fsLastDeformPt;

  // snapshot/undo/redo search vars
  private Pt searchStart;
  private Pt dragPt;
  private boolean magicDown = false;
  private Snapshot previewSnapshot; // snapshot we are asked to preview (part of undo/redo scanning)
  private boolean requestSnapshot;

  // pan/zoom vars
  private boolean showPanZoomWidget; // should the pan/zoom widget be displayed?
  private long showPanZoomWidgetRefreshTime; // time the widget was last started or refreshed due to use.
  private Pt panZoomWidgetPt; // screen coordinate of pan/zoom widget, valid only when showPanZoomWidget is true
  private Pt panZoomBeginPt; // screen coordinate of point the user began a pan/zoom drag
  private Pt panZoomActivityPt; // screen coordinate of point the user is dragging during a pan/zoom
  private Rectangle2D[] panZoomRects; // 0: rectangle for pan zone, 1: rect for zoom zone 
  private Timer zoomPanTimer; // responsible for animating and turning off widget
  private float zoomInitialValue; // camera zoom at beginning of zoom action

  // recent pen activity vars
  private Pt screenRecentPt; // screen coordinates of the last pen event that had a location (IDLE does not!)
  private Pt hoverPt;
  private Pt prev;
  private List<Pt> currentScribble;

  // the model.
  protected SketchBook model;

  // flag to create and set a thumbnail of the currently selected stencil. given to the model.
  private boolean requestStencilThumbnail;

  // rendering-related vars
  private TextRenderer textRenderer18;
  protected GLU glu;
  protected SketchRenderer renderer;
  private Map<Integer, TextRenderer> textRenderers;
  private String textInput;

  // most recent rendering data used to unproject screen coordinates into model coordinates.
  float[] projmatrix = new float[16];
  int[] viewport = new int[4];
  float mvmatrix[] = new float[16];
  private List<Pt> taps;
  private boolean suspendRedraw;

  public DrawingSurface(SketchBook model) {
    super(new GLCapabilities(GLProfile.getDefault()));
    this.model = model;
    this.renderer = new SketchRenderer();
    this.penLatency = new Statistics();
    penLatency.setMaximumN(40);
    taps = new ArrayList<Pt>();
    zoomPanTimer = new Timer(16, new ActionListener() { // fires often in order to animate it
          public void actionPerformed(ActionEvent ev) {
            if (showPanZoomWidget) { // showing and expired ?
              long dur = System.currentTimeMillis() - showPanZoomWidgetRefreshTime;
              if (dur > PAN_ZOOM_WIDGET_TIMEOUT) {
                showPanZoomWidget = false;
                panZoomWidgetPt = null;
                zoomPanTimer.stop();
              }
            }
            repaint();
          }
        });
    zoomPanTimer.setRepeats(true);

    addGLEventListener(this);
    setName("DrawingSurface");

    textRenderers = new HashMap<Integer, TextRenderer>();
    Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
      public void eventDispatched(AWTEvent ev) {
        KeyEvent kev = (KeyEvent) ev;
        if (kev.getKeyCode() == KeyEvent.VK_SPACE) {
          if (ev.getID() == KeyEvent.KEY_PRESSED) {
            if (!magicDown) {
              magicDown = true;
              fsFSM.addEvent(BUTTON_DOWN);
            }
          } else if (ev.getID() == KeyEvent.KEY_RELEASED) {
            magicDown = false;
            fsFSM.addEvent(BUTTON_UP);
          }
        }
      }
    }, AWTEvent.KEY_EVENT_MASK);
    fsInit();
  }

  @Override
  public void init(GLAutoDrawable drawable) {
    bug("gl init");
    GL2 gl = drawable.getGL().getGL2();
    glu = new GLU();
    gl = drawable.getGL().getGL2();
    gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
    gl.glLoadIdentity();
    gl.glOrtho(-1, 1, 1, -1, 0, 1);
    gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
    gl.glClearColor(1f, 1f, 1f, 1f);
    gl.glDisable(GL.GL_DEPTH_TEST);
    gl.glEnable(GL.GL_BLEND);
    gl.glEnable(GL.GL_LINE_SMOOTH);
    gl.glHint(GL.GL_LINE_SMOOTH_HINT, GL.GL_NICEST);
    gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

    renderer.init(drawable);
    textRenderer18 = new TextRenderer(new Font("SansSerif", Font.BOLD, 18));
    textRenderer18.setSmoothing(true);
    textRenderers.put(18, textRenderer18);

    TextRenderer textRenderer12 = new TextRenderer(new Font("SansSerif", Font.PLAIN, 12));
    textRenderer12.setSmoothing(true);
    textRenderers.put(12, textRenderer12);

    if (model.getNotebook().shouldLoadDisplayLists()) {
      bug("*** Compiling display lists for all pages/snapshots. This will take a while.");
      Page formerPage = model.getNotebook().getCurrentPage();
      Dimension size = getSize();
      bug("My size: " + size.width + " x " + size.height);
      for (Page page : model.getNotebook().getPages()) {
        if (page.hasModelData()) {
          model.getNotebook().setCurrentPage(page);
          page.getSnapshotMachine().setSnapshotsEnabled(false);
          for (int snapIdx = 0; snapIdx < page.getSnapshotMachine().length(); snapIdx++) {
            Snapshot snap = page.getSnapshotMachine().get(snapIdx);
            page.getSnapshotMachine().load(snap);
            bug("Compile display list for page " + page.getPageNumber() + " snapshot " + snapIdx);
            int snapDList = gl.glGenLists(1);
            snap.setDisplayListID(snapDList);
            gl.glNewList(snapDList, GL2.GL_COMPILE_AND_EXECUTE);
            renderContent(drawable, size);
            gl.glEndList();
            if (snapIdx == page.getSnapshotMachine().getCurrentIdx()) {
              setPageThumbnail(drawable);
            }
          }
          page.getSnapshotMachine().setSnapshotsEnabled(true);
        }
      }
      model.getNotebook().setCurrentPage(formerPage);
      bug("*** Done compiling display lists. You may resume your life now.");
    }
  }

  @Override
  public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
    bug("reshape! new size: " + width + " x " + height);
    GL2 gl = drawable.getGL().getGL2();
    gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
    gl.glLoadIdentity();
    gl.glOrtho(x, x + width, y + height, y, 0, 1);
    gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
  }

  @Override
  public void display(GLAutoDrawable drawable) {
    if (suspendRedraw) {
      return;
    }
    long start = System.currentTimeMillis();
    GL2 gl = drawable.getGL().getGL2();
    Dimension size = getSize();
    int displayList = 0;
    // do camera stuff first
    Camera cam = model.getCamera();

    gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
    gl.glLoadIdentity();
    float[] ortho = cam.getOrthoValues(size);
    gl.glOrtho(ortho[0], ortho[1], ortho[2], ortho[3], 0, 1);
    gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);

    // store info for translating mouse coords to model coords
    gl.glGetFloatv(GLMatrixFunc.GL_PROJECTION_MATRIX, projmatrix, 0);
    gl.glGetIntegerv(GL.GL_VIEWPORT, viewport, 0);
    gl.glGetFloatv(GLMatrixFunc.GL_MODELVIEW_MATRIX, mvmatrix, 0);

    if (previewSnapshot != null) {
      gl.glCallList(previewSnapshot.getDisplayListID());
    } else {
      // if we have been requested to make a snapshot, save this round to a display list.
      if (requestSnapshot) {
        Snapshot snap = model.getSnapshotMachine().save();
        if (snap != null) {
          requestSnapshot = false;
          displayList = gl.glGenLists(1);
          snap.setDisplayListID(displayList);
          gl.glNewList(displayList, GL2.GL_COMPILE_AND_EXECUTE);
        }
      }
      renderContent(drawable, size);

      if (displayList > 0) {
        // when a display list was created, end the list and save a thumbnail for the current page
        gl.glEndList();
        setPageThumbnail(drawable);
      }

      if (requestStencilThumbnail) {
        setStencilThumbnail(drawable);
        requestStencilThumbnail = false;
      }
    }

    if (hoverPt != null) {
      gl.glLineWidth(2f);
      gl.glColor3fv(SketchRenderer.red, 0);
      renderer.dot(hoverPt, 5);
    }

    if (fsFSM.getState().equals(SEARCH_DIR)) {
      float[] undoColor = SketchRenderer.lightGray;
      float[] redoColor = SketchRenderer.lightGray;
      if (model.getSnapshotMachine().canUndo()) {
        undoColor = SketchRenderer.black;
      }
      if (model.getSnapshotMachine().canRedo()) {
        redoColor = SketchRenderer.black;
      }
      textRenderer18.beginRendering(drawable.getWidth(), drawable.getHeight());
      textRenderer18.setColor(undoColor[0], undoColor[1], undoColor[2], undoColor[3]);
      textRenderer18.draw("Undo", size.width - 120, size.height - 20);
      textRenderer18.setColor(redoColor[0], redoColor[1], redoColor[2], redoColor[3]);
      textRenderer18.draw("Redo", size.width - 60, size.height - 20);
      textRenderer18.endRendering();

      gl.glLineWidth(1f);
      gl.glColor3fv(undoColor, 0);
      Pt undoStart = new Pt(size.width - 80, 40);
      Pt undoEnd = undoStart.getTranslated(-40, 0);
      renderer.arrow(undoStart, undoEnd);

      gl.glColor3fv(redoColor, 0);
      Pt redoStart = new Pt(size.width - 60, 40);
      Pt redoEnd = redoStart.getTranslated(40, 0);
      renderer.arrow(redoStart, redoEnd);
    }

    // switch back to non-scaled, non-translated ortho mode to draw UI things
    gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
    gl.glLoadIdentity();
    ortho = Camera.getOrthoValues(size, 1, 0, 0);
    gl.glOrtho(ortho[0], ortho[1], ortho[2], ortho[3], 0, 1);
    gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);

    if (showPanZoomWidget) {
      panZoomRects = renderer.panZoomWidget(gl, panZoomWidgetPt);
    }

    long end = System.currentTimeMillis();
    long dur = end - start;

    // show possibly helpful info
    Page curPage = model.getNotebook().getCurrentPage();
    int maxPages = model.getNotebook().getPages().size();
    int snapIdx = curPage.getSnapshotMachine().getCurrentIdx() + 1;
    int maxIdx = curPage.getSnapshotMachine().length();
    String pageInfo = "Page " + (curPage.getPageNumber() + 1) + " / " + maxPages;
    String snapInfo = "Snap " + snapIdx + " / " + maxIdx;
    textRenderer18.beginRendering(drawable.getWidth(), drawable.getHeight());
    textRenderer18.setColor(0.4f, 0.4f, 0.4f, 0.4f);
    textRenderer18.draw("Pan: " + num(cam.getPanX()) + ", " + num(cam.getPanY()), size.width - 180,
        120);
    textRenderer18.draw("Zoom: " + num(cam.getZoom()), size.width - 180, 100);
    textRenderer18.draw(pageInfo, size.width - 180, 80);
    textRenderer18.draw(snapInfo, size.width - 180, 60);
    textRenderer18.draw("Render: " + dur + "ms", size.width - 180, 40);
    textRenderer18.draw("Pen Latency: " + (long) penLatency.getMean() + "ms", size.width - 180, 20);
    textRenderer18.endRendering();
  }

  /**
   * Draws the border and content. This does not put up window dressing like the latency text. You
   * can wrap calls to this method between open GL display list definition begin/end statements.
   * 
   * @param drawable
   * @param size
   */
  private void renderContent(GLAutoDrawable drawable, Dimension size) {
    GL2 gl = drawable.getGL().getGL2(); // get GL pipe handle

    gl.glClear(GL.GL_COLOR_BUFFER_BIT); // clear screen.

    // draw border.
    gl.glColor3fv(SketchRenderer.black, 0);
    float thick = 1.5f;
    float thickHalf = thick / 2f;
    gl.glLineWidth(thick);
    rect(gl, thickHalf, size.width - thick, thickHalf, size.height - thick);

    // render scribble and model data
    renderer.render(model, drawable, currentScribble, true, this);
  }

  private void setStencilThumbnail(GLAutoDrawable drawable) {
    Set<Stencil> stencils = model.getSelectedStencils();
    BoundingBox bb = new BoundingBox();
    for (Stencil stencil : stencils) {
      Shape shape = stencil.getShape(false);
      List<Pt> points = ShapeFactory.makePointList(shape.getPathIterator(null));
      bb.addAll(points);
    }
    BufferedImage bigImage = takeScreenShot(drawable, bb);
    BufferedImage draggingThumb = Scalr.resize(bigImage, 48);
    model.setDraggingThumbImage(draggingThumb);
  }

  private void setPageThumbnail(GLAutoDrawable drawable) {
    BoundingBox bb = new BoundingBox();
    //    for (Segment seg : model.getGeometry()) {
    //      bb.addAll(seg.getPointList());
    //    }
    bb.add(getVisibleRect());
    if (bb.isValid()) {
      BufferedImage bigImage = takeScreenShot(drawable, bb);
      int currentThumbWidth = model.getEditor().getGrid().getCurrentThumbWidth();
      BufferedImage thumb = Scalr.resize(bigImage, currentThumbWidth);
      model.getNotebook().getCurrentPage().setTinyThumb(thumb);
    }
  }

  public BufferedImage takeScreenShot(GLAutoDrawable drawable, BoundingBox bb) {
    int windowHeight = drawable.getHeight();
    double minX = bb.getMinX();
    double maxY = bb.getMaxY();
    Pt corner = new Pt(minX, maxY);
    int width = bb.getWidthInt();
    int height = bb.getHeightInt();
    float[] windowCorner = unproject(drawable.getGL().getGL2(), corner.fx(), corner.fy());
    BufferedImage bigImage = Screenshot.readToBufferedImage((int) windowCorner[0], windowHeight
        - (int) windowCorner[1], width, height, false);
    return bigImage;
  }

  @Override
  public void dispose(GLAutoDrawable drawable) {
    bug("gl dispose");
  }

  protected TextRenderer getTextRenderer(int pointSize) {
    TextRenderer tr = textRenderers.get(pointSize);
    return tr;
  }

  private final void fsInit() {
    fsRecent = new ArrayList<Pt>();
    fsTimer = new Timer(fsPauseTimeout, new ActionListener() {
      public void actionPerformed(ActionEvent ev) {
        bug("tick 1");
        fsCheck();
      }
    });
    fsTimer.setRepeats(false);
    fsTickTimer = new Timer(fsTickTimeout, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        fsFSM.addEvent(TICK);
        repaint();
        //        display();
      }
    });
    FSM f = new FSM("Flow Selection FSM");
    f.addState(IDLE);
    f.addState(DRAW);
    f.addState(PAN);
    f.addState(ZOOM);
    f.addState(DRAGGING);
    f.addState(FLOW);
    f.addState(OP);
    f.addState(SMOOTH);
    f.addState(ARMED);
    f.addState(SEARCH_DIR);
    f.setStateEntryCode(DRAW, new Runnable() {
      public void run() {
        fsInitTimer();
      }
    });
    f.setStateEntryCode(IDLE, new Runnable() {
      public void run() {
        fsDown = null;
        fsNearestSeg = null; // segment currently flow-selected
        fsNearestPt = null; // point on segment currently selected
        fsTimer.stop();
        fsTransitionPt = null;
        fsRecent.clear();
      }
    });
    f.setStateEntryCode(FLOW, new Runnable() {
      public void run() {
        fsTickTimer.restart();
      }
    });
    f.setStateEntryCode(SEARCH_DIR, new Runnable() {
      public void run() {
        searchStart = fsDown.copyXYT();
      }
    });
    f.addTransition(new Transition(START_PAN, IDLE, PAN) {
      public void doAfterTransition() {
        // clear the way for the next round of panning. this lets
        // us start from the new pen drag locations
        panZoomBeginPt = null;
      }
    });
    f.addTransition(new Transition(START_ZOOM, IDLE, ZOOM) {
      public void doAfterTransition() {
        panZoomBeginPt = new Pt(fsDown.getDouble("worldX"), fsDown.getDouble("worldY"));
        Camera cam = model.getCamera();
        zoomInitialValue = cam.getZoom();
      }
    });

    f.addTransition(new Transition(DOWN, IDLE, DRAW) {
      public void doBeforeTransition() {
        fsTransitionPt = fsRecentPt;
      }
    });
    f.addTransition(new Transition(MOVE, DRAW, DRAW) {
      public void doBeforeTransition() {
        addFsRecent(fsRecentPt);
      }
    });
    f.addTransition(new Transition(DRAG_BEGIN, DRAW, DRAGGING));
    f.addTransition(new Transition(UP, DRAGGING, IDLE));

    f.addTransition(new Transition(UP, PAN, IDLE));
    f.addTransition(new Transition(UP, ZOOM, IDLE));
    f.addTransition(new Transition(MOVE, PAN, PAN) {
      public void doAfterTransition() {
        showPanZoomWidgetRefreshTime = System.currentTimeMillis();
        panZoomActivityPt = screenRecentPt; // screen coordinates
        if (panZoomBeginPt != null) {
          Vec change = new Vec(panZoomBeginPt, panZoomActivityPt);
          float zoom = model.getCamera().getZoom();
          change = change.getScaled(1f / zoom);
          float targetX = -(float) change.getX(); // get delta from pendown location
          float targetY = -(float) change.getY();
          model.getCamera().translateBy(getSize(), targetX, targetY);
        }
        panZoomBeginPt = panZoomActivityPt;
      }
    });
    f.addTransition(new Transition(MOVE, ZOOM, ZOOM) {
      public void doAfterTransition() {
        showPanZoomWidgetRefreshTime = System.currentTimeMillis();
        panZoomActivityPt = screenRecentPt;
        float changeY = panZoomBeginPt.fy() - panZoomActivityPt.fy();
        float target = zoomInitialValue + (changeY * ZOOM_SENSITIVITY);
        model.getCamera().zoomTo(getSize(), target);
      }
    });
    f.addTransition(new Transition(UP, DRAW, IDLE));
    f.addTransition(new Transition(PAUSE, DRAW, FLOW) {
      public void doAfterTransition() {
        fsRecent.clear();
        fsInitSelection();
      }
    });
    f.addTransition(new Transition(TICK, FLOW, FLOW) {
      public void doAfterTransition() {
        fsGrowSelection();
      }
    }); // causes flow selection to grow
    f.addTransition(new Transition(UP, FLOW, IDLE));
    f.addTransition(new Transition(MOVE, FLOW, OP) {
      public void doBeforeTransition() {
        addFsRecent(fsRecentPt);
      }

      public boolean veto() {
        boolean ret = false;
        if (fsCheck()) {
          ret = true;
        }
        return ret;
      }

      public void doAfterTransition() {
        fsRecent.clear();
        fsTransitionPt = fsRecentPt;
      }

    });
    f.addTransition(new Transition(MOVE, OP, OP) {
      public void doBeforeTransition() {
        addFsRecent(fsRecentPt);
        long now = System.currentTimeMillis();
        long target = now - fsPauseTimeout;
        int transitionIdx = -1;
        for (int i = fsRecent.size() - 1; i >= 0; i--) {
          Pt pt = fsRecent.get(i);
          if (pt.getTime() < target) {
            transitionIdx = i;
            fsTransitionPt = pt;
            break;
          }
        }
        if (transitionIdx >= 0) {
          for (int i = 0; i < transitionIdx; i++) {
            fsRecent.remove(0);
          }
        }
        fsDeform(fsRecentPt);
      }
    });
    f.addTransition(new Transition(TICK, OP, OP) {
      @Override
      public void doAfterTransition() {
        fsCheck();
      }
    });
    f.addTransition(new Transition(UP, OP, IDLE) {
      public void doBeforeTransition() {
        fsSaveChanges();
      }
    });
    f.addTransition(new Transition(PAUSE, OP, SMOOTH) {
      public void doAfterTransition() {
        fsRecent.clear();
      }
    });
    f.addTransition(new Transition(MOVE, SMOOTH, OP) {
      public void doBeforeTransition() {
        addFsRecent(fsRecentPt);
      }

      public boolean veto() {
        boolean ret = false;
        if (fsCheck()) {
          ret = true;
        }
        return ret;
      }
    });
    f.addTransition(new Transition(TICK, SMOOTH, SMOOTH) {
      public void doAfterTransition() {
        fsSmooth();
      }
    });
    f.addTransition(new Transition(UP, SMOOTH, IDLE) {
      public void doBeforeTransition() {
        fsSaveChanges();
      }
    });
    f.addTransition(new Transition(BUTTON_DOWN, IDLE, ARMED));
    f.addTransition(new Transition(BUTTON_UP, ARMED, IDLE) {
      public void doAfterTransition() {
        model.getEditor().go();
      }
    });
    f.addTransition(new Transition(MOVE, ARMED, SEARCH_DIR));
    f.addTransition(new Transition(MOVE, SEARCH_DIR, SEARCH_DIR) {
      public void doBeforeTransition() {
        if ((searchStart != null) && (dragPt != null)) {
          double dx = dragPt.getX() - searchStart.getX();
          if (dx < -UNDO_REDO_THRESHOLD) {
            searchStart = dragPt.copyXYT();
            model.undoPreview();
          } else if (dx > UNDO_REDO_THRESHOLD) {
            searchStart = dragPt.copyXYT();
            model.redoPreview();
          }
        }
      }
    });
    f.addTransition(new Transition(UP, SEARCH_DIR, SEARCH_DIR) {
      public void doBeforeTransition() {
        searchStart = null;
        dragPt = null;
      }
    });
    f.addTransition(new Transition(DOWN, SEARCH_DIR, SEARCH_DIR) {
      public void doBeforeTransition() {
        searchStart = fsDown.copyXYT();
        dragPt = searchStart.copyXYT();
      }
    });
    f.addTransition(new Transition(BUTTON_UP, SEARCH_DIR, IDLE) {
      public void doBeforeTransition() {
        model.undoRedoComplete();
      }
    });

    //    f.addChangeListener(new ChangeListener() {
    //      public void stateChanged(ChangeEvent ev) {
    //        bug("state: " + fsFSM.getState());
    //      }      
    //    });
    this.fsFSM = f;
  }

  private void fsSmoothPair(int nearIdx, int farIdx, List<Pt> def) {
    Pt a = def.get(nearIdx); // near
    Pt b = def.get(farIdx); // far
    Vec toNext = new Vec(a, b);
    double aStr = a.getDouble("fsStrength");
    double bStr = b.getDouble("fsStrength");
    double attenuation = bStr - aStr;
    double dampedScale = bStr * FS_SMOOTH_DAMPING;
    Vec dampedVec = toNext.getScaled(dampedScale);
    a.move(dampedVec);
    double dampedAttenuation = attenuation * FS_SMOOTH_DAMPING;
    a.setDouble("fsStrength", aStr + dampedAttenuation);
  }

  private void fsSmooth() {
    List<Pt> def = fsNearestSeg.getDeformedPoints();
    for (int i = fsSmoothIndex - 2; i >= 0; i--) {
      int nearIdx = i + 1;
      int farIdx = i;
      fsSmoothPair(nearIdx, farIdx, def);
    }
    for (int i = fsSmoothIndex + 2; i < def.size(); i++) {
      int nearIdx = i - 1;
      int farIdx = i;
      fsSmoothPair(nearIdx, farIdx, def);
    }
    fsNearestSeg.calculateParameters(def);
    repaint();
  }

  protected void addFsRecent(Pt pt) {
    if (fsRecent.isEmpty()) {
      fsRecent.add(pt);
    } else if (!Lists.getLast(fsRecent).isSameLocation(pt)) {
      fsRecent.add(pt);
    }
  }

  /**
   * Checks to see if the pen has remained relatively still since the last 'special transition
   * change' it triggers a PAUSE event. At least 'fsPauseTimeout' milliseconds must have passed for
   * this to trigger. If a PAUSE event is sent, this returns true.
   * 
   * @return
   */
  private boolean fsCheck() {
    boolean paused = false;
    long now = System.currentTimeMillis();
    long then = fsTransitionPt.getTime();
    if (now > (then + fsPauseTimeout)) {
      // pause is possible. take the approach of assuming there 
      // is a pause but using fsRecent to disprove it.
      paused = true;
      double maxD = 0;
      for (int i = fsRecent.size() - 1; i >= 0; i--) {
        Pt pt = fsRecent.get(i);
        long dur = now - pt.getTime();
        if (dur < fsPauseTimeout) {
          maxD = Math.max(maxD, pt.distance(fsTransitionPt));
          if (maxD > fsBubble) {
            paused = false;
            break;
          }
        } else {
          bug("checking for pause: points are too old. breaking out.");
          break; // points are too old. stop looking.
        }
      }
      if (paused) {
        fsFSM.addEvent(PAUSE);
      } else {
        bug("checking for pause: moved too far (" + num(maxD) + ")");
      }
    } else {
      bug("checking for pause: not enough time has passed");
    }
    return paused;
  }

  private void fsInitTimer() {
    fsTimer.stop();
    fsTimer.setInitialDelay(fsPauseTimeout);
    fsTimer.restart();
  }

  private void fsGrowSelection() {
    long t = System.currentTimeMillis() - fsStartTime;
    double f = fsFull(t);
    List<Pt> def = fsNearestSeg.getDeformedPoints();
    for (int i = 0; i < def.size(); i++) {
      Pt pt = def.get(i);
      double effort = pt.getDouble("fsEffort");
      double numerator = effort * Math.PI;
      double s = 0.0;
      if (effort < f) {
        s = (Math.cos(numerator / f) + 1.0) / 2.0;
      }
      pt.setDouble("fsStrength", s);
    }
    repaint();
    //    display();
  }

  private void fsDeform(Pt recent) {
    if ((fsLastDeformPt != null) && (fsNearestSeg != null)) {
      Vec dir = new Vec(fsLastDeformPt, recent);
      double m = dir.mag();
      List<Pt> def = fsNearestSeg.getDeformedPoints();
      if (def != null) {
        for (Pt pt : def) {
          double s = pt.getDouble("fsStrength");
          Vec amt = dir.getVectorOfMagnitude(s * m);
          if (Double.isNaN(amt.getX()) || Double.isNaN(amt.getY())) {
            // avoid scary NaNs.
          } else {
            pt.move(amt);
          }
        }
        if (fsNearestSeg.isClosed()) {
          switch (fsNearestSeg.getType()) {
            case Circle:
            case Ellipse:
              Blob asBlob = new Blob(fsNearestSeg.getDelegate());
              fsNearestSeg.setDelegate(asBlob);
              break;
            case Blob:
              // no action needed.
              break;
            default:
              bug("deforming closed thing that isn't yet supported. add a clause here to make it work.");
          }
        }
        fsNearestSeg.calculateParameters(def);
      } else {
        bug("Def was null");
      }
    }
    fsLastDeformPt = recent;
    //    display();
    repaint();
  }

  private void fsSaveChanges() {
    if (fsNearestSeg != null) {
      List<Pt> choppySpline = fsNearestSeg.asSpline().getDownsample(10);
      fsNearestSeg.calculateParameters(choppySpline);
      fsNearestSeg.clearDeformation();
      model.getConstraints().wakeUp();
      model.getSnapshotMachine().requestSnapshot("flow selection finished");
    }
  }

  private double fsFull(long elapsed) {
    // fully select P pixels per second
    double p = 70;
    return (elapsed * p) / 1000.0;
  }

  public Segment getFlowSelectionSegment() {
    return fsNearestSeg;
  }

  private void fsInitSelection() {
    fsStartTime = System.currentTimeMillis();
    if (fsDown != null) {
      Segment nearestSeg = null;
      Pt nearestPoint = null;
      double nearestDist = Double.MAX_VALUE;
      for (Segment seg : model.getGeometry()) {
        Pt thisPoint = seg.getNearestPoint(fsDown);
        double thisDist = thisPoint.distance(fsDown);
        if (thisDist < nearestDist) {
          nearestDist = thisDist;
          nearestSeg = seg;
          nearestPoint = thisPoint;
        }
      }
      if (nearestSeg != null) {
        fsLastDeformPt = null;
        fsNearestSeg = nearestSeg;
        fsNearestPt = nearestPoint;
        fsNearestSeg.storeParaPointsForDeformation();
        List<Pt> def = fsNearestSeg.getDeformedPoints();
        double[] distance;
        if (fsNearestSeg.isClosed()) {
          distance = Functions.calculateCurvilinearDistanceClosedCircuit(def, nearestPoint);
        } else {
          distance = Functions.calculateCurvilinearDistance(def, fsNearestPt);
        }
        int idxTarget = 0;
        double minVal = Double.MAX_VALUE;
        for (int i = 0; i < distance.length; i++) {
          if (distance[i] < minVal) {
            minVal = distance[i];
            idxTarget = i;
          }
          def.get(i).setDouble("fsEffort", distance[i]);
          def.get(i).setDouble("fsStrength", 0.0); // in case it has stale data from a previous go.
        }
        fsSmoothIndex = idxTarget;
      }
    }
  }

  ////  ////  ////  ////  ////  ////  ////  ////
  ////
  ////            Utility Functions.
  ////
  ////  ////  ////  ////  ////  ////  ////  ////

  protected float[] unproject(GL2 gl, float x, float y) {
    int realy = 0;// GL y coord pos
    float wcoord[] = new float[4];// wx, wy, wz
    realy = viewport[3] - (int) y - 1; // have to invert y values because Java and OpenGL disagree which way is up.
    glu.gluUnProject(x, realy, 0.0f, //
        mvmatrix, 0, projmatrix, 0, viewport, 0, wcoord, 0);
    return wcoord;
  }

  public float[] project(GL2 gl, float x, float y) {
    float scoord[] = new float[4];// screen x, y, don't care about z and homo coord
    glu.gluProject(x, y, 0.0f, //
        mvmatrix, 0, projmatrix, 0, viewport, 0, scoord, 0);
    return scoord;
  }

  protected Pt mkPt(float[] coords, long time) {
    Pt ret = new Pt(coords[0], coords[1], time);
    return ret;
  }

  protected void rect(GL2 gl, float minX, float width, float minY, float height) {
    gl.glBegin(GL.GL_LINE_LOOP);
    {
      gl.glVertex2f(minX, minY);
      gl.glVertex2f(minX, minY + height);
      gl.glVertex2f(minX + width, minY + height);
      gl.glVertex2f(minX + width, minY);
    }
    gl.glEnd();
  }

  public String getFlowSelectionState() {
    return fsFSM.getState();
  }

  @Override
  public void handlePenEvent(PenEvent ev) {
    long dur = ev.getTimestamp() - lastPenTime;
    lastPenTime = ev.getTimestamp();
    if (dur < 1000) {
      penLatency.addData(dur);
    }
    Pt modelPt = null;
    final Pt world = ev.getPt();
    if (world == null) {
      // world point can be null when this is an Idle event
    } else {
      GLContext context = getContext();
      if (context != null) {
        int current = context.makeCurrent();
        if (current != GLContext.CONTEXT_NOT_CURRENT) {
          GL2 gl = context.getGL().getGL2();
          float[] coords = unproject(gl, world.ix(), world.iy());
          modelPt = mkPt(coords, world.getTime());
          fsRecentPt = modelPt;
          screenRecentPt = world.copyXYT();
        } else {
          bug("Could not make open gl context current. Pen Event will be ignored.");
        }
        context.release();
      }
    }
    switch (ev.getType()) {
      case Down:
        fsDown = modelPt.copyXYT();
        fsDown.setDouble("worldX", world.x); // record world point here to avoid having to project it later
        fsDown.setDouble("worldY", world.y);
        if (showPanZoomWidget && (panZoomRects != null)) {
          if (panZoomRects[0].contains(world)) {
            fsFSM.addEvent(START_PAN);
          } else if (panZoomRects[1].contains(world)) {
            fsFSM.addEvent(START_ZOOM);
          }
        }
        fsFSM.addEvent(DOWN);
        fsDown.setDouble("tap_dist", 0);
        if (fsFSM.getState().equals(DRAW)) {
          model.retainVisibleGuides();
          hoverPt = null;
          if (model.isPointOverSelection(modelPt)) {
            fsFSM.addEvent(DRAG_BEGIN);
            model.setDraggingSelection(true);
          } else {
            Collection<GuidePoint> nearbyGuidePoints = model.findGuidePoints(modelPt, true);
            if (nearbyGuidePoints.isEmpty()) {
              prev = modelPt;
              currentScribble = new ArrayList<Pt>();
              currentScribble.add(prev);
            } else {
              GuidePoint nearestGP = null;
              double nearestDist = Double.MAX_VALUE;
              for (GuidePoint gpt : nearbyGuidePoints) {
                double d = gpt.getLocation().distance(modelPt);
                if (d < nearestDist) {
                  nearestDist = d;
                  nearestGP = gpt;
                }
              }
              model.setDraggingGuidePoint(nearestGP);
            }
            model.startScribble(modelPt);
            model.clearSelectedStencils();
          }
        }
        break;
      case Drag:
        fsFSM.addEvent(MOVE);
        // accumulate total stroke length in fsDown["tap_dist"], used to detect taps on idle.
        if (dragPt != null) {
          double thisDist = dragPt.distance(modelPt);
          double prevDist = fsDown.getDouble("tap_dist");
          fsDown.setDouble("tap_dist", prevDist + thisDist);
        } else {
          double thisDist = fsDown.distance(modelPt);
          fsDown.setDouble("tap_dist", thisDist);
        }
        dragPt = modelPt;
        hoverPt = null;
        Pt here = modelPt;
        if (model.isDraggingSelection()) {
          bug("You should never see this! Shouldn't send layers drag events when drag selection is true");
        } else if (model.isDraggingGuide()) {
          model.dragGuidePoint(here);
        } else if (fsFSM.getState().equals(OP)) {

        } else if (fsFSM.getState().equals(DRAW) || fsFSM.getState().equals(FLOW)) {
          if (currentScribble != null) {
            currentScribble.add(here);
          }
          model.addScribble(modelPt);
        }
        break;
      case Idle:
        // examine fsDown["tap_dist"] and duration to detect a tap
        boolean wasTap = false;
        double tapDist = fsDown.getDouble("tap_dist");
        long tapDur = lastPenTime - fsDown.getTime();
        if ((tapDist < TAP_DIST_THRESH) && (tapDur < TAP_DUR_THRESH)) {
          wasTap = true;
          Pt tapPt = fsDown.copyXYT();
          addTap(tapPt);
          int howMany = countCurrentTaps(System.currentTimeMillis());
          if (howMany >= 2) {
            showPanZoomWidget = true;
            showPanZoomWidgetRefreshTime = System.currentTimeMillis();
            panZoomWidgetPt = new Pt(fsDown.getDouble("worldX"), fsDown.getDouble("worldY"));
            zoomPanTimer.restart();
          }
        }
        boolean wasDrawing = fsFSM.getState().equals(DRAW);
        fsFSM.addEvent(UP);
        if (model.isDraggingGuide()) {
          model.setDraggingGuidePoint(null);
        } else {
          if (!wasTap && wasDrawing) {
            Sequence seq = model.endScribble(modelPt);
            if (seq != null) {
              model.addInk(new Ink(seq));
            }
          }
          clearScribble();
        }
        dragPt = null;
        break;
      case Enter:
        break;
      case Exit:
        hoverPt = null;
        break;
      case Hover:
        hoverPt = modelPt.copyXYT();
        break;
    }
    repaint();
  }

  private void addTap(Pt tapPt) {
    taps.add(0, tapPt); // higher indices = older
  }

  /**
   * Count the number of taps that the user has recently completed in the same general area. The
   * definition of 'recently' and 'same general area' are defined by the constants TAP_TIMEOUT and
   * TAP_DIST_THRESHOLD.
   */
  private int countCurrentTaps(long referenceTime) {
    int count = 0;
    if (taps.size() > 0) {
      Pt pt = taps.get(0);
      long dur = referenceTime - pt.getTime();
      if (dur < TAP_TIMEOUT) {
        count = count + 1; // first one is good
      }
    }
    int badIdx = -1;
    if (count > 0) {
      // if the first one is recent, check the time/location of the rest.
      for (int i = 0; i < (taps.size() - 1); i++) {
        Pt left = taps.get(i);
        Pt right = taps.get(i + 1);
        double dist = left.distance(right);
        long dur = left.getTime() - right.getTime();
        if ((dist < TAP_DIST_THRESH) && (dur < TAP_TIMEOUT)) {
          count = count + 1; // pair (i, i+1) is good
        } else {
          badIdx = i + 1;
          break; // indices 'count' and higher are no longer relevant.
        }
      }
    }
    // clean up the taps list.
    if (badIdx >= 0) {
      while (taps.size() > badIdx) {
        taps.remove(badIdx);
      }
    }
    return count;
  }

  /**
   * Returns the last location the pen was hovering, or null if the pen is down or outside of the
   * component.
   */
  public Pt getHoverPoint() {
    return hoverPt;
  }

  public void clearScribble() {
    currentScribble = null;
  }

  public void setPreview(Snapshot snap) {
    this.previewSnapshot = snap;
    //    bug("Please show snapshot " + previewSnapshot.getID() + " (display list "
    //        + previewSnapshot.getDisplayListID());
    //    display();
    repaint();
  }

  public void clearPreview() {
    previewSnapshot = null;
    bug("Stop showing preview.");
    //    display();
    //    repaint();
  }

  public void snapshot() {
    requestSnapshot = true;
  }

  public void requestStencilThumb() {
    requestStencilThumbnail = true;
  }

  @Override
  public void dragMove(Event ev) {
  }

  @Override
  public void dragEnter(Event ev) {
  }

  @Override
  public void dragExit(Event ev) {
  }

  @Override
  public void dragDrop(Event ev) {
  }

  /**
   * When the user types something, this is what they have typed so far. Used for setting the
   * specific length/angle of selections.
   */
  public void setTextInput(String str) {
    this.textInput = str;
    //    display();
    repaint();
  }

  /**
   * Gives the string the user is currently typing. Might be null. And its meaning is
   * context-dependent (e.g. if a single segment is selected, this number is interpreted as a
   * length.).
   */
  public String getTextInput() {
    return textInput;
  }

  public void suspendRedraw(boolean suspendRedraw) {
    this.suspendRedraw = suspendRedraw;
  }

}
