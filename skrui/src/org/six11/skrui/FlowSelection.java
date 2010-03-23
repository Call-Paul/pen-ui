package org.six11.skrui;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Timer;

import org.six11.skrui.script.Neanderthal;
import org.six11.util.Debug;
import org.six11.util.data.FSM;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.Vec;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class FlowSelection {
  Sequence flowSelectionStroke;
  Pt dwellPoint;
  Pt dragPoint; // latest location of pen during a drag.
  Vec dragDelta; // latest change in location during drag.
  Pt nearestPt;
  List<Sequence> nearestSequences;
  FSM fsm;
  Timer dwellTimer;
  Neanderthal data;
  long fsStartTime;
  private static int TIMEOUT = 900;
  private static int TICK = 50;
  private static double MOVE_THRESHOLD = 10.0;

  // private static double EFFORT_INCREMENT = 0.3;

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
    nearestPt = null;
    flowSelectionStroke = null;
  }

  protected void enterReshape() {
    for (Sequence seq : nearestSequences) {
      DrawingBuffer hideMe = data.getDrawingSurface().getSoup().getDrawingBufferForSequence(seq);
      hideMe.setVisible(false);
    }
  }

  protected void exitReshape() {
    for (Sequence seq : nearestSequences) {
      DrawingBuffer showMe = data.getDrawingSurface().getSoup().getDrawingBufferForSequence(seq);
      showMe.setVisible(true);
      data.getDrawingSurface().getSoup().updateFinishedSequence(seq);
    }
    data.getDrawingSurface().getSoup().removeBuffer("fs buffer");
  }

  protected void reshape() {
    if (dragDelta != null) {
      DrawingBuffer db = new DrawingBuffer();
      for (Sequence seq : nearestSequences) {
        int centerIdx = seq.getNamedPointIndex("flow select center");
        passReshapeMsg(centerIdx, seq, dragDelta.getX(), dragDelta.getY(), 0);
        DrawingBufferRoutines.lines(db, seq.getPoints(), Color.BLACK, 1.0);
        DrawingBufferRoutines.flowSelectEffect(db, seq, 4.0);
      }
      data.getDrawingSurface().getSoup().addBuffer("fs buffer", db);
    }
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
    nearestPt = data.getAllPoints().getNearest(dwellPoint);
    nearestSequences.clear();
    Sequence ns = nearestPt.getSequence(Neanderthal.MAIN_SEQUENCE);
    nearestSequences.add(ns);
    data.getDrawingSurface().getSoup().setCurrentSequenceShapeVisible(false);
    dwellPoint.getSequence(Neanderthal.MAIN_SEQUENCE).setAttribute(Neanderthal.SCRAP, "true");
    bug("just set the scrap attribute on the current scribble, and set it to invisible.");
    ns.setNamedPoint("flow select center", nearestPt);
  }

  protected void growSelection() {
    DrawingBuffer db = new DrawingBuffer();
    for (Sequence seq : nearestSequences) {
      int centerIdx = seq.getNamedPointIndex("flow select center");
      double maxHeat = 300;
      passHeatMsg(centerIdx, seq, 20, maxHeat, 0);
      DrawingBufferRoutines.flowSelectEffect(db, seq, 4.0);
    }
    data.getDrawingSurface().getSoup().addBuffer("fs buffer", db);
  }

  private void passHeatMsg(int idx, Sequence seq, double xfer, double maxHeat, int dir) {
    Pt pt = seq.get(idx);
    double currentHeat = pt.getDouble("heat", 0);
    double headroom = maxHeat - currentHeat; // this is how much hotter I can get.
    double amt = Math.min(headroom / 3, xfer / 3);
    double newHeat = Math.min(maxHeat, currentHeat + amt);
    pt.setDouble("heat", newHeat);
    double str = getStrength(newHeat, maxHeat);
    seq.get(idx).setDouble("fs strength", str);
    double remainingHeat = xfer - amt;
    if (dir == 0) {
      double what = 3 / 2;
      // This is the selection center. Go both directions.
      int nextIdx = idx + 1;
      if (nextIdx >= 0 && nextIdx < seq.size()) {
        passHeatMsg(nextIdx, seq, remainingHeat * what, maxHeat, 1);
      }
      nextIdx = idx - 1;
      if (nextIdx >= 0 && nextIdx < seq.size()) {
        passHeatMsg(nextIdx, seq, remainingHeat * what, maxHeat, -1);
      }
    } else {
      // Not the center. Just keep going the direction you're on.
      int nextIdx = idx + dir;
      if (nextIdx >= 0 && nextIdx < seq.size()) {
        passHeatMsg(nextIdx, seq, remainingHeat, maxHeat, dir);
      }
    }
  }

  // private void passStrengthMsg(int idx, Sequence seq, double str, int dir) {
  // // double effort = seq.get(idx).getDouble("fs effort");
  // seq.get(idx).setDouble("fs strength", str);
  // int nextIdx = idx + dir;
  // if (str > 0 && nextIdx >= 0 && nextIdx < seq.size()) {
  // double nextStr = str * str;
  // passStrengthMsg(nextIdx, seq, nextStr, dir);
  // }
  // }

  // private void passEffortMsg(int idx, Sequence seq, double e, int dir) {
  // seq.get(idx).setDouble("fs effort", e);
  // boolean isCorner = idx > 0 && idx < (seq.size() - 1) && seq.get(idx).hasAttribute("corner");
  // bug("effort " + idx + "/" + (seq.size() - 1) + " = " + Debug.num(e)
  // + (isCorner ? " (corner)" : ""));
  // int nextIdx = idx + dir;
  // if (nextIdx < seq.size() && nextIdx >= 0) {
  // double dist = seq.get(idx).distance(seq.get(nextIdx));
  // if (isCorner) {
  // dist = dist + 1400;
  // }
  // passEffortMsg(nextIdx, seq, e + dist, dir);
  // }
  // }

  private double getStrength(double heat, double maxHeat) {
    double ratioSqrt = Math.sqrt(heat / maxHeat);
    double num = Math.cos(ratioSqrt * Math.PI) + 1;
    return 1 - (num / 2);
  }

  public void sendDwell() {
    fsm.addEvent("dwell");
  }

  public void sendDrag(Pt where) {
    if (dragPoint != null) {
      dragDelta = new Vec(dragPoint, where);
    }
    dragPoint = where;
    fsm.addEvent("drag");
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
    // bug("detectDwell says you moved " + Debug.num(curviDist) + ". Returning: " + ret);
    return ret;
  }

  private void setDwellPoint() {
    dwellPoint = flowSelectionStroke.getLast();
    dwellTimer.restart();
    bug("Set dwell point to: " + Debug.num(dwellPoint) + " and restarted timer.");
  }

  private static void bug(String what) {
    Debug.out("FlowSelection", what);
  }
}
