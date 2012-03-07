package org.six11.sf;

import javax.media.opengl.GL2;
import javax.media.opengl.glu.GLUtessellatorCallbackAdapter;

public class TessCallback extends GLUtessellatorCallbackAdapter {

  private GL2 gl;
  
  public TessCallback(GL2 gl) {
    this.gl = gl;
  }

  public void begin(int type) {
    gl.glBegin(type);
  }

  public void vertex(Object vertexData) {
    double[] coords = (double[]) vertexData;
    gl.glVertex3dv(coords, 0);
  }

  public void end() {
    gl.glEnd();
  }

}
