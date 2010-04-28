package org.six11.skrui.shape;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import org.six11.util.pen.Pt;

/**
 * 
 *
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Region implements Shape {

  List<Pt> boundary;
  Color color;
  GeneralPath gp;
  
  public Region(List<Pt> boundaryPoints, Color color) {
    this.boundary = boundaryPoints;
    this.color = color;
    this.gp = new GeneralPath();
    gp.moveTo(boundary.get(0).getX(), boundary.get(0).getY());
    for (int i=1; i < boundary.size(); i++) {
      Pt pt = boundary.get(i);
      gp.lineTo(pt.getX(), pt.getY());
    }
  }
  
  public Color getColor() {
    return color;
  }
  
  public List<Pt> getPoints() {
    return boundary;
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
    return gp.contains(x,y,w,h);
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
    return intersects(x,y,w,h);
  }

}
