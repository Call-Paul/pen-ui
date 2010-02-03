package org.six11.skrui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import org.six11.util.Debug;
import org.six11.util.gui.Colors;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.OliveSoupEvent;
import org.six11.util.pen.OliveSoupListener;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class PrettyPrinterSegments extends DrawingScript {

  @Override
  public Map<String, BoundedParameter> initializeParameters() {
    return null; // it is OK to return null.
  }

  @Override
  public void initialize() {
    getSoup().addSoupListener("segmentation", new OliveSoupListener() {
      @SuppressWarnings("unchecked")
      public void handleSoupEvent(OliveSoupEvent ev) {
        prettyPrint((SortedSet<Segment>) ev.getData());
      }
    });
  }

  private void prettyPrint(SortedSet<Segment> segs) {
    if (segs.size() > 0) {
      DrawingBuffer db = new DrawingBuffer();
      Set<Sequence> involvedSequences = new HashSet<Sequence>();

      for (Segment seg : segs) {
        DrawingBufferRoutines.seg(db, seg, Color.BLACK, getParam(CornerFinder.K_LINE_RATIO)
            .getValue());
        involvedSequences.add(seg.seq);
      }
      main.getDrawingSurface().getSoup().addBuffer(db);

      for (Sequence seq : involvedSequences) {
        bug("Disabling visual for a sequence...");
        disableOriginalInput(seq);
        List<Pt> corners = new ArrayList<Pt>();
        for (Pt pt : seq) {
          if (pt.getBoolean("corner")) {
            corners.add(pt);
          }
        }
        drawCorners(corners, seq);
      }

    }
  }

  private void disableOriginalInput(Sequence seq) {
    main.getDrawingSurface().getSoup().getDrawingBufferForSequence(seq).setVisible(false);
  }

  private void drawCorners(List<Pt> corners, Sequence seq) {
    if (corners != null && corners.size() > 0) {
      DrawingBuffer db = new DrawingBuffer();
      Color cornerColor = new Color(255, 0, 0, 127);
      Color mergedColor = new Color(0, 0, 255, 127);
      Color normalColor = new Color(255, 255, 255, 127);
      Color curvyColor = Colors.makeAlpha(Color.GREEN, 0.6f);
      Color slowColor = Colors.makeAlpha(Color.YELLOW, 0.6f);
      Color slowAndCurvyColor = Colors.makeAlpha(Color.MAGENTA, 0.6f);
      boolean addedSomething = false;
      for (Pt pt : seq) {
        boolean specialPoint = false;
        Color c = cornerColor;
        if (pt.getBoolean("corner")) {
          specialPoint = true;
        } else if (pt.getBoolean("removed")) {
          c = mergedColor;
          specialPoint = true;
        } else if (pt.getBoolean("slow") && pt.getBoolean("curvy")) {
          c = slowAndCurvyColor;
        } else if (pt.getBoolean("curvy")) {
          c = curvyColor;
        } else if (pt.getBoolean("slow")) {
          c = slowColor;
        } else {
          c = normalColor;
        }

        if (specialPoint) {
          DrawingBufferRoutines.dot(db, pt, 4.0, 0.4, Color.BLACK, c);
          addedSomething = true;
        } else {
          // DrawingBufferRoutines.rect(db, pt, 3.0, 3.0, Color.BLACK, c, 0.3);
        }
      }
      if (addedSomething) {
        main.getDrawingSurface().getSoup().addBuffer(db);
      }
    }
  }

  private static void bug(String what) {
    Debug.out("PrettyPrinterSegments", what);
  }
}
