package org.six11.sf;

import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.GLU;
import javax.media.opengl.glu.GLUtessellator;

import org.six11.sf.constr.ColinearUserConstraint;
import org.six11.sf.constr.RightAngleUserConstraint;
import org.six11.sf.constr.SameAngleUserConstraint;
import org.six11.sf.constr.SameLengthUserConstraint;
import org.six11.sf.constr.UserConstraint;
import org.six11.util.data.Lists;
import org.six11.util.gui.shape.Circle;
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
  public static float[] white = new float[] {
      1f, 1f, 1f, 1f
  };

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
  public final static float[] STENCIL_UNSELECTED_COLOR = lightGray;
  public final static float[] STENCIL_SELECTED_COLOR = skyBlue;
  public final static float[] SEGMENT_SELECTED_COLOR = brighter(skyBlue);
  public final static float[] DOT_SELECTED_COLOR = peachy;

  private static float[] brighter(float[] c) {
    float[] r = new float[4];
    r[0] = (float) Math.sqrt(c[0]);
    r[1] = (float) Math.sqrt(c[1]);
    r[2] = (float) Math.sqrt(c[2]);
    r[3] = c[3]; // leave alpha alone.
    return r;
  }

  private static float[] makeAlphaColor(float[] color, float alpha) {
    return new float[] {
        color[0], color[1], color[2], alpha
    };
  }

  private static final Vec EAST = new Vec(1, 0);
  private static final Vec WEST = new Vec(-1, 0);
  private static final Vec NORTH = new Vec(0, 1);
  private static final Vec SOUTH = new Vec(0, -1);

  private transient GL2 gl; // only valid during the render method
  private transient GLU glu; // only valid when rendering
  private transient SketchBook model; // also valid only when rendering
  private transient GLAutoDrawable drawable; // same

  private DrawingSurface surface;
  private TessCallback tessCallback;

  public void init(GLAutoDrawable drawable) {
    this.drawable = drawable;
    this.gl = drawable.getGL().getGL2();
    this.tessCallback = new TessCallback(gl);
  }

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

    // retain variabels for this round of rendering
    this.drawable = drawable;
    this.gl = drawable.getGL().getGL2();
    this.glu = drawingSurface.glu;
    this.model = model;
    this.surface = drawingSurface;

    // render things back-to-front
    renderStencils();
    renderGeometry();
    renderFlowSelection();
    renderDerivedGuides();
    renderGuides();
    renderConstraints();
    renderUnanalyzed();
    renderScribble(scribble, drawDots);
    renderErase();
    renderTextInput();
  }

  private void renderStencils() {
    Set<Stencil> stencils = model.getStencils();

    Set<Stencil> later = new HashSet<Stencil>();
    for (Stencil s : stencils) {
      if (model.getSelectedStencils().contains(s)) {
        later.add(s);
      } else {
        renderStencil(s, false);
      }
    }
    if (later.size() > 0) {
      for (Stencil s : later) {
        renderStencil(s, true);
      }
    }
  }

  /**
   * Render a top-level stencil. This will create a tessellated object and possibly add holes to it
   * (if the stencil has children).
   * 
   * @param stencil
   */
  private void renderStencil(Stencil stencil, boolean isSelected) {
    gl.glLineWidth(4f);
    if (isSelected) {
      gl.glColor4fv(STENCIL_SELECTED_COLOR, 0);
    } else {
      gl.glColor4fv(STENCIL_UNSELECTED_COLOR, 0);
    }
    tessellateShape(stencil.getShape(false));
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
    float z = model.getCamera().getZoom();
    for (GuidePoint gpt : model.getGuidePoints()) {
      if (model.getActiveGuidePoints().contains(gpt)) {
        c = DOT_SELECTED_COLOR;
        r = 4f;
      } else {
        c = lightGray;
        r = 3f;
      }
      gl.glColor3fv(c, 0);
      fillDot(gpt.getLocation(), r / z);
      gl.glColor3fv(black, 0);
      dot(gpt.getLocation(), r / z);
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
    Set<Pt> spots = new HashSet<Pt>();
    float dist = (float) c.getDistance(surface.getHoverPoint());
    float[] color = makeAlphaColor(CONSTRAINT_COLOR, getAlpha(dist, 10f, 50f, 0.15f));
    float zoom = model.getCamera().getZoom();
    for (Constraint basicC : c.getConstraints()) {
      DistanceConstraint dc = (DistanceConstraint) basicC;
      if (dc.getValue() == null) {
        bug("ok, so, this distance constraint has a null value.");
      }
      Vec segDir = new Vec(dc.getP1(), dc.getP2()).getUnitVector();
      Pt mid = Functions.getMean(dc.getP1(), dc.getP2());
      spots.add(mid);
      if (dc.getValue() instanceof MultisourceNumericValue) {
        gl.glLineWidth(2f);
        gl.glColor4fv(color, 0);
        acuteHash(mid, segDir, 24 / zoom);
      } else {
        Vec segDirNorm = segDir.getNormal();
        Pt textLocModel = mid.getTranslated(segDirNorm, 8 / zoom);
        Material.Units units = model.getMasterUnits();
        double asUnits = Material.fromPixels(units, dc.getValue().getValue());
        text("" + num(asUnits), surface.getTextRenderer(18), textLocModel, color);
      }
    }
    c.setDistanceSpots(spots.toArray(new Pt[0]));
  }

  private void renderSameAngleConstraint(SameAngleUserConstraint c) {
    Pt[][] spots = c.getSpots(16 / model.getCamera().getZoom());
    float dist = (float) c.getDistance(surface.getHoverPoint());
    float[] color = makeAlphaColor(CONSTRAINT_COLOR, getAlpha(dist, 10f, 50f, 0.15f));
    gl.glLineWidth(2f);
    gl.glColor4fv(color, 0);
    Set<Pt> corners = new HashSet<Pt>();
    for (Pt[] spot : spots) {
      List<Pt> arc = Functions.getCircularArc(spot[0], spot[1], spot[2], spot[3], 6);
      curve(arc);
      corners.add(spot[3]);
    }
    c.setDistanceSpots(corners.toArray(new Pt[0]));
  }

  private void renderColinearConstraint(ColinearUserConstraint c) {
    float dist = (float) c.getDistance(surface.getHoverPoint());
    float[] color = makeAlphaColor(CONSTRAINT_COLOR, getAlpha(dist, 10f, 50f, 0.15f));
    Pt[] spots = c.getSpots();
    Line line = new Line(spots[0], spots[1]);
    gl.glLineWidth(1f);
    gl.glColor4fv(color, 0);
    gl.glLineStipple(3, (short) 0xAAAA);
    gl.glEnable(GL2.GL_LINE_STIPPLE);
    screenLine(line, drawable.getWidth(), drawable.getHeight());
    gl.glDisable(GL2.GL_LINE_STIPPLE);
    c.setDistanceSpots(spots[0], spots[1]);
  }

  private void renderRightAngleConstraint(RightAngleUserConstraint c) {
    Pt[] spots = c.getSpots(16 / model.getCamera().getZoom());
    float dist = (float) c.getDistance(surface.getHoverPoint());
    float[] color = makeAlphaColor(CONSTRAINT_COLOR, getAlpha(dist, 10f, 50f, 0.15f));
    Pt left = spots[RightAngleUserConstraint.SPOT_LEFT];
    Pt right = spots[RightAngleUserConstraint.SPOT_RIGHT];
    Pt ful = spots[RightAngleUserConstraint.SPOT_FULCRUM];
    gl.glLineWidth(CONSTRAINT_LINE_THICKNESS);
    gl.glColor4fv(color, 0);
    gl.glBegin(GL2.GL_LINE_STRIP);
    {
      gl.glVertex2f(left.fx(), left.fy());
      gl.glVertex2f(ful.fx(), ful.fy());
      gl.glVertex2f(right.fx(), right.fy());
    }
    gl.glEnd();
    c.setDistanceSpots(ful);
  }

  private void renderErase() {
    if (model.isErasing()) {
      Pt killSpot = model.getEraseSpot();
      if (killSpot != null) {
        gl.glLineWidth(6);
        gl.glColor3fv(ERASE_COLOR, 0);
        cross(killSpot, 30 / model.getCamera().getZoom());
      }
    }
  }

  private void renderTextInput() {
    if (surface.getTextInput() != null && model.getSelectedSegments().size() == 1) {
      float zoom = model.getCamera().getZoom();
      String str = surface.getTextInput();
      Segment selSeg = Lists.getOne(model.getSelectedSegments());
      Vec segDir = new Vec(selSeg.getP1(), selSeg.getP2()).getUnitVector();
      Vec segDirNorm = segDir.getNormal();
      Pt mid = selSeg.getVisualMidpoint();
      Pt textLocModel = mid.getTranslated(segDirNorm, 8 / zoom);
      Rectangle2D textBox = surface.getTextRenderer(18).getBounds(str);
      float halfPad = 4 / zoom;
      float pad = halfPad * 2;
      float boxX = textLocModel.ix();
      float boxY = textLocModel.iy();
      float boxW = (int) textBox.getWidth() / 2;
      float boxH = (int) textBox.getHeight();
      boxX = boxX - halfPad;
      boxY = boxY - halfPad;
      boxW = boxW + pad;
      boxH = boxH + pad;
      gl.glLineWidth(1f);
      gl.glColor4fv(white, 0);
      fillRect(boxX, boxY, boxW, boxH);
      gl.glColor4fv(skyBlue, 0);
      rect(boxX, boxY, boxW, boxH);
      text(str, surface.getTextRenderer(18), textLocModel.getTranslated(0, boxH/2), skyBlue);
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
    float z = model.getCamera().getZoom();
    float unlatchLength = 10 / z;
    float latchedVertSideLen = 5 / z;
    for (Segment seg : model.getGeometry()) {
      if (model.getSelectedSegments().contains(seg)) {
        gl.glLineWidth(6.3f);
        gl.glColor3fv(SEGMENT_SELECTED_COLOR, 0);
        renderSegment(seg);
      } else {
        gl.glLineWidth(3.8f); // ensure pen settings ok because render unlatched changes it
        gl.glColor3fv(black, 0);
        renderSegment(seg);
      }

      // draw latchedness
      if (!seg.isSingular()) {
        if (!(model.findRelatedSegments(seg.getP1()).size() > 1)) {
          notLatched.add(seg.getP1());
          renderUnlatched(seg.getP1(), seg.getStartDir(), bloodRed, unlatchLength, 6f);
        }
        if (!(model.findRelatedSegments(seg.getP2()).size() > 1)) {
          renderUnlatched(seg.getP2(), seg.getEndDir(), bloodRed, unlatchLength, 6f);
          notLatched.add(seg.getP2());
        }
      }
    } // end big geometry for-loop.

    // render the latched points
    for (Pt pt : model.getConstraints().getPoints()) {
      if (!notLatched.contains(pt)) {
        renderLatched(pt, LATCH_SPOT_COLOR, latchedVertSideLen, 1f);
      }
    }
  }

  private void renderSegment(Segment seg) {
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

  private void renderFlowSelection() {
    Segment fsSeg = surface.getFlowSelectionSegment();
    if (fsSeg != null) {
      List<Pt> def = fsSeg.getDeformedPoints();
      boolean drawNodes = true;
      String state = surface.getFlowSelectionState();
      if (state.equals(DrawingSurface.OP) || state.equals(DrawingSurface.SMOOTH)) {
        drawNodes = true;
      }
      float[] color = new float[] {
          1, 0, 0, 0
      };

      if (def != null) {
        gl.glLineWidth(5);
        for (int i = 0; i < def.size() - 1; i++) {
          Pt a = def.get(i);
          Pt b = def.get(i + 1);
          double aStr = a.getDouble("fsStrength");
          double bStr = b.getDouble("fsStrength");
          double str = Math.max(aStr, bStr);
          color[3] = (float) str;
          gl.glColor4fv(color, 0);
          line(a, b);
        }

        if (drawNodes) {
          gl.glLineWidth(1);

          for (int i = 0; i < def.size(); i++) {
            Pt pt = def.get(i);
            double str = pt.getDouble("fsStrength");
            if (str > 0) {
              gl.glColor4fv(black, 0);
              dot(pt, 2);
              gl.glColor4fv(white, 0);
              fillDot(pt, 2);
            }
          }
        }
      }
    }
  }

  private void tessellateShape(Shape shape) {
    // the following code was adapted from Ric Wright's example on JOGL tessellation
    // at this URL: http://www.geofx.com/html/OpenGL_Eclipse/TextRenderer3D.html
    GLUtessellator tess = GLU.gluNewTess();
    GLU.gluTessCallback(tess, GLU.GLU_TESS_BEGIN, tessCallback);
    GLU.gluTessCallback(tess, GLU.GLU_TESS_END, tessCallback);
    GLU.gluTessCallback(tess, GLU.GLU_TESS_ERROR, tessCallback);
    GLU.gluTessCallback(tess, GLU.GLU_TESS_VERTEX, tessCallback);
    GLU.gluTessCallback(tess, GLU.GLU_TESS_COMBINE, tessCallback);

    GLU.gluTessBeginPolygon(tess, (double[]) null);

    PathIterator pi = shape.getPathIterator(null);
    while (!pi.isDone()) {
      double[] coords = new double[3];
      coords[2] = 0d;
      switch (pi.currentSegment(coords)) {
        case PathIterator.SEG_MOVETO:
          GLU.gluTessBeginContour(tess);
          break;

        case PathIterator.SEG_LINETO:
          GLU.gluTessVertex(tess, coords, 0, coords);
          break;

        case PathIterator.SEG_CLOSE:
          GLU.gluTessEndContour(tess);
          break;
      }
      pi.next();
    }
    GLU.gluTessEndPolygon(tess);
  }

  public float getAlpha(double distance, double min, double max, double minRetVal) {
    double ret = 0;
    if (distance < min) {
      ret = 1;
    } else if (distance < max) {
      ret = 1 - ((distance - min) / (max - min));
      ret = ret * ret;
    } else {
      ret = 0;
    }
    return (float) Math.max(ret, minRetVal);
  }

  void acuteHash(Pt mid, Vec segDir, float length) {
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

  void fillDot(Pt pt, float r) {
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

  void dot(Pt pt, float r) {
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

  void circle(Circle circle) {
    Pt center = circle.getCenter();
    float r = (float) circle.getRadius();
    dot(center, r);
  }

  void curve(List<Pt> points) {
    gl.glBegin(GL2.GL_LINE_STRIP);
    {
      for (Pt pt : points) {
        gl.glVertex2f(pt.fx(), pt.fy());
      }
    }
    gl.glEnd();
  }

  void line(Pt p1, Pt p2) {
    gl.glBegin(GL2.GL_LINE_STRIP);
    {
      gl.glVertex2f(p1.fx(), p1.fy());
      gl.glVertex2f(p2.fx(), p2.fy());
    }
    gl.glEnd();
  }

  void screenLine(Line line, int width, int height) {
    Rectangle bounds = new Rectangle(0, 0, width, height);
    if (bounds.intersectsLine(line)) {
      // find the two intersection points and connect them.
      Pt[] ix = Functions.getIntersectionPoints(bounds, line);
      if (ix[0] != null && ix[1] != null) {
        line(ix[0], ix[1]);
      }
    }
  }

  void box(Pt pt, float side) { // TODO keep this private
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

  void rect(float x, float y, float w, float h) {
    gl.glBegin(GL2.GL_LINE_LOOP);
    {
      gl.glVertex2f(x + 0, y + 0);
      gl.glVertex2f(x + w, y + 0);
      gl.glVertex2f(x + w, y + h);
      gl.glVertex2f(x + 0, y + h);
    }
    gl.glEnd();
  }

  void fillRect(float x, float y, float w, float h) {
    gl.glBegin(GL2.GL_POLYGON);
    {
      gl.glVertex2f(x + 0, y + 0);
      gl.glVertex2f(x + w, y + 0);
      gl.glVertex2f(x + w, y + h);
      gl.glVertex2f(x + 0, y + h);
    }
    gl.glEnd();
  }

  void cross(Pt pt, float length) {
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

  void text(String str, TextRenderer t12, Pt loc, float[] color) {
    // the TextRenderer works with screen coordinates for some reason.
    // We are given 'loc' in model coordinates. To transform it to screen coordinates use gluProject.
    float[] screenCoords = surface.project(gl, loc.fx(), loc.fy());
    Pt screenPt = new Pt(screenCoords[0], screenCoords[1]);

//    TextRenderer t12 = surface.getTextRenderer(12);
//    TextRenderer t12 = new TextRenderer(new Font("SansSerif", Font.PLAIN, 12));
    t12.setSmoothing(true);
    t12.beginRendering(drawable.getWidth(), drawable.getHeight());
    t12.setColor(color[0], color[1], color[2], color[3]);
    t12.draw(str, screenPt.ix(), screenPt.iy());
    t12.endRendering();
  }

  void arrow(Pt start, Pt tip) {
    double length = start.distance(tip);
    double headLength = length / 10.0;
    Vec tipToStart = new Vec(tip, start).getVectorOfMagnitude(headLength);
    Pt cross = tip.getTranslated(tipToStart.getX(), tipToStart.getY());
    Vec outward = tipToStart.getNormal();
    Pt head1 = cross.getTranslated(outward.getX(), outward.getY());
    outward = outward.getFlip();
    Pt head2 = cross.getTranslated(outward.getX(), outward.getY());
    line(start, tip);
    line(head1, tip);
    line(head2, tip);
  }

  public Rectangle2D[] panZoomWidget(GL2 gl, Pt pt) {
    Rectangle2D[] ret = new Rectangle2D[2];
    float w = 104;
    float h = 48;
    float w4 = w / 4;
    float w2 = w / 2;
    float h2 = h / 2;
    float h3 = h / 3;
    float bot = pt.fy() - h2;
    float left = pt.fx() - w4;
    gl.glColor4fv(bloodRed, 0);
    gl.glLineWidth(2f);
    rect(left, bot, w2, h); // two boxes for pan and zoom
    rect(left + w2, bot, w2, h);
    ret[0] = new Rectangle2D.Float(left, bot, w2, h);
    ret[1] = new Rectangle2D.Float(left + w2, bot, w2, h);
    Pt upC = pt.getTranslated(0, h3); // draw chevrons in pan box
    Pt downC = pt.getTranslated(0, -h3);
    Pt leftC = pt.getTranslated(-h3, 0);
    Pt rightC = pt.getTranslated(h3, 0);
    this.gl = gl;
    float chevSize = h / 8;
    chevron(upC, NORTH, chevSize, chevSize);
    chevron(downC, SOUTH, chevSize, chevSize);
    chevron(leftC, WEST, chevSize, chevSize);
    chevron(rightC, EAST, chevSize, chevSize);
    float r = h / 5;
    Pt magCenter = pt.getTranslated(w2 + r / 2, -r / 2);
    Vec handleVec = new Vec(-r, r).getVectorOfMagnitude(r);
    Pt handleDot1 = magCenter.getTranslated(handleVec);
    Pt handleDot2 = handleDot1.getTranslated(handleVec.getVectorOfMagnitude(r * 2));
    dot(magCenter, r);
    gl.glLineWidth(3f);
    line(handleDot1, handleDot2);
    return ret;
  }

  private void chevron(Pt pt, Vec dir, float chevW, float chevH) {
    float halfW = chevW / 2;
    float halfH = chevH / 2;
    Pt a = pt.getTranslated(dir, halfH);
    Pt d = pt.getTranslated(dir.getFlip(), halfH);
    Vec n = dir.getNormal();
    Pt b = d.getTranslated(n, halfW);
    Pt c = d.getTranslated(n.getFlip(), halfW);
    gl.glBegin(GL2.GL_LINE_STRIP);
    {
      gl.glVertex2f(b.fx(), b.fy());
      gl.glVertex2f(a.fx(), a.fy());
      gl.glVertex2f(c.fx(), c.fy());
    }
    gl.glEnd();

  }

}
