package org.six11.sf;

import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

import org.six11.sf.constr.ColinearUserConstraint;
import org.six11.sf.constr.RightAngleUserConstraint;
import org.six11.sf.constr.SameAngleUserConstraint;
import org.six11.sf.constr.SameLengthUserConstraint;
import org.six11.sf.constr.UserConstraint;
import org.six11.util.gui.shape.Circle;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;
import org.six11.util.solve.Constraint;
import org.six11.util.solve.DistanceConstraint;
import org.six11.util.solve.MultisourceNumericValue;

import com.jogamp.opengl.util.awt.TextRenderer;

import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

public class SketchRenderer {

  // GL colors as float arrays
  public static float[] black = new float[] {
      0f, 0f, 0f, 1f
  };

  public static float[] gray = new float[] {
      0.5f, 0.5f, 0.5f, 1f
  };

  public static float[] lightGray = new float[] {
      0.8f, 0.8f, 0.8f, 1f
  };

  public static float[] red = new float[] {
      1f, 0f, 0f, 1f
  };

  public static float[] green = new float[] {
      0f, 1f, 0f, 1f
  };

  public static float[] blue = new float[] {
      0f, 0f, 1f, 1f
  };

  public static float[] skyBlue = new float[] {
      0.44f, 0.64f, 0.88f, 1f
  };

  public static float[] bloodRed = new float[] {
      0.65f, 0.05f, 0.05f, 1f
  };

  public static float[] peachy = new float[] {
      1f, 0.5f, 0.5f, 0.75f
  };
  // pen settings (colors and thicknesses)
  //
  // thicknesses first
  public final static float DEFAULT_WET_THICKNESS = 1.8f;
  public final static float CONSTRAINT_LINE_THICKNESS = 1.4f;

  // colors next
  public final static float[] DEFAULT_WET_COLOR = black;
  public final static float[] LATCH_SPOT_COLOR = skyBlue;
  public final static float[] ERASE_COLOR = bloodRed;
  public final static float[] CONSTRAINT_COLOR = bloodRed;
  public final static float[] SELECTION_COLOR = peachy;

  private static final Vec EAST = new Vec(1, 0);

  private transient GL2 gl; // only valid during the render method
  private transient SketchBook model; // also valid only when rendering
  private transient GLAutoDrawable drawable; // same

  private DrawingSurface surface;

  /**
   * The main render function. This is called when it is time to redraw the screen.
   * 
   * @param model
   *          the source of all data except the current scribble
   * @param drawable
   *          gives access to the current opengl pipe
   * @param scribble
   *          the current scribble, only valid when the user is drawing
   * @param drawDots
   *          set to true to turn on dot rendering (for debugging)
   * @param drawingSurface
   */
  public void render(SketchBook model, GLAutoDrawable drawable, List<Pt> scribble,
      boolean drawDots, DrawingSurface drawingSurface) {
    this.drawable = drawable;
    this.gl = drawable.getGL().getGL2();
    this.model = model;
    this.surface = drawingSurface;
    renderStencils();
    renderGeometry();
    renderDerivedGuides();
    renderGuides();
    renderConstraints();
    renderUnanalyzed();
    renderScribble(scribble, drawDots);
    renderErase();
  }

  private void renderStencils() {
    Set<Stencil> stencils = model.getStencils();

    Set<Stencil> later = new HashSet<Stencil>();
    for (Stencil s : stencils) {
      if (model.getSelectedStencils().contains(s)) {
        later.add(s);
      } else {
        List<Pt> stencilPath = s.getPath();
        gl.glColor3fv(lightGray, 0);
        fillPoly(stencilPath);
//        DrawingBufferRoutines.fillShape(buf, s.getShape(true), colors.get("stencil"), 0);
      }
    }
    if (later.size() > 0) {
      for (Stencil s : later) {
//        DrawingBufferRoutines
//            .fillShape(selBuf, s.getShape(true), colors.get("selected stencil"), 0);
      }
    }
  }

  private void renderDerivedGuides() {
    for (Guide g : model.getDerivedGuides()) {
      switch (g.getType()) {
        case Circle:
          renderGuideCircle((GuideCircle) g);
          break;
        case Line:
          renderGuideLine((GuideLine) g);
          break;
        case Point:
          renderGuidePoint((GuidePoint) g);
          break;
        case Unknown:
          break;
      }
    }
  }

  private void renderGuideCircle(GuideCircle g) {
    Pt hoverPt = surface.getHoverPoint();
    if (hoverPt != null) {
      double toHover = g.getCenter().distance(hoverPt);
      double residual = 0;
      Circle guideCirc = g.getCircle();
      if (g.isFixedRadius()) {
        residual = Math.abs(toHover - g.getRadius());
      } else {
        guideCirc = new Circle(g.getCenter(), toHover);
        g.setCircle(guideCirc);
      }
      float alpha = (float) getAlpha(residual, 5, 30, 0.1);
      gl.glColor4f(lightGray[0], lightGray[1], lightGray[2], alpha);
      gl.glLineWidth(1f);
      gl.glLineStipple(3, (short) 0xAAAA);
      gl.glEnable(GL2.GL_LINE_STIPPLE);
      circle(guideCirc);
      gl.glDisable(GL2.GL_LINE_STIPPLE);
    }
  }

  private void renderGuideLine(GuideLine g) {
    Pt hoverPt = surface.getHoverPoint();
    if (hoverPt != null) {
      if (g.isDynamic()) {
        g.myLine = new Line(g.a, hoverPt);
      }
      gl.glLineWidth(1f);
      gl.glColor4fv(lightGray, 0);
      gl.glLineStipple(3, (short) 0xAAAA);
      gl.glEnable(GL2.GL_LINE_STIPPLE);
      screenLine(g.myLine, drawable.getWidth(), drawable.getHeight());
      gl.glDisable(GL2.GL_LINE_STIPPLE);
    }
  }

  private void renderGuidePoint(GuidePoint g) {
    Pt spot = g.getLocation();
    gl.glLineWidth(1f);
    gl.glColor4fv(gray, 0);
    dot(spot, 4);
    gl.glColor4fv(lightGray, 0);
    fillDot(spot, 4);
  }

  private void renderGuides() {
    gl.glLineWidth(1f);
    float[] c;
    float r;
    for (GuidePoint gpt : model.getGuidePoints()) {
      if (model.getActiveGuidePoints().contains(gpt)) {
        c = SELECTION_COLOR;
        r = 4f;
      } else {
        c = lightGray;
        r = 3f;
      }
      gl.glColor3fv(c, 0);
      fillDot(gpt.getLocation(), r);
      gl.glColor3fv(black, 0);
      dot(gpt.getLocation(), r);
    }
  }

  private void renderConstraints() {
    for (UserConstraint c : model.getUserConstraints()) {
      switch (c.getType()) {
        case Colinear:
          renderColinearConstraint((ColinearUserConstraint) c);
          break;
        case RightAngle:
          renderRightAngleConstraint((RightAngleUserConstraint) c);
          break;
        case SameAngle:
          renderSameAngleConstraint((SameAngleUserConstraint) c);
          break;
        case SameLength:
          renderSameLengthConstraint((SameLengthUserConstraint) c);
          break;
        case Unknown:
          break;

      }
    }
  }

  private void renderSameLengthConstraint(SameLengthUserConstraint c) {
    for (Constraint basicC : c.getConstraints()) {
      DistanceConstraint dc = (DistanceConstraint) basicC;
      if (dc.getValue() == null) {
        bug("ok, so, this distance constraint has a null value.");
      }
      Vec segDir = new Vec(dc.getP1(), dc.getP2()).getUnitVector();
      Pt mid = Functions.getMean(dc.getP1(), dc.getP2());
      if (dc.getValue() instanceof MultisourceNumericValue) {
        gl.glLineWidth(3f);
        gl.glColor3fv(red, 0);
        acuteHash(mid, segDir, 24);
      } else {
        bug("This part is untested.");
        Vec segDirNorm = segDir.getNormal();
        Pt textLoc = mid.getTranslated(segDirNorm, 8);
        Material.Units units = model.getMasterUnits();
        double asUnits = Material.fromPixels(units, dc.getValue().getValue());
        text("" + num(asUnits), textLoc);
      }
    }
  }

  private void renderSameAngleConstraint(SameAngleUserConstraint c) {
  }

  private void renderColinearConstraint(ColinearUserConstraint c) {
  }

  private void renderRightAngleConstraint(RightAngleUserConstraint c) {
    Pt[] spots = c.getSpots();
    Pt left = spots[RightAngleUserConstraint.SPOT_LEFT];
    Pt right = spots[RightAngleUserConstraint.SPOT_RIGHT];
    Pt ful = spots[RightAngleUserConstraint.SPOT_FULCRUM];
    gl.glLineWidth(CONSTRAINT_LINE_THICKNESS);
    gl.glColor3fv(CONSTRAINT_COLOR, 0);
    gl.glBegin(GL2.GL_LINE_STRIP);
    {
      gl.glVertex2f(left.fx(), left.fy());
      gl.glVertex2f(ful.fx(), ful.fy());
      gl.glVertex2f(right.fx(), right.fy());
    }
    gl.glEnd();
  }

  private void renderErase() {
    if (model.isErasing()) {
      Pt killSpot = model.getEraseSpot();
      if (killSpot != null) {
        gl.glLineWidth(6);
        gl.glColor3fv(ERASE_COLOR, 0);
        cross(killSpot, 30f);
      }
    }
  }

  private void renderUnanalyzed() {
    List<Ink> inkStrokes = model.getUnanalyzedInk();
    for (Ink ink : inkStrokes) {
      List<Pt> points = ink.getSequence().getPoints();
      renderScribble(points, false);
    }
  }

  /**
   * Render a scribble, optionally with dots.
   * 
   * @param scribble
   *          the current pen path. safe for this to be null.
   * @param drawDots
   *          set to true if you want to see little dots where the pen is.
   */
  private void renderScribble(List<Pt> scribble, boolean drawDots) {
    if (scribble != null) {
      gl.glLineWidth(DEFAULT_WET_THICKNESS);
      gl.glColor3fv(DEFAULT_WET_COLOR, 0);
      gl.glBegin(GL2.GL_LINE_STRIP);
      {
        for (Pt pt : scribble) {
          gl.glVertex2f(pt.fx(), pt.fy());
        }
      }
      gl.glEnd();

      if (drawDots) {
        gl.glLineWidth(1.0f);
        gl.glColor3fv(gray, 0);
        for (Pt pt : scribble) {
          box(pt, 4f);
        }
      }
    }
  }

  private void renderGeometry() {
    Set<Pt> notLatched = new HashSet<Pt>();
    for (Segment seg : model.getGeometry()) {
      gl.glLineWidth(3.8f); // ensure pen settings ok because render unlatched changes it
      gl.glColor3fv(black, 0);
      switch (seg.getType()) {
        case Line:
          line(seg.getP1(), seg.getP2());
          break;
        case EllipticalArc:
        case Curve:
        case Blob:
          curve(seg.asSpline().getPoints());
          break;
        case Circle:
          circle(seg.getCircle());
          break;
        case CircularArc:
          curve(seg.getPointList());
          break;
        case Ellipse:
          curve(seg.getPointList());
          break;
        case Dot:
          bug("Can't do " + seg.getType() + " yet.");
          break;
        case Unknown:
          bug("Unknown type: " + seg.getType());
          break;
      }

      // draw latchedness
      if (!seg.isSingular()) {
        if (!(model.findRelatedSegments(seg.getP1()).size() > 1)) {
          notLatched.add(seg.getP1());
          renderUnlatched(seg.getP1(), seg.getStartDir(), bloodRed, 10f, 6f);
        }
        if (!(model.findRelatedSegments(seg.getP2()).size() > 1)) {
          renderUnlatched(seg.getP2(), seg.getEndDir(), bloodRed, 10f, 6f);
          notLatched.add(seg.getP2());
        }
      }
    } // end big geometry for-loop.

    // render the latched points
    for (Pt pt : model.getConstraints().getPoints()) {
      if (!notLatched.contains(pt)) {
        renderLatched(pt, LATCH_SPOT_COLOR, 5f, 1f);
      }
    }
  }

  private void renderLatched(Pt pt, float[] color, float side, float thick) {
    gl.glLineWidth(thick);
    gl.glColor3fv(color, 0);
    box(pt, side);
  }

  private void renderUnlatched(Pt pt, Vec dir, float[] color, float length, float thick) {
    Pt away = pt.getTranslated(dir, length);
    gl.glLineWidth(thick);
    gl.glColor4fv(color, 0);
    line(pt, away);
  }

  public double getAlpha(double distance, double min, double max, double minRetVal) {
    double ret = 0;
    if (distance < min) {
      ret = 1;
    } else if (distance < max) {
      ret = 1 - ((distance - min) / (max - min));
      ret = Math.sqrt(ret);
    } else {
      ret = 0;
    }
    return Math.max(ret, minRetVal);
  }

  private void acuteHash(Pt mid, Vec segDir, float length) {
    gl.glPushMatrix();
    gl.glTranslatef(mid.fx(), mid.fy(), 0);
    double angleRadians = Functions.getSignedAngleBetween(segDir, EAST);
    float angle = (float) Math.toDegrees(angleRadians);
    gl.glRotated(-angle, 0, 0, 1);
    float halfLen = length / 2f;
    gl.glRotatef(45, 0, 0, 1);
    gl.glBegin(GL2.GL_LINE_STRIP);
    {
      gl.glVertex2f(-halfLen, 0);
      gl.glVertex2f(halfLen, 0);
    }
    gl.glEnd();
    gl.glPopMatrix();
  }

  private void fillDot(Pt pt, float r) {
    int sides = 36; // this should depend on circumference. fewer size for small circles.
    float twoPi = 2 * (float) Math.PI;
    float step = twoPi / (float) sides;
    float x = pt.fx(), y = pt.fy();
    gl.glBegin(GL2.GL_TRIANGLE_FAN);
    {
      gl.glVertex2f(pt.fx(), pt.fy());
      for (float theta = 0; theta < twoPi; theta = theta + step) {
        float px = x + (r * (float) cos(theta));
        float py = y + (r * (float) sin(theta));
        gl.glVertex2f(px, py);
      }
    }
    gl.glEnd();
  }

  private void dot(Pt pt, float r) {
    int sides = 36; // this should depend on circumference. fewer size for small circles.
    float twoPi = 2 * (float) Math.PI;
    float step = twoPi / (float) sides;
    float x = pt.fx(), y = pt.fy();
    gl.glBegin(GL2.GL_LINE_STRIP);
    {
      for (float theta = 0; theta < twoPi; theta = theta + step) {
        float px = x + (r * (float) cos(theta));
        float py = y + (r * (float) sin(theta));
        gl.glVertex2f(px, py);
      }
    }
    gl.glEnd();
  }

  private void circle(Circle circle) {
    Pt center = circle.getCenter();
    float r = (float) circle.getRadius();
    dot(center, r);
  }

  private void curve(List<Pt> points) {
    gl.glBegin(GL2.GL_LINE_STRIP);
    {
      for (Pt pt : points) {
        gl.glVertex2f(pt.fx(), pt.fy());
      }
    }
    gl.glEnd();
  }

  private void line(Pt p1, Pt p2) {
    gl.glBegin(GL2.GL_LINE_STRIP);
    {
      gl.glVertex2f(p1.fx(), p1.fy());
      gl.glVertex2f(p2.fx(), p2.fy());
    }
    gl.glEnd();
  }

  private void screenLine(Line line, int width, int height) {
    Rectangle bounds = new Rectangle(0, 0, width, height);
    if (bounds.intersectsLine(line)) {
      // find the two intersection points and connect them.
      Pt[] ix = Functions.getIntersectionPoints(bounds, line);
      if (ix[0] != null && ix[1] != null) {
        line(ix[0], ix[1]);
      }
    }
  }

  private void box(Pt pt, float side) {
    float cx = pt.fx(), cy = pt.fy();
    float f = side / 2f;
    gl.glBegin(GL2.GL_LINE_LOOP);
    {
      gl.glVertex2f(cx - f, cy - f);
      gl.glVertex2f(cx + f, cy - f);
      gl.glVertex2f(cx + f, cy + f);
      gl.glVertex2f(cx - f, cy + f);
    }
    gl.glEnd();
  }

  private void cross(Pt pt, float length) {
    float cx = pt.fx(), cy = pt.fy();
    float f = length / 2f;
    gl.glBegin(GL2.GL_LINES);
    {
      gl.glVertex2f(cx - f, cy - f);
      gl.glVertex2f(cx + f, cy + f);
      gl.glVertex2f(cx - f, cy + f);
      gl.glVertex2f(cx + f, cy - f);
    }
    gl.glEnd();
  }


  private void fillPoly(List<Pt> stencilPath) { // TODO: will probably need to use a GLU tesselation thing.
    gl.glBegin(GL2.GL_POLYGON); {
      for (Pt pt : stencilPath) {
        gl.glVertex2f(pt.fx(), pt.fy());
      }
    }
    gl.glEnd();
  }
  
  private void text(String str, Pt loc) {
    // this is untested.
    TextRenderer t12 = surface.getTextRenderer(12);
    t12.beginRendering(drawable.getWidth(), drawable.getHeight());
    float[] color = CONSTRAINT_COLOR;
    t12.setColor(color[0], color[1], color[2], color[3]);
    t12.draw(str, loc.ix(), loc.iy());
    t12.endRendering();
  }

}
