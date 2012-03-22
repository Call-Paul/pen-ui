package org.six11.sf;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

import java.awt.Dimension;

public class Camera {
  float zoom = 1.0f;
  float tx = 0f;
  float ty = 0f;

  public void zoomBy(Dimension d, float amt) {
    zoom = Math.max(1, zoom + amt);
    correct(d);
  }

  public void zoomTo(Dimension d, float amt, float dx, float dy) {
    tx = tx - dx;
    ty = ty - dy;
    zoom = Math.max(1, amt);
    correct(d);
  }

  public void zoomTo(Dimension d, float amt) {
    zoom = Math.max(1, amt);
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
    float[] ortho = getOrthoValues(d);
    if (ortho[0] < -0.01) {
      float dx = -ortho[0];
      tx = tx + dx;
      correct(d);
    } else if (ortho[1] > d.width) {
      float dx = d.width - ortho[1];
      tx = tx + dx;
      correct(d);
    } else if (ortho[2] > d.height) {
      float dy = d.height - ortho[2];
      ty = ty + dy;
      correct(d);
    } else if (ortho[3] < -0.01) {
      float dy = -ortho[3];
      ty = ty + dy;
      correct(d);
    }
  }
}
