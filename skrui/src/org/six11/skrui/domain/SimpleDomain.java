package org.six11.skrui.domain;

import org.six11.skrui.script.Neanderthal;

/**
 * 
 *
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class SimpleDomain extends Domain {

  
  public SimpleDomain(String name, Neanderthal data) {
    super(name, data);
    templates.add(new TriangleTemplate(this));
  }
}
