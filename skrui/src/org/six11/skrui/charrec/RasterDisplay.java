package org.six11.skrui.charrec;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

import javax.swing.JPanel;

import org.six11.util.gui.Components;
import org.six11.util.pen.Functions;

public class RasterDisplay extends JPanel {

  double[] raster;
  int n; // the size of a row and column in the raster. (rasters are square)

  public RasterDisplay() {
    setPreferredSize(new Dimension(128, 128));
  }

  /**
   * Populate the raster with an input array. The input array's length MUST be a perfect square: for
   * a 4x4 raster, the length is 16; for a 8x8 the length is 64, and so on. The data in 'raster' is
   * arranged in row major order. In other words, raster[0] is the top left pixel, raster[1] is to
   * its right, and so on, until the end of the row. Each row is sqrt(raster.length) units long.
   */
  public void setData(double[] raster) {
    double nDouble = Math.sqrt(raster.length);
    if (!Functions.eq(nDouble, Math.rint(nDouble), Functions.EQ_TOL)) {
      err("raster input has " + raster.length + " elements but it should be a perfect square.");
    }
    this.n = (int) Math.rint(nDouble);
    this.raster = raster;
    setPreferredSize(new Dimension(n * 4, n* 4));
    repaint();
  }

  private static void err(String what) {
    System.err.println("RasterDisplay: ** Warning ** " + what);
  }

  protected void paintComponent(Graphics g1) {
    if (n > 0 && raster != null) {
      Graphics2D g = (Graphics2D) g1;
      Components.antialias(g);
      g.setColor(Color.WHITE);
      g.fill(getVisibleRect());
      double totW = getBounds().getWidth();
      double totH = getBounds().getHeight();
      double w = totW / n;
      double h = totH / n;
      Rectangle2D rec = new Rectangle2D.Double(0, 0, w, h);
      for (int i = 0; i < n * n; i++) {
        double x = w * (i % n);
        double y = h * (i / n);
        rec.setFrame(x, y, w, h);
        g.setColor(getMonochrome(raster[i]));
        g.fill(rec);
      }
    }
  }

  private Color getMonochrome(double v) {
    if (v < 0 || v > 1) {
      err("Found cell value out of range: " + v);
    }
    float f = (float) v;
    return new Color(f, f, f);
  }
}
