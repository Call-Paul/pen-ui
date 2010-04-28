package org.six11.skrui;

import java.awt.Cursor;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.six11.skrui.data.Journal;
import org.six11.util.Debug;
import org.six11.util.args.Arguments;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public abstract class SkruiScript {
  protected Main main;

  public static SkruiScript load(Class<? extends SkruiScript> scriptClazz, Main m)
      throws InstantiationException, IllegalAccessException {
    SkruiScript script = scriptClazz.newInstance();
    script.setMain(m);
    m.addParameters(script.initializeParameters(m.getArguments()));
    script.initialize();
    return script;
  }

  private static void bug(String what) {
    Debug.out("DrawingScript", what);
  }

  /**
   * Zero-arg constructor to make reflection easy.
   */
  protected SkruiScript() {

  }

  public Main getMain() {
    return main;
  }

  protected BoundedParameter getParam(String key) {
    return main.getParam(key);
  }

  public static Arguments getArgumentSpec() {
    return new Arguments();
  }

  public static Map<String, BoundedParameter> getDefaultParameters() {
    return new HashMap<String, BoundedParameter>();
  }

  public abstract Map<String, BoundedParameter> initializeParameters(Arguments args);

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

  public JSONObject getSaveData(@SuppressWarnings("unused") Journal jnl) throws JSONException {
    bug("Script '" + getClass() + "' does not implement getSaveData()");
    return null;
  }

  public void openSaveData(@SuppressWarnings("unused") Journal jnl,
      @SuppressWarnings("unused") JSONObject job) throws JSONException {
    bug("Script '" + getClass() + "' does not implement openSaveData(JSONObject)");
  }

  public Cursor getCursor() {
    return null;
  }

}
