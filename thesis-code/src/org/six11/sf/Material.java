package org.six11.sf;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.six11.util.gui.BoundingBox;
import org.six11.util.pen.Pt;

import com.sun.xml.internal.ws.wsdl.writer.document.Part;

import static org.six11.util.Debug.bug;

// import static org.six11.util.Debug.num;

public class Material {

  public enum Units {
    Inch, Centimeter, Millimeter, Meter, Pixel,
  }

  private Units units;
  private double width;
  private double height;
  private BoundingBox materialBB;
  private BoundingBox stencilBB;
  Set<Stencil> stencils;

  public Material(Units units, double width, double height) {
    this.units = units;
    this.width = width;
    this.height = height;
    this.materialBB = new BoundingBox();
    this.stencilBB = new BoundingBox();
    materialBB.add(new Pt(width, height));
  }

  public void addStencil(Stencil s) {
    stencils.add(s);
  }

  public void layoutStencils() {
    stencilBB = new BoundingBox();
    for (Stencil s : stencils) {
      BoundingBox sBB = s.getBoundingBox();
      sBB.translateToOrigin();
      Pt topRight = stencilBB.getTopRight();
      sBB.translate(topRight);
      BoundingBox proposed = stencilBB.copy();
      proposed.add(sBB.getRectangle());
      boolean ok = false;
      if (materialBB.getRectangle().contains(proposed.getMinX(), proposed.getMinY(),
          proposed.getWidth(), proposed.getHeight())) {
        ok = true;
      } else {
        Pt botLeft = stencilBB.getBotLeft();
        sBB.translateToOrigin();
        sBB.translate(botLeft);
        proposed = stencilBB.copy();
        proposed.add(sBB.getRectangle());
        ok = materialBB.getRectangle().contains(proposed.getMinX(), proposed.getMinY(),
            proposed.getWidth(), proposed.getHeight());
      }
      if (ok) {
        // the top left corner of the stencil is sBB.topLeft 

        //      double dx = sBB.getMinX();
        //      double dy = sBB.getMinY();
        //      List<Pt> finishedPoints = new ArrayList<Pt>();
        //      for (Pt pt : s.getPath()) {
        //        Pt finishedPoint = pt.copyXYT();
        //        finishedPoint.getTranslated(dx, dy);
        //      }
      } else {
        bug("Can't fit stencil inside material bounds.");
      }
    }
  }
}
