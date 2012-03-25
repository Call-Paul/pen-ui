package org.six11.sf;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

import java.awt.Dimension;

import org.six11.util.Debug;

public class Camera {

  public static final int LEFT = 0;
  public static final int RIGHT = 1;
  public static final int TOP = 2;
  public static final int BOTTOM = 3;

  float zoom = 1.0f;
  float tx = 0f;
  float ty = 0f;

  public void zoomBy(Dimension d, float amt) {
    zoom = amt;
    correct(d);
  }

  public void zoomTo(Dimension d, float amt, float dx, float dy) {
    tx = tx - dx;
    ty = ty - dy;
    zoom = amt;
    correct(d);
  }

  public void zoomTo(Dimension d, float amt) {
    zoom = amt;
    correct(d);
  }

  public float getZoom() {
    return zoom;
  }

  public float[] getScreenCenter(Dimension d) {
    float[] ortho = getOrthoValues(d);
    float width = ortho[1] - ortho[0];
    float height = ortho[2] - ortho[3];
    float[] ret = new float[2];
    ret[0] = width / 2f;
    ret[1] = height / 2f;
    bug("screen center: " + num(ret[0]) + ", " + num(ret[1]));
    return ret;
  }

  /**
   * Returns the ortho values for a widget of the given size. Uses the camera's current zoom and
   * translation values.
   * 
   * @param d
   *          the widget size (e.g. 1300 x 900)
   * @return { Left, Right, Top, Bottom }. In OpenGL, bottom < top. Makes sense, but is the opposite
   *         of Java.
   */
  public float[] getOrthoValues(Dimension d) {
    return Camera.getOrthoValues(d, zoom, tx, ty);
  }

  public static float[] getOrthoValues(Dimension d, float zoom, float tx, float ty) {
    float dcx = d.width / 2f;
    float dcy = d.height / 2f;
    float viewW = d.width / zoom;
    float viewH = d.height / zoom;
    float vcx = dcx + tx;
    float vcy = dcy + ty;
    float vLeft = vcx - (viewW / 2f);
    float vTop = vcy + (viewH / 2f);
    float vRight = vcx + (viewW / 2f);
    float vBot = vcy - (viewH / 2f);
    float[] ret = new float[] {
        vLeft, vRight, vTop, vBot
    };
    return ret;
  }

  public float getPanX() {
    return tx;
  }

  public float getPanY() {
    return ty;
  }

  public void translateBy(Dimension d, float x, float y) {
    tx = tx + x;
    ty = ty + y;
    correct(d);
  }

  public void translateTo(Dimension d, float x, float y) {
    tx = x;
    ty = y;
    correct(d);
  }

  private void correct(Dimension d) {
    float minZoom = 0.9f;
    if (zoom < minZoom) {
      zoom = minZoom;
    }
    float[] ortho = getOrthoValues(d);
    
    float edgeW = Math.abs((d.width / minZoom) - d.width); // in case minZoom changes to >= 1
    float edgeH = Math.abs((d.height / minZoom) - d.height); // ditto
    float minLeft = -(edgeW + 1);
    float maxRight = d.width + edgeW + 1;
    float maxTop = d.height + edgeH + 1;
    float minBot = -(edgeH + 1);
//    bug("d: " + d.width + ", " + d.height);
//    bug("edgeW: " + num(edgeW));
//    bug("edgeH: " + num(edgeH));
//    bug("l r t b: " + num(ortho));
//    bug("thresholds: " + num(minLeft) + " " + num(maxRight) + " " + num(maxTop) + " " + num(minBot));
//
//    try {
//      Debug.detectNaN(ortho[LEFT]);
//      Debug.detectNaN(ortho[RIGHT]);
//      Debug.detectNaN(ortho[TOP]);
//      Debug.detectNaN(ortho[BOTTOM]);
//      Debug.detectNaN(edgeW);
//      Debug.detectNaN(edgeH);
//      Debug.detectNaN(minLeft);
//      Debug.detectNaN(maxRight);
//      Debug.detectNaN(maxTop);
//      Debug.detectNaN(minBot);
//    } catch (Exception ex) {
//      ex.printStackTrace();
//      System.exit(0);
//    }
    // nudge things to the correct spot
    if (ortho[LEFT] < minLeft) {
      float diff = minLeft - ortho[LEFT];
//      bug("nudge right " + num(diff));
      tx = tx + diff;
      correct(d);
    } else if (ortho[RIGHT] > maxRight) {
      float diff = maxRight - ortho[RIGHT];
//      bug("nudge left " + num(diff));
      tx = tx + diff;
      correct(d);
    } else if (ortho[TOP] > maxTop) {
      float diff = maxTop - ortho[TOP];
//      bug("nudge down " + num(diff));
      ty = ty + diff;
      correct(d);
    } else if (ortho[BOTTOM] < minBot) {
      float diff = minBot - ortho[BOTTOM];
//      bug("nudge up " + num(diff));
      ty = ty + diff;
      correct(d);
    }
  }
}
