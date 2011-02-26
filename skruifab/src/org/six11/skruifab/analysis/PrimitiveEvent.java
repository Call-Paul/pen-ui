package org.six11.skruifab.analysis;

import java.util.EventObject;
import java.util.SortedSet;

/**
 * 
 *
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class PrimitiveEvent extends EventObject {

  private SortedSet<Primitive> prims;

  /**
   * @param source
   */
  public PrimitiveEvent(Object source, SortedSet<Primitive> prims) {
    super(source);
    this.prims = prims;
  }

  public SortedSet<Primitive> getPrims() {
    return prims;
  }
  
  

}
