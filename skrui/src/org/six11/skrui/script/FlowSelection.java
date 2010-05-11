package org.six11.skrui.script;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.Timer;

import org.six11.skrui.BoundedParameter;
import org.six11.skrui.DrawingBufferRoutines;
import org.six11.skrui.Main;
import org.six11.skrui.SkruiScript;
import org.six11.skrui.shape.Dot;
import org.six11.skrui.shape.Primitive;
import org.six11.skrui.shape.PrimitiveEvent;
import org.six11.skrui.shape.PrimitiveListener;
import org.six11.skrui.shape.Stroke;
import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.data.FSM;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.SequenceEvent;
import org.six11.util.pen.SequenceListener;
import org.six11.util.pen.Vec;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class FlowSelection extends SkruiScript implements SequenceListener, PrimitiveListener {
  private static final String K_HINGE_THRESHOLD = "fs-hinge-threshold"; // 0.7
  private static final String K_TIMEOUT = "fs-timeout"; // 900
  private static final String K_TICK = "fs-tick"; // 50
  private static final String K_MOVE_THRESHOLD = "fs-move-thresh"; // 10
  public static final double OVERDRAW_NEARNESS_THRESHOLD = 40.0;
  private static final double OVERDRAW_EFFECT_MULTIPLE = 0.5;

  public final static String STR = "fs strength";
  public final static String FSBUF = "fs buffer";
  public final static String HINGE = "hinge";
  public final static String FSCENTER = "flow select center";
  public final static String FSEFFORT = "fs effort";

  Stroke flowSelectionStroke;
  Pt dwellPoint;
  Pt dragPoint; // latest location of pen during a drag.
  Vec dragDelta; // latest change in location during drag.
  List<Stroke> nearestSequences;
  FSM fsm;
  Timer dwellTimer;
  Neanderthal data;
  long fsStartTime;
  List<Dot> taps;
  Timer tapTimer;
  Timer coolTimer;
  private boolean selecting;

  protected void idle() {
    dragPoint = null;
    dragDelta = null;
    dwellPoint = null;
    flowSelectionStroke = null;
    selecting = false;
    drawFlowSelectionEffect();
    coolTimer.restart();
  }

  private void detectOverdraw(Stroke recent) {
    if (!selecting && nearestSequences.size() > 0) {
      // 1. If the recent stroke could conceivably be seen as a dot, forget it.
      Collection<Primitive> prims = (Collection<Primitive>) recent
          .getAttribute(Neanderthal.PRIMITIVES);
      boolean ok = true;
      for (Primitive prim : prims) {
        if (prim instanceof Dot) {
          ok = false;
        }
      }
      if (ok) {
        // 2. If it's not a dot, then see if any part of the drawn stroke comes 'close' to a
        // selected point.
        Sequence overstroke = Functions.getSpline(recent, recent.size() / 4, 1);
        boolean didOverdraw = false;
        List<List<Pt>> patches = extractSelectedPatches();
        for (List<Pt> patch : patches) {
          double dist = Functions.getMinDistBetween(patch, recent.getPoints());
          if (dist < OVERDRAW_NEARNESS_THRESHOLD) {
            handleOverdraw(overstroke, patch, OVERDRAW_EFFECT_MULTIPLE);
            didOverdraw = true;
          }
        }
        if (didOverdraw) {
          data.forget(recent, false);
        }
      }
    }
  }

  /**
   * @param overstroke
   *          is the spline that we're tweening towards
   * @param patch
   *          is the list of points that should move (excluding those already handled)
   */
  private void handleOverdraw(Sequence overstroke, List<Pt> patch, double dampener) {
    List<Pt> tweenTo = Functions.getSequenceTweenTarget(patch, overstroke.getPoints());
    enterReshape();
    for (int i = 0; i < patch.size(); i++) {
      Pt pt = patch.get(i);
      Pt yonder = tweenTo.get(i);
      // if yonder is an endpoint of overstroke, do not move this point. We only want to move
      // points that are 'under' the overstroke.
      if (!yonder.isSameLocation(overstroke.getFirst())
          && !yonder.isSameLocation(overstroke.getLast())) {
        Vec v = new Vec(pt, yonder).getScaled(pt.getDouble(STR) * dampener);
        pt.setLocation(v.add(pt));
      }
    }
    exitReshape();
  }

  /**
   * Extract the selected regions from nearestSequences. Each patch is a sequence of points on the
   * same pen stroke that have positive flow selection strength.
   */
  private List<List<Pt>> extractSelectedPatches() {
    List<List<Pt>> ret = new ArrayList<List<Pt>>();
    for (Stroke seq : nearestSequences) {
      Pt prev = null;
      for (Pt pt : seq) {
        // start patch when moving from an unselected to selected, or if the first point is selected
        if (prev != null && prev.getDouble(STR, 0) == 0 && pt.getDouble(STR, 0) > 0) {
          ret.add(new ArrayList<Pt>());
        } else if (prev == null && pt.getDouble(STR, 0) > 0) {
          ret.add(new ArrayList<Pt>());
        }
        // add pt to current patch if it has positive selection strength
        if (pt.getDouble(STR, 0) > 0) {
          ret.get(ret.size() - 1).add(pt);
        }
        prev = pt;
      }
    }
    return ret;
  }

  protected void exitIdle() {
    coolTimer.stop();
  }

  private void cool() {
    List<Stroke> cooledSequences = new ArrayList<Stroke>();
    if (nearestSequences.size() > 0) {
      for (Stroke str : nearestSequences) {
        boolean stillSelected = false;
        for (Pt pt : str) {
          double selStr = pt.getDouble(STR);
          selStr = Math.max(0, selStr - 0.005);
          pt.setDouble(STR, selStr);
          if (selStr > 0) {
            stillSelected = true;
          }
        }
        if (!stillSelected) {
          cooledSequences.add(str);
        }
      }
      for (Stroke seq : cooledSequences) {
        nearestSequences.remove(seq);
      }
    } else {
      coolTimer.stop(); // nothing more to cool.
    }
    drawFlowSelectionEffect();
  }

  private void drawFlowSelectionEffect() {
    if (nearestSequences.size() > 0) {
      DrawingBuffer db = new DrawingBuffer();
      for (Stroke seq : nearestSequences) {
        DrawingBufferRoutines.flowSelectEffect(db, seq, getThickness(seq));
      }
      main.getDrawnStuff().addNamedBuffer(FSBUF, db);
    } else {
      main.getDrawnStuff().removeNamedBuffer(FSBUF);
    }
  }

  protected void enterReshape() {
    for (Stroke seq : nearestSequences) {
      DrawingBuffer hideMe = seq.getDrawingBuffer();
      if (hideMe != null) {
        hideMe.setVisible(false);
      }
    }
  }

  protected void exitReshape() {
    for (Stroke seq : nearestSequences) {
      DrawingBuffer showMe = seq.getDrawingBuffer();
      if (showMe != null) { // the fact that I must do this tells me there is another bug somewhere.
        showMe.setVisible(true);
        main.updateFinishedSequence(seq);
      }
    }
  }

  protected void reshape() {
    if (dragDelta != null) {
      DrawingBuffer db = new DrawingBuffer();
      // First see if this is a hinged operation.
      Pt hinge = null;
      int numHinges = 0;
      for (Stroke seq : nearestSequences) {
        for (Pt pt : seq) {
          if (pt.getBoolean(HINGE, false)) {
            numHinges++;
            hinge = pt;
          }
        }
      }
      if (numHinges == 1) {
        rotateAndScale(db, hinge);
      } else {
        move(db);
      }
      main.getDrawnStuff().addNamedBuffer(FSBUF, db);
    }
  }

  private void rotateAndScale(DrawingBuffer db, Pt hinge) {
    Pt prev = dragPoint.getTranslated(-dragDelta.getX(), -dragDelta.getY());
    Vec toPrev = new Vec(hinge, prev);
    Vec toDrag = new Vec(hinge, dragPoint);
    double scale = toDrag.mag() / toPrev.mag();
    double theta = Math.atan2(toDrag.getY(), toDrag.getX())
        - Math.atan2(toPrev.getY(), toPrev.getX());
    AffineTransform rot = Functions.getRotationInstance(hinge, theta);
    for (Stroke seq : nearestSequences) {
      for (Pt pt : seq) {
        if (pt != hinge && pt.getDouble(STR, 0) > main.getParam(K_HINGE_THRESHOLD).getDouble()/* HINGE_THRESHOLD */) {
          rot.transform(pt, pt);
          Vec toPt = new Vec(hinge, pt).getScaled(scale);
          pt.setLocation(hinge.getX() + toPt.getX(), hinge.getY() + toPt.getY());
        }
      }
      DrawingBufferRoutines.lines(db, seq.getPoints(), (Color) seq.getAttribute(Main.PEN_COLOR),
          getThickness(seq));
      DrawingBufferRoutines.flowSelectEffect(db, seq, getThickness(seq));
    }
  }

  private void move(DrawingBuffer db) {
    for (Stroke seq : nearestSequences) {
      int centerIdx = seq.getNamedPointIndex(FSCENTER);
      passReshapeMsg(centerIdx, seq, dragDelta.getX(), dragDelta.getY(), 0);
      DrawingBufferRoutines.lines(db, seq.getPoints(), (Color) seq.getAttribute(Main.PEN_COLOR),
          getThickness(seq));
    }
  }

  private double getThickness(Stroke seq) {
    double ret = 2.0;
    if (seq.getAttribute(Main.PEN_THICKNESS) != null) {
      ret = (java.lang.Double) seq.getAttribute(Main.PEN_THICKNESS);
    }
    return ret;
  }

  private void passReshapeMsg(int idx, Stroke seq, double dx, double dy, int dir) {
    Pt pt = seq.get(idx);
    double str = pt.getDouble(STR, 0);
    pt.setLocation(pt.getX() + (str * dx), pt.getY() + (str * dy));
    if (dir == 0) {
      int nextIdx = idx + 1;
      if (nextIdx < seq.size() && nextIdx >= 0) {
        passReshapeMsg(nextIdx, seq, dx, dy, 1);
      }
      nextIdx = idx - 1;
      if (nextIdx < seq.size() && nextIdx >= 0) {
        passReshapeMsg(nextIdx, seq, dx, dy, -1);
      }
    } else {
      int nextIdx = idx + dir;
      if (nextIdx < seq.size() && nextIdx >= 0) {
        passReshapeMsg(nextIdx, seq, dx, dy, dir);
      }
    }
  }

  protected void startSelection() {
    fsStartTime = System.currentTimeMillis();
    selecting = true;
    if (taps.size() != 1) {
      clearFSData(true, true);
    } else {
      clearFSData(false, true);
    }

    for (Dot tap : taps) {
      data.forget(tap, false);
    }
    if (taps.size() < 2) {
      Pt nearestPt = data.getAllPoints().getNearest(dwellPoint);
      prepare(nearestPt);
    } else {
      int nTaps = taps.size();
      Set<Pt> all = data.getAllPoints().getNear(dwellPoint, nTaps * 30);
      Set<Pt> near = new HashSet<Pt>();
      Set<Stroke> sequences = Neanderthal.getSequences(Neanderthal.getPrimitives(all));
      for (Stroke seq : sequences) {
        Pt pt = Functions.getNearestPointOnSequence(dwellPoint, seq);
        near.add(pt);
      }
      for (Pt pt : near) {
        prepare(pt);
      }
    }
    main.setCurrentSequenceShapeVisible(false);
    Stroke scrapMe = (Stroke) dwellPoint.getSequence(Neanderthal.MAIN_SEQUENCE);
    data.forget(scrapMe, false);
  }

  private void clearFSData(boolean resetStrength, boolean clearList) {
    // set the selection strength to zero for all sequences in our list, then clear the list.

    for (Stroke seq : nearestSequences) {
      if (resetStrength) {
        for (Pt pt : seq) {
          pt.setDouble(STR, 0);
        }
      }
      seq.clearNamedPoint(FSCENTER);
    }
    if (clearList) {
      nearestSequences.clear();
    }
  }

  private void prepare(Pt pt) {
    Stroke ns = (Stroke) pt.getSequence(Neanderthal.MAIN_SEQUENCE);
    if (!nearestSequences.contains(ns)) {
      nearestSequences.add(ns);
      int centerIdx = ns.indexOf(pt);
      passEffortMsg(centerIdx, ns, 0, 0);
      // clear the existing center, if any.
      ns.clearNamedPoint(FSCENTER);
      ns.setNamedPoint(FSCENTER, pt);
    }
  }

  /**
   * Sets the FSEFFORT value on each point.
   */
  private void passEffortMsg(int idx, Stroke seq, double effort, int dir) {
    if (indexValid(idx, seq)) {
      Pt pt = seq.get(idx);
      pt.setDouble(FSEFFORT, effort);
      double penalty = getEffortPenalty(idx, seq);
      if (dir == 0) {
        int nextIdx = idx + 1;
        if (indexValid(nextIdx, seq)) {
          Pt next = seq.get(nextIdx);
          double dist = pt.distance(next);
          passEffortMsg(nextIdx, seq, effort + dist + penalty, 1);
        }
        nextIdx = idx - 1;
        if (indexValid(nextIdx, seq)) {
          Pt next = seq.get(nextIdx);
          double dist = pt.distance(next);
          passEffortMsg(nextIdx, seq, effort + dist + penalty, -1);
        }
      } else {
        int nextIdx = idx + dir;
        if (indexValid(nextIdx, seq)) {
          Pt next = seq.get(nextIdx);
          double dist = pt.distance(next);
          passEffortMsg(nextIdx, seq, effort + dist + penalty, dir);
        }
      }
    }
  }

  private double getEffortPenalty(int idx, Stroke seq) {
    double ret = 0;
    // TODO: Figure out another way of passing effort, because this is not very nice.
//    if (idx > 0 && idx < seq.size() - 1 && seq.get(idx).hasAttribute(Neanderthal.CORNER)) {
//      ret = 130;
//    }
    return ret;
  }

  private boolean indexValid(int idx, Stroke seq) {
    return idx >= 0 && idx < seq.size();
  }

  protected void growSelection() {
    long duration = System.currentTimeMillis() - fsStartTime;
    for (Stroke seq : nearestSequences) {
      int centerIdx = seq.getNamedPointIndex(FSCENTER);
      passStrengthMsg(centerIdx, seq, duration, 0);
      assignHinge(seq);
    }

    drawFlowSelectionEffect();
  }

  /**
   * For each point, assign a boolean 'hinge' value. A hinge is a point with a 'corner' attribute
   * that has greater than HINGE_THRESHOLD (e.g. 0.7) selection strength and a point to one side has
   * less than half the threshold.
   * 
   * @return the number of hinges found.
   */
  private int assignHinge(Stroke seq) {
    int numHinges = 0;
    double hingeThresh = main.getParam(K_HINGE_THRESHOLD).getDouble();
    for (int i = 1; i < seq.size() - 1; i++) {
      Pt pt = seq.get(i);
      if (pt.hasAttribute(Neanderthal.CORNER) && pt.getDouble(STR, 0) > hingeThresh) {
        double left = seq.get(i - 1).getDouble(STR, 0);
        double right = seq.get(i + 1).getDouble(STR, 0);
        double min = Math.min(left, right);
        boolean isHinge = min < (hingeThresh / 2);
        if (isHinge) {
          // also ensure that all points to the strongly selected side are selected > 0
          int dir = left > right ? -1 : 1;
          for (int j = i; j < seq.size() && j >= 0; j = j + dir) {
            if (seq.get(j).getDouble(STR, 0) <= 0) {
              isHinge = false;
//              bug("Hingeyness denied.");
              break;
            }
          }
        }
        if (isHinge) {
          pt.setBoolean(HINGE, isHinge);
          if (isHinge) {
            numHinges++;
          }
        }
      } else {
        pt.setBoolean(HINGE, false);
      }
    }
    return numHinges;
  }

  private void passStrengthMsg(int idx, Stroke seq, long duration, int dir) {
    if (indexValid(idx, seq)) {
      Pt pt = seq.get(idx);
      double str = getStrength(pt.getDouble(FSEFFORT), duration, 0.1);
      pt.setDouble(STR, Math.max(str, pt.getDouble(STR, 0)));
      if (dir == 0) {
        passStrengthMsg(idx + 1, seq, duration, 1);
        passStrengthMsg(idx - 1, seq, duration, -1);
      } else {
        passStrengthMsg(idx + dir, seq, duration, dir);
      }
    }
  }

  private double getStrength(double effort, double duration, double saneConstant) {
    double ret = 0;
    double tc = duration * saneConstant;
    if (effort < tc) {
      double trouble = (effort * Math.PI / tc);
      ret = (Math.cos(trouble) + 1) / 2;
    }
    return ret;
  }

  public void sendDwell() {
    fsm.addEvent("dwell");
  }

  public void sendDrag(Pt where) {
    // it is always OK to drag if we have dragged once. If we don't test if dragPoint != null, there
    // is a 'dead zone' around the initial dwell point where dragging does no good, even if we have
    // already moved the selection.
    if (dragPoint != null
        || where.distance(dwellPoint) > main.getParam(K_MOVE_THRESHOLD).getDouble()) {
      if (dragPoint != null) {
        dragDelta = new Vec(dragPoint, where);
      }
      dragPoint = where;
    }
    if (dragPoint != null) {
      fsm.addEvent("drag");
    }
  }

  /**
   * Return true if the user has held the pen still for X milliseconds. "Still" means it has not
   * moved more than Y from the location that it was last known to be X milliseconds ago.
   */
  private boolean detectDwell() {
    boolean ret = false;
    long now = System.currentTimeMillis();
    long target = now - main.getParam(K_TIMEOUT).getInt();
    double curviDist = 0;
    Pt prev = null;
    for (int i = flowSelectionStroke.size() - 1; i >= 0; i--) {
      Pt pt = flowSelectionStroke.get(i);
      if (prev != null) {
        curviDist = curviDist + pt.distance(prev);
      }
      if (pt.getTime() < target) {
        ret = curviDist < main.getParam(K_MOVE_THRESHOLD).getDouble();
        break;
      }
      prev = pt;
    }
    return ret;
  }

  private void setDwellPoint() {
    dwellPoint = flowSelectionStroke.getLast();
    dwellTimer.restart();
  }

  private static void bug(String what) {
    Debug.out("FlowSelection", what);
  }

  @Override
  public void initialize() {
    bug("Flow Selection initialization...");
    flowSelectionStroke = null;
    this.data = (Neanderthal) main.getScript("Neanderthal");
    data.addSequenceListener(this);
    data.addPrimitiveListener(this);
    nearestSequences = new ArrayList<Stroke>();
    int timeout = main.getParam(K_TIMEOUT).getInt();
    dwellTimer = new Timer(timeout, new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (detectDwell()) {
          sendDwell();
        }
      }
    });
    dwellTimer.stop();
    dwellTimer.setRepeats(true);
    dwellTimer.setInitialDelay(timeout);
    dwellTimer.setDelay(main.getParam(K_TICK).getInt());

    taps = new ArrayList<Dot>();
    tapTimer = new Timer(2 * timeout, new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        taps.clear();
      }
    });
    tapTimer.stop();
    tapTimer.setRepeats(false);

    coolTimer = new Timer(3000, new ActionListener() {
      public void actionPerformed(ActionEvent ev) {
        cool();
      }
    });
    coolTimer.stop();
    coolTimer.setInitialDelay(3000);
    coolTimer.setDelay(20);
    coolTimer.setRepeats(true);

    fsm = new FSM("Flow Selection");
    fsm.addState("idle");
    fsm.setStateEntryCode("idle", new Runnable() {
      public void run() {
        idle();
      }
    });
    fsm.setStateExitCode("idle", new Runnable() {
      public void run() {
        exitIdle();
      }
    });
    fsm.addState("draw");
    fsm.addState("select");
    fsm.addState("reshape");
    fsm.setStateEntryCode("reshape", new Runnable() {
      public void run() {
        enterReshape();
      }
    });
    fsm.setStateExitCode("reshape", new Runnable() {
      public void run() {
        exitReshape();
      }
    });
    fsm.addState("smooth");
    fsm.setState("idle", false);

    fsm.addTransition(new FSM.Transition("down", "idle", "draw") {
      @Override
      public void doAfterTransition() {
        setDwellPoint();
      }
    });

    fsm.addTransition(new FSM.Transition("up", "draw", "idle") {
      @Override
      public void doAfterTransition() {
      }
    });

    fsm.addTransition(new FSM.Transition("drag", "draw", "draw") {
      @Override
      public void doAfterTransition() {
      }
    });

    fsm.addTransition(new FSM.Transition("dwell", "draw", "select") {
      @Override
      public void doAfterTransition() {
        startSelection();
      }
    });

    fsm.addTransition(new FSM.Transition("up", "select", "idle") {
      @Override
      public void doAfterTransition() {
      }
    });

    fsm.addTransition(new FSM.Transition("dwell", "select", "select") {
      @Override
      public void doAfterTransition() {
        growSelection();
      }
    });

    fsm.addTransition(new FSM.Transition("drag", "select", "reshape") {
      @Override
      public void doAfterTransition() {
        setDwellPoint();
        // bug("reshaping...");
      }
    });

    fsm.addTransition(new FSM.Transition("up", "reshape", "idle") {
      @Override
      public void doAfterTransition() {
        // bug("up!");
      }
    });

    fsm.addTransition(new FSM.Transition("drag", "reshape", "reshape") {
      @Override
      public void doAfterTransition() {
        reshape();
      }
    });

    fsm.addTransition(new FSM.Transition("dwell", "reshape", "smooth") {
      @Override
      public void doAfterTransition() {
        setDwellPoint();
        smooth();
      }
    });

    fsm.addTransition(new FSM.Transition("up", "smooth", "idle") {
      @Override
      public void doAfterTransition() {
        bug("up!");
      }
    });

    fsm.addTransition(new FSM.Transition("dwell", "smooth", "smooth") {
      @Override
      public void doAfterTransition() {
        smooth();
      }
    });

    fsm.addTransition(new FSM.Transition("drag", "smooth", "reshape") {
      @Override
      public void doAfterTransition() {
        bug("reshaping...");
      }
    });
    bug("Flow Selection initialized.");
  }

  protected void smooth() {
    List<List<Pt>> patches = extractSelectedPatches();
    for (List<Pt> patch : patches) {
      // make a spline based on a downsample of the patch
      Sequence spline = Functions
          .getSplineAuthentic(patch, (int) Math.ceil(patch.size() / 10), 1.0);
      handleOverdraw(spline, patch, 0.05);
    }
    drawFlowSelectionEffect();
  }

  @Override
  public Map<String, BoundedParameter> initializeParameters(Arguments args) {
    // TODO: why is this not just a part of SkruiScript?
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

  public static Arguments getArgumentSpec() {
    Arguments args = new Arguments();
    args.setProgramName("Flow Selection: fuzzily select portions of things.");
    args.setDocumentationProgram("Select portions of things with a gradient selection strength.");

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

    defs.put(K_TIMEOUT, new BoundedParameter.Integer(K_TIMEOUT, "Flow selection dwell timeout",
        "Dwell time (milliseconds) to trigger a flow selection", 900));
    defs.put(K_TICK, new BoundedParameter.Integer(K_TICK, "Flow selection growth tick",
        "Time (milliseconds) between growth ticks while selecting", 50));

    defs.put(K_MOVE_THRESHOLD, new BoundedParameter.Double(K_MOVE_THRESHOLD,
        "Dwell movement threshold",
        "Maximum distance the pen may move while being considered a dwell", 1.0, 16.0, 10.0));
    defs.put(K_HINGE_THRESHOLD, new BoundedParameter.Double(K_HINGE_THRESHOLD,
        "Hinge selection threshold",
        "Required selection strength for a corner to be considered a hinge. The neighboring "
            + "point must have less than half this value for a corner to count as a hinge.", 0.5,
        1.0, 0.7));
    return defs;
  }

  public void handleSequenceEvent(SequenceEvent seqEvent) {
    // we receive this after the Neanderthal has processed it. Other registered sequence listeners
    // might have already handled this particular event, and others might process it after we are
    // done. Be aware.
    SequenceEvent.Type type = seqEvent.getType();
    Stroke seq = (Stroke) seqEvent.getSeq();
    switch (type) {
      case BEGIN:
        flowSelectionStroke = seq;
        fsm.addEvent("down");
        break;
      case PROGRESS:
        sendDrag(seq.getLast());
        break;
      case END:
        dwellTimer.stop();
        detectOverdraw(seq);
        fsm.addEvent("up");
        break;
    }
  }

  public void handlePrimitiveEvent(PrimitiveEvent ev) {
    for (Primitive prim : ev.getPrims()) {
      if (prim instanceof Dot) {
        Dot dot = (Dot) prim;
        taps.add(dot);
        tapTimer.restart();
      }
    }
  }

}
