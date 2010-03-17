package org.six11.skrui.domain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.six11.skrui.script.Neanderthal;

/**
 * An abstract domain, to be implemented by subclasses. This guarantees that all domains have easy
 * access to the data source (the Neanderthal primitive ink analyzer).
 * 
 * Subclasses simply create and add a bunch of templates. Then the Neanderthal has to load the
 * (subclass) domain.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public abstract class Domain {

  String name;
  Set<ShapeTemplate> templates;
  Map<String, ShapeRenderer> renderers;
  Neanderthal data;

  public Domain(String name, Neanderthal data) {
    this.name = name;
    this.data = data;
    this.templates = new HashSet<ShapeTemplate>();
    this.renderers = new HashMap<String, ShapeRenderer>();
  }

  public void addTemplate(ShapeTemplate t) {
    templates.add(t);
  }

  public Set<ShapeTemplate> getTemplates() {
    return templates;
  }
  
  public void addRenderer(String which, ShapeRenderer renderer) {
    renderers.put(which, renderer);
  }
  
  public ShapeRenderer getRenderer(String which) {
    return renderers.get(which);
  }
  
  public Neanderthal getData() {
    return data;
  }
  
  public String getName() {
    return name;
  }
  
  public String toString() {
    return "Domain '" + name + "' (" + templates.size() + " shape templates)";
  }
}
