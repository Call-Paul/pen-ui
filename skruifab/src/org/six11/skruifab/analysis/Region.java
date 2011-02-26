package org.six11.skruifab.analysis;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import org.six11.skruifab.DrawnThing;
import org.six11.util.Debug;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Pt;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Region implements Shape, DrawnThing {

  private static int ID_COUNTER = 1;
  List<Pt> boundary;
  Color color;
  GeneralPath gp;
  final int id;
  int stackOrder;
  DrawingBuffer db;

  /**
   * Builds a Region that already has an ID. This should probably only be called when restoring from
   * disk or similar.
   */
  public Region(int id, List<Pt> boundaryPoints, Color color) {
    this.id = id;
    this.boundary = boundaryPoints;
    this.color = color;
    this.gp = new GeneralPath();
    this.stackOrder = 0;
    gp.moveTo(boundary.get(0).getX(), boundary.get(0).getY());
    for (int i = 1; i < boundary.size(); i++) {
      Pt pt = boundary.get(i);
      gp.lineTo(pt.getX(), pt.getY());
    }
    ID_COUNTER = Math.max(id+1, ID_COUNTER);
  }

  /**
   * Build a new Region given the set of boundary points and color. The boundary points must loop
   * back on itself---the first and last points must be at the same spot.
   * 
   * @param boundaryPoints
   * @param color
   */
  public Region(List<Pt> boundaryPoints, Color color) {
    this(ID_COUNTER, boundaryPoints, color);
  }
  
  public int getId() {
    return id;
  }

  public Color getColor() {
    return color;
  }

  public List<Pt> getPoints() {
    return boundary;
  }
  
  public int undo() {
    bug("Undo!");
    return 0;
  }
  
  public void redo() {
    bug("Redo!");
  }
  
  public void snap() {
    bug("Snap!");
  }

  public static void bug(String what) {
    Debug.out("Region", what);
  }

  public boolean contains(Point2D p) {
    return gp.contains(p);
  }

  public boolean contains(Rectangle2D r) {
    return gp.contains(r);
  }

  public boolean contains(double x, double y) {
    return gp.contains(x, y);
  }

  public boolean contains(double x, double y, double w, double h) {
    return gp.contains(x, y, w, h);
  }

  public Rectangle getBounds() {
    return gp.getBounds();
  }

  public Rectangle2D getBounds2D() {
    return gp.getBounds2D();
  }

  public PathIterator getPathIterator(AffineTransform at) {
    return gp.getPathIterator(at);
  }

  public PathIterator getPathIterator(AffineTransform at, double flatness) {
    return gp.getPathIterator(at, flatness);
  }

  public boolean intersects(Rectangle2D r) {
    return gp.intersects(r);
  }

  public boolean intersects(double x, double y, double w, double h) {
    return intersects(x, y, w, h);
  }

  public void setStackOrder(int stackOrder) {
    this.stackOrder = stackOrder;
  }
  
  public int getStackOrder() {
    return this.stackOrder;
  }

  public DrawingBuffer getDrawingBuffer() {
    return db;
  }

  public void setDrawingBuffer(DrawingBuffer buf) {
    this.db = buf;
  }
}
