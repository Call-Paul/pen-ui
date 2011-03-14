package org.six11.skruifab.analysis;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import org.six11.skruifab.DrawnThing;
import org.six11.skruifab.Main;
import org.six11.util.Debug;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

/**
 * This is a Sequence, augmented with GUI stuff. This includes pen state, a cached drawing buffer,
 * and undo/redo support.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Stroke extends Sequence implements DrawnThing {

  /**
   * Records everything you need to reconstitute a stroke. This includes points and pen properties.
   */
  protected static class StrokeState {
    Color penColor;
    double penThickness;
    List<Pt> points;

    /**
     * Store all the relevent information in the given stroke so it may be reconstituted later.
     */
    public StrokeState(Stroke stroke) {
      penColor = (Color) stroke.getAttribute(Main.PEN_COLOR);
      penThickness = (Double) stroke.getAttribute(Main.PEN_THICKNESS);
      points = new ArrayList<Pt>();
      for (Pt pt : stroke) {
        points.add(pt.copy());
      }
    }

    /**
     * Sets the stroke's point locations and pen settings to this memento's values.
     */
    public void reconstitute(Stroke stroke) {
      stroke.setAttribute(Main.PEN_COLOR, penColor);
      stroke.setAttribute(Main.PEN_THICKNESS, penThickness);
      for (int i = 0; i < stroke.size(); i++) {
        Pt newPt = stroke.get(i);
        Pt oldPt = points.get(i);
        newPt.setLocation(oldPt);
        for (String k : oldPt.getAttribs().keySet()) {
          newPt.setAttribute(k, oldPt.getAttribute(k));
        }
      }
    }
  }

  DrawingBuffer db;
  Stack<StrokeState> pastStates;
  Stack<StrokeState> redoStates;

  /**
   * @param id
   */
  public Stroke(int id) {
    super(id);
    init();
  }

  /**
   * 
   */
  public Stroke() {
    super();
    init();
  }

  protected final void init() {
    pastStates = new Stack<StrokeState>();
    redoStates = new Stack<StrokeState>();
  }

  public void setDrawingBuffer(DrawingBuffer buf) {
    db = buf;
  }

  public DrawingBuffer getDrawingBuffer() {
    return db;
  }

  public int undo() {
    if (pastStates.size() > 0) {
      StrokeState nukeMe = pastStates.pop();
      if (pastStates.size() > 0) {
        pastStates.peek().reconstitute(this);
      }
      redoStates.add(nukeMe);
    }
    redraw();
    return pastStates.size();
  }

  public void redo() {
    if (redoStates.size() > 0) {
      StrokeState ss = redoStates.pop();
      ss.reconstitute(this);
      pastStates.add(ss);
    }
    redraw();
  }

  public void snap() {
    StrokeState ss = new StrokeState(this);
    pastStates.push(ss);
    redoStates.clear();
    redraw();
  }

  public void redraw() {
    DrawingBuffer buf = new DrawingBuffer();
    if (getAttribute("pen color") != null) {
      buf.setColor((Color) getAttribute("pen color"));
    } else {
      buf.setColor(DrawingBuffer.getBasicPen().color);
    }
    if (getAttribute("pen thickness") != null) {
      buf.setThickness((Double) getAttribute("pen thickness"));
    } else {
      buf.setThickness(DrawingBuffer.getBasicPen().thickness);
    }
    buf.up();
    buf.moveTo(get(0).x, get(0).y);
    buf.down();
    for (Pt pt : this) {
      buf.moveTo(pt.x, pt.y);
    }
    buf.up();
    setDrawingBuffer(buf);
  }

  /**
   * Gives the set of primitives held in the Analyzer.PRIMITIVES attribute. <b>This will be null if
   * the stroke hasn't been analyzed so it is wise to test for nullness.</b>
   * 
   * @return
   */
  public Set<Primitive> getPrimitives() {
    Set<Primitive> primitives = (Set<Primitive>) getAttribute(Analyzer.PRIMITIVES);
    return primitives;
  }

  @SuppressWarnings("unchecked")
  public List<Pt> getCorners() {
    return (List<Pt>) getAttribute(MergeCF.CORNERS);
  }
  
  public List<Segment> getSegments() {
    return (List<Segment>) getAttribute(MergeCF.SEGMENTS);
  }
  
  public Set<LineSegment> getLineSegments() {
    Set<LineSegment> ret = new HashSet<LineSegment>();
    Set<Primitive> prims = getPrimitives();
    if (prims != null) {
      for (Primitive p : prims) {
        if (p instanceof LineSegment) {
          ret.add((LineSegment) p);
        }
      }
    }
    return ret;
  }
  
  public Set<ArcSegment> getArcSegments() {
    Set<ArcSegment> ret = new HashSet<ArcSegment>();
    Set<Primitive> prims = getPrimitives();
    if (prims != null) {
      for (Primitive p : prims) {
        if (p instanceof ArcSegment) {
          ret.add((ArcSegment) p);
        }
      }
    }
    return ret;
  }
  
  public Set<EllipseSegment> getEllipseSegments() {
    Set<EllipseSegment> ret = new HashSet<EllipseSegment>();
    Set<Primitive> prims = getPrimitives();
    if (prims != null) {
      for (Primitive p : prims) {
        if (p instanceof EllipseSegment) {
          ret.add((EllipseSegment) p);
        }
      }
    }
    return ret;
  }
  
  public Set<Dot> getDots() {
    Set<Dot> ret = new HashSet<Dot>();
    Set<Primitive> prims = getPrimitives();
    if (prims != null) {
      for (Primitive p : prims) {
        if (p instanceof Dot) {
          ret.add((Dot) p);
        }
      }
    }
    return ret;
  }
  
  public Set<Ellipse> getEllipses() {
    Set<Ellipse> ret = new HashSet<Ellipse>();
    Set<Primitive> prims = getPrimitives();
    if (prims != null) {
      for (Primitive p : prims) {
        if (p instanceof Ellipse) {
          ret.add((Ellipse) p);
        }
      }
    }
    return ret;
  }

  public static void bug(String what) {
    Debug.out("Stroke", what);
  }

}
