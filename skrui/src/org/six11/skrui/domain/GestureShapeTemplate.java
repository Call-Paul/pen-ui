package org.six11.skrui.domain;

import java.util.List;

import org.six11.skrui.script.Neanderthal;
import org.six11.skrui.script.Primitive;

/**
 * 
 *
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public abstract class GestureShapeTemplate extends ShapeTemplate {

  public GestureShapeTemplate(Domain domain, String name) {
    super(domain, name);
  }

  public abstract void trigger(Neanderthal data, Shape s, List<Primitive> matches);
  
}
