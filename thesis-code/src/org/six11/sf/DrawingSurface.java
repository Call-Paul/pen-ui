package org.six11.sf;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLContext;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLProfile;
import javax.media.opengl.Threading;
import javax.media.opengl.awt.GLCanvas;
import javax.media.opengl.awt.GLJPanel;
import javax.media.opengl.glu.GLU;
import javax.swing.Timer;

import jogamp.opengl.glu.nurbs.DisplayList;

import org.six11.util.data.FSM;
import org.six11.util.data.Lists;
import org.six11.util.data.FSM.Transition;
import org.six11.util.data.Statistics;
import org.six11.util.pen.Functions;
import org.six11.util.pen.PenEvent;
import org.six11.util.pen.PenListener;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.Vec;

import com.jogamp.opengl.util.FPSAnimator;
import com.jogamp.opengl.util.awt.TextRenderer;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

public class DrawingSurface extends GLJPanel implements GLEventListener, PenListener {

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

  // events
  private static final String MOVE = "move";
  private static final String PAUSE = "pause";
  private static final String UP = "up";
  private static final String DOWN = "down";
  private static final String TICK = "tick";
  private static final String BUTTON_DOWN = "magic_button_down";
  private static final String BUTTON_UP = "magic_button_up";
  private static final String ARMED = "armed";
  private static final String SEARCH_DIR = "search_direction";
  private static final String DRAG_BEGIN = "drag_begin";

  public final static Color DEFAULT_DRY_COLOR = Color.GRAY.darker();
  public final static float DEFAULT_DRY_THICKNESS = 1.4f;

  protected static final int UNDO_REDO_THRESHOLD = 40;
  private static final Color PAUSE_COLOR = Color.YELLOW.darker();
  private static final double FS_SMOOTH_DAMPING = 0.025;

  private TextRenderer textRenderer18;

  protected GLU glu;

  // pen listeners
  private List<PenListener> penListeners;

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

  // recent pen activity vars
  private Pt hoverPt;
  private Pt prev;
  private List<Pt> currentScribble;

  // the model.
  protected SketchBook model;

  // the thing that knows how to render the model
  protected SketchRenderer renderer;
  private Map<Integer, TextRenderer> textRenderers;

  public DrawingSurface(SketchBook model) {
    super(new GLCapabilities(GLProfile.getDefault()));
    this.model = model;
    this.renderer = new SketchRenderer();
    this.penLatency = new Statistics();
    penLatency.setMaximumN(40);
    addGLEventListener(this);
    //    FPSAnimator animator = new FPSAnimator(this, 60);
    //    animator.add(this);
    //    animator.start();
    setName("DrawingSurface");
    penListeners = new ArrayList<PenListener>();
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
  public void display(GLAutoDrawable drawable) {
    long start = System.currentTimeMillis();
    GL2 gl = drawable.getGL().getGL2();
    Dimension size = getSize();

    if (previewSnapshot != null) {
      gl.glCallList(previewSnapshot.getDisplayListID());
    } else {
      // if we have been requested to make a snapshot, save this round to a display list.
      int displayList = 0;
      if (requestSnapshot) {
        Snapshot snap = model.getSnapshotMachine().save();
        if (snap != null) {
          requestSnapshot = false;
          displayList = gl.glGenLists(1);
          snap.setDisplayListID(displayList);
          gl.glNewList(displayList, GL2.GL_COMPILE_AND_EXECUTE);
        }
      }
      gl.glClear(GL.GL_COLOR_BUFFER_BIT); // clear screen.

      // draw border.

      gl.glColor3fv(SketchRenderer.black, 0);
      float thick = 1.5f;
      float thickHalf = thick / 2f;
      gl.glLineWidth(thick);
      rect(gl, thickHalf, size.width - thick, thickHalf, size.height - thick);

      // render scribble and model data
      renderer.render(model, drawable, currentScribble, true, this);

      if (displayList > 0) {
        gl.glEndList();
      }
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
      
      //TODO: put arrows in here too
    }

    long end = System.currentTimeMillis();
    long dur = end - start;

    // show framerate
    textRenderer18.beginRendering(drawable.getWidth(), drawable.getHeight());
    textRenderer18.setColor(0.4f, 0.4f, 0.4f, 0.4f);
    textRenderer18.draw("Render: " + dur + "ms", size.width - 180, 40);
    textRenderer18.draw("Pen Latency: " + (long) penLatency.getMean() + "ms", size.width - 180, 20);
    textRenderer18.endRendering();
  }

  @Override
  public void dispose(GLAutoDrawable drawable) {
    bug("gl dispose");
  }

  @Override
  public void init(GLAutoDrawable drawable) {
    bug("gl init");
    GL2 gl = drawable.getGL().getGL2();
    glu = new GLU();
    gl = drawable.getGL().getGL2();
    gl.glMatrixMode(GL2.GL_PROJECTION);
    gl.glLoadIdentity();
    gl.glOrtho(-1, 1, 1, -1, 0, 1);
    gl.glMatrixMode(GL2.GL_MODELVIEW);
    gl.glClearColor(1f, 1f, 1f, 1f);
    gl.glDisable(GL2.GL_DEPTH_TEST);
    gl.glEnable(GL2.GL_BLEND);
    gl.glEnable(GL2.GL_LINE_SMOOTH);
    gl.glHint(GL2.GL_LINE_SMOOTH_HINT, GL2.GL_NICEST);
    gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);

    renderer.init(drawable);
    textRenderer18 = new TextRenderer(new Font("SansSerif", Font.BOLD, 18));
    textRenderer18.setSmoothing(true);
    textRenderers.put(18, textRenderer18);

    TextRenderer textRenderer12 = new TextRenderer(new Font("SansSerif", Font.PLAIN, 12));
    textRenderer12.setSmoothing(true);
    textRenderers.put(12, textRenderer12);

  }

  @Override
  public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
    GL2 gl = drawable.getGL().getGL2();
    gl.glMatrixMode(GL2.GL_PROJECTION);
    gl.glLoadIdentity();
    gl.glOrtho(x, x + width, y + height, y, 0, 1);
    gl.glMatrixMode(GL2.GL_MODELVIEW);
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
      }
    });
    FSM f = new FSM("Flow Selection FSM");
    f.addState(IDLE);
    f.addState(DRAW);
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
        model.getEditor().drawStuff();
        fsTransitionPt = null;
        fsRecent.clear();
      }
    });
    f.setStateEntryCode(FLOW, new Runnable() {
      public void run() {
        fsTickTimer.restart();
      }
    });
    //    f.setStateExitCode(OP, new Runnable() {
    //      public void run() {
    ////        fsSaveChanges();
    //      }
    //    });

    f.setStateEntryCode(SEARCH_DIR, new Runnable() {
      public void run() {
        searchStart = fsDown.copyXYT();
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
        if (searchStart != null && dragPt != null) {
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
    //      public void stateChanged(ChangeEvent arg0) {
    //        bug("state: " + fsFSM.getState());
    //      }
    //    });
    //    f.setDebugMode(true);
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

  protected void fsSmooth() {

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
    model.getEditor().drawStuff();

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
    if (now > then + fsPauseTimeout) {
      // pause is possible. take the approach of assuming there 
      // is a pause but using fsRecent to disprove it.
      paused = true;
      double maxD = 0;
      for (int i = fsRecent.size() - 1; i >= 0; i--) {
        Pt pt = fsRecent.get(i);
        maxD = Math.max(maxD, pt.distance(fsTransitionPt));
        if (maxD > fsBubble) {
          paused = false;
          break;
        }
      }
      if (paused) {
        fsFSM.addEvent(PAUSE);
      }
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
    model.getEditor().drawStuff();
  }

  private void fsDeform(Pt recent) {
    if (fsLastDeformPt != null && fsNearestSeg != null) {
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
    model.getEditor().drawStuff();
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
    return ((double) elapsed * p) / 1000.0;
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
    int viewport[] = new int[4];
    float mvmatrix[] = new float[16];
    float projmatrix[] = new float[16];
    int realy = 0;// GL y coord pos
    float wcoord[] = new float[4];// wx, wy, wz
    //    GL2 gl = getGL().getGL2();
    gl.glGetIntegerv(GL.GL_VIEWPORT, viewport, 0);
    gl.glGetFloatv(GL2.GL_MODELVIEW_MATRIX, mvmatrix, 0);
    gl.glGetFloatv(GL2.GL_PROJECTION_MATRIX, projmatrix, 0);
    realy = viewport[3] - (int) y - 1; // have to invert y values because Java and OpenGL disagree which way is up.
    glu.gluUnProject((float) x, (float) realy, 0.0f, //
        mvmatrix, 0, projmatrix, 0, viewport, 0, wcoord, 0);
    return wcoord;
  }

  protected Pt mkPt(float[] coords, long time) {
    Pt ret = new Pt(coords[0], coords[1], time);
    return ret;
  }

  protected void rect(GL2 gl, float minX, float width, float minY, float height) {
    gl.glBegin(GL2.GL_LINE_LOOP);
    {
      gl.glVertex2f(minX, minY);
      gl.glVertex2f(minX, minY + height);
      gl.glVertex2f(minX + width, minY + height);
      gl.glVertex2f(minX + width, minY);
    }
    gl.glEnd();

  }

  @Override
  public void handlePenEvent(PenEvent ev) {
    long dur = ev.getTimestamp() - lastPenTime;
    lastPenTime = ev.getTimestamp();
    if (dur < 1000) {
      penLatency.addData(dur);
    }

    final Pt world = ev.getPt();
    if (world == null) {

    } else {
      GLContext context = getContext();
      if (context != null) {
        int current = context.makeCurrent();
        if (current != GLContext.CONTEXT_NOT_CURRENT) {
          GL2 gl = context.getGL().getGL2();
          float[] coords = unproject(gl, world.ix(), world.iy());
          fsRecentPt = mkPt(coords, world.getTime());
        } else {
          bug("Could not make open gl context current. Pen Event will be ignored.");
        }
        context.release();
      }
    }

    switch (ev.getType()) {
      case Down:
        fsFSM.addEvent(DOWN);
        fsDown = ev.getPt();
        if (fsFSM.getState().equals(DRAW)) {
          model.retainVisibleGuides();
          hoverPt = null;
          if (model.isPointOverSelection(ev.getPt())) {
            fsFSM.addEvent(DRAG_BEGIN);
            model.setDraggingSelection(true);
          } else {
            Collection<GuidePoint> nearbyGuidePoints = model.findGuidePoints(ev.getPt(), true);
            if (nearbyGuidePoints.isEmpty()) {
              prev = ev.getPt();
              currentScribble = new ArrayList<Pt>();
              currentScribble.add(prev);
            } else {
              GuidePoint nearestGP = null;
              double nearestDist = Double.MAX_VALUE;
              for (GuidePoint gpt : nearbyGuidePoints) {
                double d = gpt.getLocation().distance(ev.getPt());
                if (d < nearestDist) {
                  nearestDist = d;
                  nearestGP = gpt;
                }
              }
              model.setDraggingGuidePoint(nearestGP);
            }
            model.startScribble(ev.getPt());
            model.clearSelectedStencils();
          }
        }
        break;
      case Drag:
        fsFSM.addEvent(MOVE);
        dragPt = ev.getPt();
        hoverPt = null;
        Pt here = ev.getPt();
        if (model.isDraggingSelection()) {
          bug("You should never see this! Shouldn't send layers drag events when drag selection is true");
        } else if (model.isDraggingGuide()) {
          model.dragGuidePoint(here);
        } else if (fsFSM.getState().equals(OP)) {

        } else if (fsFSM.getState().equals(DRAW) || fsFSM.getState().equals(FLOW)) {
          if (currentScribble != null) {
            currentScribble.add(here);
            //            currentScribble.lineTo(here.getX(), here.getY());
          }
          model.addScribble(ev.getPt());
        }
        break;
      case Idle:
        boolean wasDrawing = fsFSM.getState().equals(DRAW);
        fsFSM.addEvent(UP);
        if (model.isDraggingGuide()) {
          model.setDraggingGuidePoint(null);
        } else {
          if (wasDrawing) {
            Sequence seq = model.endScribble(ev.getPt());
            if (seq != null) {
              SafeAction a = model.getActionFactory().addInk(new Ink(seq));
              model.addAction(a);
            }
          }
          clearScribble();
        }
        break;
      case Enter:
        break;
      case Exit:
        hoverPt = null;
        break;
      case Hover:
        hoverPt = ev.getPt().copyXYT();
        break;
    }
    display();
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
    bug("Please show snapshot " + previewSnapshot.getID() + " (display list "
        + previewSnapshot.getDisplayListID());
    display();
  }

  public void clearPreview() {
    previewSnapshot = null;
    bug("Stop showing preview.");
    display();
  }

  public void snapshot() {
    requestSnapshot = true;
  }
}
