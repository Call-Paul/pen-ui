package org.six11.skrui.script;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import org.six11.skrui.script.Neanderthal.Certainty;
import org.six11.skrui.shape.LineSegment;
import org.six11.skrui.shape.Primitive;
import org.six11.util.Debug;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.skrui.DrawingBufferRoutines;
import org.six11.skrui.Main;

/**
 * Assorted debugging functions that aren't strictly needed for other classes to work. This might be
 * something of a code graveyard, but at least it is all in one spot.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class DebugUtil {

  Main main;

  DebugUtil(Main main) {
    this.main = main;
  }

  void output(String... stuff) {
    boolean first = true;
    for (String v : stuff) {
      if (!first) {
        System.out.print(", ");
      }
      first = false;
      System.out.print(v);
    }
    System.out.print("\n");
  }

  void output(double... stuff) {
    boolean first = true;
    for (double v : stuff) {
      if (!first) {
        System.out.print(", ");
      }
      first = false;
      System.out.print(Debug.num(v));
    }
    System.out.print("\n");
  }

  int sumCertainty(Certainty... certainties) {
    int sum = 0;
    for (Certainty c : certainties) {
      switch (c) {
        case Maybe:
        case Yes:
          sum += 1;
      }
    }
    return sum;
  }

  void drawDots(Sequence seq, String booleanPropertyName, String drawingBufferName, Color color,
      double radius) {
    if (seq.size() > 1) {
      DrawingBuffer db = main.getBuffer(drawingBufferName);
      if (db == null) {
        db = new DrawingBuffer();
        db.setComplainWhenDrawingToInvisibleBuffer(false);
        main.addBuffer(drawingBufferName, db);
        try {
          int num = Integer.parseInt(drawingBufferName);
          if (num >= 0 && num <= 9) {
            db.setVisible(false);
            bug("Created debug buffer '" + drawingBufferName
                + "' but it is currently invisible. Press its button to show it.");
          }
        } catch (NumberFormatException ex) {
          db.setVisible(true);
        }
      }
      db.setEmptyOK(true);
      List<Pt> suspects = new ArrayList<Pt>();
      for (Pt pt : seq) {
        if (pt.getBoolean(booleanPropertyName, false)) {
          suspects.add(pt);
        }
      }
      if (suspects.size() > 0) {
        DrawingBufferRoutines.dots(db, suspects, radius, 0.3, Color.BLACK, color);
      }
    }
  }

  private static void bug(String what) {
    Debug.out("DebugUtil", what);
  }

  void drawDots(List<Pt> spots, String bufferName) {
    if (spots.size() > 1) {
      DrawingBuffer db = main.getBuffer(bufferName);
      if (db == null) {
        db = new DrawingBuffer();
        main.addBuffer(bufferName, db);
      }
      DrawingBufferRoutines.dots(db, spots, 2.5, 0.3, Color.BLACK, Color.PINK);
    }
  }

  void drawDots(Sequence seq, boolean plain, boolean curvy, boolean slow, boolean both,
      boolean corner, String bufferName) {
    if (seq.size() > 1) {
      DrawingBuffer db = main.getBuffer(bufferName);
      if (db == null) {
        db = new DrawingBuffer();
        main.addBuffer(bufferName, db);
        db.setVisible(false);
      }
      List<Pt> plainDots = new ArrayList<Pt>();
      List<Pt> curvyDots = new ArrayList<Pt>();
      List<Pt> slowDots = new ArrayList<Pt>();
      List<Pt> bothDots = new ArrayList<Pt>();
      List<Pt> cornerDots = new ArrayList<Pt>();
      for (Pt pt : seq) {
        if (plain) {
          plainDots.add(pt);
        }
        if (curvy && pt.getBoolean("curvy")) {
          curvyDots.add(pt);
        }
        if (slow && pt.getBoolean("slow")) {
          slowDots.add(pt);
        }
        if (both && pt.getBoolean("both")) {
          bothDots.add(pt);
        }
        if (corner && pt.getBoolean("corner")) {
          cornerDots.add(pt);
        }
      }
      if (plainDots.size() > 0) {
        DrawingBufferRoutines.dots(db, plainDots, 2.5, 0.3, Color.BLACK, null);
      }
      if (curvyDots.size() > 0) {
        DrawingBufferRoutines.dots(db, curvyDots, 2.5, 0.3, Color.BLACK, Color.YELLOW);
      }
      if (slowDots.size() > 0) {
        DrawingBufferRoutines.dots(db, slowDots, 2.5, 0.3, Color.BLACK, Color.BLUE);
      }
      if (bothDots.size() > 0) {
        DrawingBufferRoutines.dots(db, bothDots, 2.5, 0.3, Color.BLACK, Color.GREEN);
      }
      if (cornerDots.size() > 0) {
        DrawingBufferRoutines.dots(db, cornerDots, 2.5, 0.3, Color.BLACK, Color.RED);
      }
    }
  }

  void drawPrims(Set<Primitive> inSet, Color color, String name) {
    DrawingBuffer db = new DrawingBuffer();
    boolean dirty = false;
    for (Primitive prim : inSet) {
      if (prim instanceof LineSegment) {
        dirty = true;
        DrawingBufferRoutines.patch(db, prim.getSeq(), prim.getStartIdx(), prim.getEndIdx(), 2.0,
            color);
        DrawingBufferRoutines.text(db, Functions.getMean(prim.getP1(), prim.getP2()).getTranslated(
            5, 10), prim.getShortStr(), Color.BLACK);
      }
    }
    if (dirty) {
      main.addBuffer(name, db);
    } else {
      main.removeBuffer(name);
    }
  }

  void drawSimilarLength(Sequence seq) {
    DrawingBuffer db = new DrawingBuffer();
    boolean dirty = false;
    Neanderthal data = (Neanderthal) main.getScript("Neanderthal");
    for (Primitive prim : Neanderthal.getPrimitiveSet(seq)) {
      dirty = true;
      DrawingBufferRoutines.patch(db, seq, prim.getStartIdx(), prim.getEndIdx(), 2.0, Color.RED);
      double pathLength = prim.getSeq().getPathLength(prim.getStartIdx(), prim.getEndIdx());
      Set<Primitive> similar = data.getLengthGraph().getSimilar(pathLength, pathLength * 0.25);
      for (Primitive adj : similar) {
        if ((adj.getSeq() == prim.getSeq() && adj.getStartIdx() == prim.getStartIdx() && adj
            .getEndIdx() == prim.getEndIdx()) == false) {
          DrawingBufferRoutines.patch(db, adj.getSeq(), adj.getStartIdx(), adj.getEndIdx(), 2.0,
              Color.GREEN);
        }
      }
    }
    if (dirty) {
      main.addBuffer("similength", db);
    } else {
      main.removeBuffer("similength");
    }
  }

  void drawAdjacent(Sequence seq) {
    DrawingBuffer db = new DrawingBuffer();
    boolean dirty = false;
    Neanderthal data = (Neanderthal) main.getScript("Neanderthal");
    for (Primitive prim : Neanderthal.getPrimitiveSet(seq)) {
      dirty = true;
      // DrawingBufferRoutines.patch(db, seq, prim.getStartIdx(), prim.getEndIdx(), 2.0, Color.RED);
      double pathLength = prim.getSeq().getPathLength(prim.getStartIdx(), prim.getEndIdx());
      double radius = pathLength * 0.20;
      Set<Pt> ends = data.getEndPoints().getNear(prim.getSeq().get(prim.getStartIdx()), radius);
      ends.addAll(data.getEndPoints().getNear(prim.getSeq().get(prim.getEndIdx()), radius));
      Set<Primitive> adjacentPrims = Neanderthal.getPrimitives(ends);
      adjacentPrims.remove(prim);
      for (Primitive adj : adjacentPrims) {
        DrawingBufferRoutines.patch(db, adj.getSeq(), adj.getStartIdx(), adj.getEndIdx(), 2.0,
            Color.GREEN);
      }
    }
    if (dirty) {
      main.addBuffer("adjacent", db);
    } else {
      main.removeBuffer("adjacent");
    }
  }

  void drawParallelPerpendicular(Sequence seq) {
    DrawingBuffer db = new DrawingBuffer();
    boolean dirty = false;
    Neanderthal data = (Neanderthal) main.getScript("Neanderthal");
    for (Primitive prim : (SortedSet<Primitive>) seq.getAttribute(Neanderthal.PRIMITIVES)) {
      if (prim instanceof LineSegment) {
        dirty = true;
        DrawingBufferRoutines.patch(db, seq, prim.getStartIdx(), prim.getEndIdx(), 2.0, Color.RED);
        Set<Primitive> set = data.getAngleGraph().getNear((LineSegment) prim, 0, 0.2);
        // bug(set.size() + " are parallel to " + ((LineSegment) prim).getFixedAngle() + ": ");
        for (Primitive p : set) {
          // bug("  " + p.getFixedAngle());
          DrawingBufferRoutines.patch(db, p.getSeq(), p.getStartIdx(), p.getEndIdx(), 2.0,
              Color.BLUE);
        }
        set = data.getAngleGraph().getNear((LineSegment) prim, Math.PI / 2, 0.2);
        // bug(set.size() + " are perpendicular to " + ((LineSegment) prim).getFixedAngle() + ": ");
        for (Primitive p : set) {
          // bug("  " + p.getFixedAngle());
          DrawingBufferRoutines.patch(db, p.getSeq(), p.getStartIdx(), p.getEndIdx(), 2.0,
              Color.GREEN);
        }
      }
    }
    if (dirty) {
      main.addBuffer("paraperp", db);
    } else {
      main.removeBuffer("paraperp");
    }
  }
}
