package org.six11.skrui;

import java.util.HashMap;
import java.util.Map;

import org.six11.util.Debug;
import org.six11.util.args.Arguments;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public abstract class DrawingScript {
  protected Main main;

  public static DrawingScript load(Class<? extends DrawingScript> scriptClazz, Main m)
      throws InstantiationException, IllegalAccessException {
    DrawingScript script = scriptClazz.newInstance();
    script.setMain(m);
    script.initialize();
    return script;
  }

  @SuppressWarnings("unused")
  private static void bug(String what) {
    Debug.out("DrawingScript", what);
  }

  /**
   * Zero-arg constructor to make reflection easy.
   */
  protected DrawingScript() {

  }

  public static Arguments getArgumentSpec() {
    return new Arguments();
  }

  public static Map<String, BoundedParameter> getDefaultParameters() {
    return new HashMap<String, BoundedParameter>();
  }

  public abstract Map<String, BoundedParameter> getParameters();

  public static Map<String, BoundedParameter> copyParameters(Map<String, BoundedParameter> orig) {
    Map<String, BoundedParameter> ret = new HashMap<String, BoundedParameter>();
    for (String k : orig.keySet()) {
      ret.put(k, orig.get(k).copy());
    }
    return ret;
  }

  /**
   * Called from the factory 'load' method to set the Main instance.
   */
  public final void setMain(Main m) {
    this.main = m;
  }

  /**
   * Called once when the script is loaded. To parse arguments, look at main.getArguments().
   */
  public abstract void initialize();

}
