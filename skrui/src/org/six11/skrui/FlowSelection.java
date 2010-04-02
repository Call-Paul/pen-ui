package org.six11.skrui;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.Timer;

import org.six11.skrui.script.Dot;
import org.six11.skrui.script.Neanderthal;
import org.six11.util.Debug;
import org.six11.util.data.FSM;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.Vec;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class FlowSelection {
  private static final double HINGE_THRESHOLD = 0.7;
  Sequence flowSelectionStroke;
  Pt dwellPoint;
  Pt dragPoint; // latest location of pen during a drag.
  Vec dragDelta; // latest change in location during drag.
  List<Sequence> nearestSequences;
  FSM fsm;
  Timer dwellTimer;
  Neanderthal data;
  long fsStartTime;
  List<Dot> taps;
  Timer tapTimer;

  private static int TIMEOUT = 900;
  private static int TICK = 50;
  private static double MOVE_THRESHOLD = 10.0;

  public FlowSelection(final Neanderthal data) {
    flowSelectionStroke = null;
    this.data = data;
    nearestSequences = new ArrayList<Sequence>();
    dwellTimer = new Timer(TIMEOUT, new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (detectDwell()) {
          sendDwell();
        }
      }
    });
    dwellTimer.stop();
    dwellTimer.setRepeats(true);
    dwellTimer.setInitialDelay(TIMEOUT);
    dwellTimer.setDelay(TICK);

    taps = new ArrayList<Dot>();
    tapTimer = new Timer(2 * TIMEOUT, new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        bug("No more taps. I had: " + taps.size());
        taps.clear();
      }
    });
    tapTimer.stop();
    tapTimer.setRepeats(false);

    fsm = new FSM("Flow Selection");
    fsm.addState("idle");
    fsm.setStateEntryCode("idle", new Runnable() {
      public void run() {
        idle();
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
        bug("reshaping...");
      }
    });

    fsm.addTransition(new FSM.Transition("up", "reshape", "idle") {
      @Override
      public void doAfterTransition() {
        bug("up!");
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
        bug("smoothing...");
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
        bug("smoothing...");
      }
    });

    fsm.addTransition(new FSM.Transition("drag", "smooth", "reshape") {
      @Override
      public void doAfterTransition() {
        bug("reshaping...");
      }
    });
  }

  protected void idle() {
    dragPoint = null;
    dragDelta = null;
    flowSelectionStroke = null;
  }

  protected void enterReshape() {
    for (Sequence seq : nearestSequences) {
      DrawingBuffer hideMe = data.getDrawingSurface().getSoup().getDrawingBufferForSequence(seq);
      if (hideMe != null) {
        hideMe.setVisible(false);
      }
    }
  }

  protected void exitReshape() {
    for (Sequence seq : nearestSequences) {
      DrawingBuffer showMe = data.getDrawingSurface().getSoup().getDrawingBufferForSequence(seq);
      if (showMe != null) { // the fact that I must do this tells me there is another bug somewhere.
        showMe.setVisible(true);
        data.getDrawingSurface().getSoup().updateFinishedSequence(seq);
      }
    }
    data.getDrawingSurface().getSoup().removeBuffer("fs buffer");
  }

  protected void reshape() {
    if (dragDelta != null) {
      DrawingBuffer db = new DrawingBuffer();
      // First see if this is a hinged operation.
      Pt hinge = null;
      int numHinges = 0;
      for (Sequence seq : nearestSequences) {
        for (Pt pt : seq) {
          if (pt.getBoolean("hinge", false)) {
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
      data.getDrawingSurface().getSoup().addBuffer("fs buffer", db);
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
    for (Sequence seq : nearestSequences) {
      for (Pt pt : seq) {
        if (pt != hinge && pt.getDouble("fs strength", 0) > HINGE_THRESHOLD) {
          rot.transform(pt, pt);
          Vec toPt = new Vec(hinge, pt).getScaled(scale);
          pt.setLocation(hinge.getX() + toPt.getX(), hinge.getY() + toPt.getY());
        }
      }
      DrawingBufferRoutines.lines(db, seq.getPoints(), Color.BLACK, getThickness(seq));
      DrawingBufferRoutines.flowSelectEffect(db, seq, getThickness(seq));
    }
  }

  private void move(DrawingBuffer db) {
    for (Sequence seq : nearestSequences) {
      int centerIdx = seq.getNamedPointIndex("flow select center");
      passReshapeMsg(centerIdx, seq, dragDelta.getX(), dragDelta.getY(), 0);
      DrawingBufferRoutines.lines(db, seq.getPoints(), Color.BLACK, getThickness(seq));
      DrawingBufferRoutines.flowSelectEffect(db, seq, getThickness(seq));
    }
  }

  private double getThickness(Sequence seq) {
    double ret = 2.0;
    if (seq.getAttribute("pen thickness") != null) {
      ret = (Double) seq.getAttribute("pen thickness");
    }
    return ret;
  }

  private void passReshapeMsg(int idx, Sequence seq, double dx, double dy, int dir) {
    Pt pt = seq.get(idx);
    double str = pt.getDouble("fs strength", 0);
    if (str > 0) {
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
  }

  protected void startSelection() {
    fsStartTime = System.currentTimeMillis();
    nearestSequences.clear();
    bug("Selecting with " + taps.size() + " preceding pen taps.");

    if (taps.size() == 0) {
      bug("single-select mode.");
      Pt nearestPt = data.getAllPoints().getNearest(dwellPoint);
      prepare(nearestPt);
    } else {
      bug("multi-select mode!");
      int nTaps = taps.size();
      for (Dot tap : taps) {
        data.forget(tap);
      }
      bug("Selecting many sequences in the neighborhood");
      Set<Pt> all = data.getAllPoints().getNear(dwellPoint, nTaps * 30);
      bug("Found " + all.size() + " points...");
      Set<Pt> near = new HashSet<Pt>();
      Set<Sequence> sequences = Neanderthal.getSequences(Neanderthal.getPrimitives(all));
      bug("... which are part of " + sequences.size() + " total sequences.");
      for (Sequence seq : sequences) {
        Pt pt = Functions.getNearestPoint(dwellPoint, seq);
        near.add(pt);
      }
      bug("Using " + near.size() + " sequence centers.");
      for (Pt pt : near) {
        prepare(pt);
      }
    }
    data.getDrawingSurface().getSoup().setCurrentSequenceShapeVisible(false);
    dwellPoint.getSequence(Neanderthal.MAIN_SEQUENCE).setAttribute(Neanderthal.SCRAP, "true");
    bug("just set the scrap attribute on the current scribble, and set it to invisible.");
  }

  private void prepare(Pt pt) {
    Sequence ns = pt.getSequence(Neanderthal.MAIN_SEQUENCE);
    if (!nearestSequences.contains(ns)) {
      nearestSequences.add(ns);
      int centerIdx = ns.indexOf(pt);
      passEffortMsg(centerIdx, ns, 0, 0);
      ns.setNamedPoint("flow select center", pt);
    }
  }

  /**
   * Sets the "fs effort" value on each point.
   */
  private void passEffortMsg(int idx, Sequence seq, double effort, int dir) {
    if (indexValid(idx, seq)) {
      Pt pt = seq.get(idx);
      pt.setDouble("fs effort", effort);
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

  private double getEffortPenalty(int idx, Sequence seq) {
    double ret = 0;
    if (idx > 0 && idx < seq.size() - 1 && seq.get(idx).hasAttribute("corner")) {
      ret = 130;
    }
    return ret;
  }

  private boolean indexValid(int idx, Sequence seq) {
    return idx >= 0 && idx < seq.size();
  }

  protected void growSelection() {
    DrawingBuffer db = new DrawingBuffer();
    long duration = System.currentTimeMillis() - fsStartTime;
    for (Sequence seq : nearestSequences) {
      int centerIdx = seq.getNamedPointIndex("flow select center");
      passStrengthMsg(centerIdx, seq, duration, 0);
      assignHinge(seq);
      DrawingBufferRoutines.flowSelectEffect(db, seq, getThickness(seq));
    }
    if (nearestSequences.size() > 0) {
      data.getDrawingSurface().getSoup().addBuffer("fs buffer", db);
    } else {
      data.getDrawingSurface().getSoup().removeBuffer("fs buffer");
    }
  }

  /**
   * For each point, assign a boolean 'hinge' value. A hinge is a point with a 'corner' attribute
   * that has greater than HINGE_THRESHOLD (e.g. 0.7) selection strength and a point to one side has
   * less than half the threshold.
   * 
   * @return the number of hinges found.
   */
  private int assignHinge(Sequence seq) {
    int numHinges = 0;
    for (int i = 1; i < seq.size() - 1; i++) {
      Pt pt = seq.get(i);
      if (pt.hasAttribute("corner") && pt.getDouble("fs strength", 0) > HINGE_THRESHOLD) {
        double left = seq.get(i - 1).getDouble("fs strength", 0);
        double right = seq.get(i + 1).getDouble("fs strength", 0);
        double min = Math.min(left, right);
        pt.setBoolean("hinge", (min < HINGE_THRESHOLD / 2));
        if (min < HINGE_THRESHOLD / 2) {
          numHinges++;
        }
      } else {
        pt.setBoolean("hinge", false);
      }
    }
    return numHinges;
  }

  /**
   * Sets the "fs strength" double on points that have positive strength. Other points will not get
   * a strength value, so remember that.
   */
  private void passStrengthMsg(int idx, Sequence seq, long duration, int dir) {
    if (indexValid(idx, seq)) {
      Pt pt = seq.get(idx);
      double str = getStrength(pt.getDouble("fs effort"), duration, 0.1);
      pt.setDouble("fs strength", str);
      if (str > 0) {
        if (dir == 0) {
          passStrengthMsg(idx + 1, seq, duration, 1);
          passStrengthMsg(idx - 1, seq, duration, -1);
        } else {
          passStrengthMsg(idx + dir, seq, duration, dir);
        }
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
    if (where.distance(dwellPoint) > MOVE_THRESHOLD) {
      if (dragPoint != null) {
        dragDelta = new Vec(dragPoint, where);
      }
      dragPoint = where;
      fsm.addEvent("drag");
    }
  }

  public void sendUp() {
    dwellTimer.stop();
    fsm.addEvent("up");
  }

  public void sendDown(Sequence seq) {
    flowSelectionStroke = seq;
    fsm.addEvent("down");
  }

  /**
   * Return true if the user has held the pen still for X milliseconds. "Still" means it has not
   * moved more than Y from the location that it was last known to be X milliseconds ago.
   */
  private boolean detectDwell() {
    boolean ret = false;
    long now = System.currentTimeMillis();
    long target = now - TIMEOUT;
    double curviDist = 0;
    Pt prev = null;
    for (int i = flowSelectionStroke.size() - 1; i >= 0; i--) {
      Pt pt = flowSelectionStroke.get(i);
      if (prev != null) {
        curviDist = curviDist + pt.distance(prev);
      }
      if (pt.getTime() < target) {
        ret = curviDist < MOVE_THRESHOLD;
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

  public void sendTap(Dot dot) {
    taps.add(dot);
    bug("tap! Now I have " + taps.size());
    tapTimer.restart();
  }

  private static void bug(String what) {
    Debug.out("FlowSelection", what);
  }
}
