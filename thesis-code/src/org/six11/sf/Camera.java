package org.six11.sf;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

import java.awt.Dimension;

public class Camera {
  float zoom = 1.0f;
  float tx = 0f;
  float ty = 0f;

  public void zoom(float amt) {
    zoom = Math.max(1, zoom + amt);
  }

  public float getZoom() {
    return zoom;
  }

  public float[] getOrthoValues(Dimension d) {
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
    float[] ret = new float[] { vLeft, vRight, vTop, vBot };
    return ret;
  }

  public float getPanX() {
    return tx;
  }
  
  public float getPanY() {
    return ty;
  }

  public float[] unproject(float x, float y, Dimension d) {
//    float x = wcoord[0];
//    float y = wcoord[1];
//    float sx = x / d.width;
//    float sy = y / d.height;
    float[] ortho = getOrthoValues(d);
    float orthoW = ortho[1] - ortho[0];
    float orthoH = ortho[2] - ortho[3];
    float retX = ortho[0] + (x * orthoW);
    float retY = ortho[3] + (y * orthoH);
    return new float[] { 3, retY };
  }
}
