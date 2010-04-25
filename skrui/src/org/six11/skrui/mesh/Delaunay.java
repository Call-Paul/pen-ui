package org.six11.skrui.mesh;

import java.awt.Color;
import java.util.List;
import java.util.Set;

import org.six11.skrui.DrawingBufferRoutines;
import org.six11.skrui.Main;
import org.six11.skrui.script.Animation;
import org.six11.util.Debug;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Delaunay {

  Main main;

  public Delaunay(Main m) {
    this.main = m;
  }

  public static void bug(String what) {
    Debug.out("Delaunay", what);
  }

  private Animation getAni() {
    return (Animation) main.getScript("Animation");
  }

  public void triangulate(Sequence seq) {
    List<Pt> decimated = Functions.getNormalizedSequence(seq.getPoints(), 30);

    if (main.getScript("Animation") != null) {
      getAni().startAnimation(main.getDrawingSurface().getBounds(), "png");
    }
    Mesh mesh = new Mesh(decimated, true);
    main.addBuffer(animate(mesh.getTriangles(), true, false));
  }

  private DrawingBuffer animate(Set<Triangle> triangles, boolean override,
      boolean showOutsideTriangles) {
    DrawingBuffer db = null;
    if (override || main.getScript("Animation") != null) {
      db = new DrawingBuffer();
      for (Triangle t : triangles) {
        if (t.getMeshLocation() == Where.Inside || showOutsideTriangles) {
          DrawingBufferRoutines.dot(db, t.getCentroid(), 5.0, 0.5, Color.BLACK, Color.LIGHT_GRAY);
          List<Pt> verts = t.getPoints();
          DrawingBufferRoutines.dot(db, verts.get(0), 3.0, 0.3, Color.BLACK, Color.GREEN);
          DrawingBufferRoutines.dot(db, verts.get(1), 3.0, 0.3, Color.BLACK, Color.GREEN);
          DrawingBufferRoutines.dot(db, verts.get(2), 3.0, 0.3, Color.BLACK, Color.GREEN);
          DrawingBufferRoutines.line(db, verts.get(0), verts.get(1), Color.GRAY, 0.5);
          DrawingBufferRoutines.line(db, verts.get(1), verts.get(2), Color.GRAY, 0.5);
          DrawingBufferRoutines.line(db, verts.get(2), verts.get(0), Color.GRAY, 0.5);
        }
      }
      if (main.getScript("Animation") != null) {
        getAni().addFrame(db, false);
      }

    }
    return db;
  }
}
