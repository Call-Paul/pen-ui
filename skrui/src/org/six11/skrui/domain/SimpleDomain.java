package org.six11.skrui.domain;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.six11.skrui.DrawingBufferRoutines;
import org.six11.skrui.script.Neanderthal;
import org.six11.skrui.script.Primitive;
import org.six11.util.Debug;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Pt;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class SimpleDomain extends Domain {

  public SimpleDomain(String name, Neanderthal data) {
    super(name, data);
    TriangleTemplate triangle = new TriangleTemplate(this);
    RectangleTemplate rectangle = new RectangleTemplate(this);
    ArrowTemplate arrow = new ArrowTemplate(this);
    templates.add(triangle);
    templates.add(rectangle);
    templates.add(arrow);

    addRenderer(triangle.name, new ShapeRenderer() {
      public void draw(DrawingBuffer db, Shape s) {
        Primitive line1 = s.subshapes.get("line1");
        Primitive line2 = s.subshapes.get("line2");
        Primitive line3 = s.subshapes.get("line3");
        List<Pt> points = extractCircuit(line1, line2, line3);
        DrawingBufferRoutines.fill(db, points, 1.1, Color.BLACK, new Color(1f, 0f, 0f, 0.5f));
      }
    });

    addRenderer(rectangle.name, new ShapeRenderer() {
      public void draw(DrawingBuffer db, Shape s) {
        Primitive line1 = s.subshapes.get("line1");
        Primitive line2 = s.subshapes.get("line2");
        Primitive line3 = s.subshapes.get("line3");
        Primitive line4 = s.subshapes.get("line4");
        List<Pt> points = extractCircuit(line1, line2, line3, line4);
        DrawingBufferRoutines.fill(db, points, 1.1, Color.BLACK, new Color(0f, 1f, 0f, 0.5f));
      }
    });

    addRenderer(arrow.name, new ShapeRenderer() {
      @Override
      public void draw(DrawingBuffer db, Shape s) {
        Primitive head1 = s.subshapes.get("head1");
        Primitive shaft = s.subshapes.get("shaft");
        Pt tip = shaft.getEndPt();
        Pt start = shaft.getStartPt();
        if (head1.getStartPt().distance(shaft.getStartPt()) < head1.getStartPt().distance(
            shaft.getEndPt())) {
          tip = shaft.getStartPt();
          start = shaft.getEndPt();
        }
        DrawingBufferRoutines.arrow(db, start, tip, 4.5, Color.BLUE);
      }
    });
  }

  private List<Pt> extractCircuit(Primitive... ordered) {
    List<Pt> ret = new ArrayList<Pt>();
    for (int i = 0; i < ordered.length; i++) {
      ret.add(agree(ordered, i, (i + 1) % ordered.length));
    }
    ret.add(ret.get(0));
    return ret;
  }

  @SuppressWarnings("unused")
  private void bug(String what) {
    Debug.out("SimpleDomain", what);
  }

  private Pt agree(Primitive[] ordered, int i, int j) {
    Primitive a = ordered[i];
    Primitive b = ordered[j];
    Pt closest = null;
    double bestDist = Double.MAX_VALUE;
    double dist;
    dist = a.getStartPt().distance(b.getStartPt());
    if (dist < bestDist) {
      bestDist = dist;
      closest = a.getStartPt();
    }
    dist = a.getStartPt().distance(b.getEndPt());
    if (dist < bestDist) {
      bestDist = dist;
      closest = a.getStartPt();
    }
    dist = a.getEndPt().distance(b.getStartPt());
    if (dist < bestDist) {
      bestDist = dist;
      closest = a.getEndPt();
    }
    dist = a.getEndPt().distance(b.getEndPt());
    if (dist < bestDist) {
      bestDist = dist;
      closest = a.getEndPt();
    }
    return closest;
  }
}
