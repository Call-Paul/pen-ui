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

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class FlowSelection {
  Sequence flowSelectionStroke;
  Pt dwellPoint;
  Pt nearestPt;
  List<Sequence> nearestSequences;
  FSM fsm;
  Timer dwellTimer;
  Neanderthal data;
  long fsStartTime;
  private static int TIMEOUT = 900;
  private static int TICK = 50;
  private static double MOVE_THRESHOLD = 10.0;
  private static double EFFORT_INCREMENT = 0.3;

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
    fsm.addState("draw");
    fsm.addState("select");
    fsm.addState("reshape");
    fsm.addState("smooth");
    fsm.setState("idle", false);

    fsm.addTransition(new FSM.Transition("down", "idle", "draw") {
      @Override
      public void doAfterTransition() {
        setDwellPoint();
        // bug("drawing...");
      }
    });

    fsm.addTransition(new FSM.Transition("up", "draw", "idle") {
      @Override
      public void doAfterTransition() {
        // bug("up!");
      }
    });

    fsm.addTransition(new FSM.Transition("drag", "draw", "draw") {
      @Override
      public void doAfterTransition() {
        // bug("drawing some more...");
      }
    });

    fsm.addTransition(new FSM.Transition("dwell", "draw", "select") {
      @Override
      public void doAfterTransition() {
        bug("selecting...");
        startSelection();
      }
    });

    fsm.addTransition(new FSM.Transition("up", "select", "idle") {
      @Override
      public void doAfterTransition() {
        bug("up!");
      }
    });

    fsm.addTransition(new FSM.Transition("dwell", "select", "select") {
      @Override
      public void doAfterTransition() {
        bug("selecting some more...");
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
        bug("reshaping some more...");
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
    DrawingBuffer db = data.getDrawingSurface().getSoup().getBuffer("fs");
    if (db == null) {
      db = new DrawingBuffer();
      data.getDrawingSurface().getSoup().addBuffer("fs", db);
    }
    DrawingBufferRoutines.dot(db, nearestPt, 6.0, 1.0, Color.black, Color.RED);
    data.getDrawingSurface().repaint();
  }

  protected void growSelection() {
    long duration = System.currentTimeMillis() - fsStartTime;
    DrawingBuffer db = new DrawingBuffer();
    for (Sequence seq : nearestSequences) {
      int centerIdx = seq.getNamedPointIndex("flow select center");
      double distToCenter = 0;
      for (int i = centerIdx; i < seq.size(); i++) {
        distToCenter = seq.getPathLength(centerIdx, i);
        double str = getStrength(distToCenter, duration);
        seq.get(i).setDouble("fs strength", str);
        if (str == 0) {
          break;
        }
      }
      distToCenter = 0;
      for (int i = centerIdx; i >= 0; i--) {
        distToCenter = seq.getPathLength(i, centerIdx);
        double str = getStrength(distToCenter, duration);
        seq.get(i).setDouble("fs strength", str);
        if (str == 0) {
          break;
        }
      }
      DrawingBufferRoutines.flowSelectEffect(db, seq, 4.0);
    }
    data.getDrawingSurface().getSoup().addBuffer("fs buffer", db);
  }

  private double getStrength(double effort, long duration) {
    double ret = 0;
    double denom = duration * EFFORT_INCREMENT;
    if (denom > effort) {
      double num = (Math.cos(effort * Math.PI / denom)) + 1;
      ret = num / 2;
    }
//    bug("Strength at effort: " + Debug.num(effort) + ", duration: " + duration + " = "
//        + Debug.num(ret));
    return ret;
  }

  public void sendDwell() {
    fsm.addEvent("dwell");
  }

  public void sendDrag() {
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
