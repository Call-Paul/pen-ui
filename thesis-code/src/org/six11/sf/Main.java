package org.six11.sf;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.six11.util.Debug;
import org.six11.util.args.Arguments;

/**
 * 
 **/
public class Main {

  /**
   * holds general info about the application (e.g. when it was run). Useful for debugging and
   * documentation.
   */
  Map<String, Object> vars;

  /**
   * Creates a new SkruiFab application and shows a single editor window.
   */
  public static void main(String[] in) {
    Arguments args = new Arguments(in);
    if (args.hasFlag("no-debug-color")) {
      Debug.useColor = false;
    }
    if (args.hasFlag("no-debug-time")) {
      Debug.useTime = false;
    }
    new Main().go();
  }

  /**
   * Initialize variables, but does not show any GUI elements.
   */
  public Main() {
    vars = new HashMap<String, Object>();
    vars.put("date", new Date());
    vars.put("dateString", new SimpleDateFormat("MMM dd yyyy").format(varDate("date")));
    vars.put("timeString", new SimpleDateFormat("K:mm a").format(varDate("date")));
    vars.put("editors", new ArrayList<SkruiFabEditor>());
  }

  /**
   * Creates and shows a single new editor window.
   */
  @SuppressWarnings("unchecked")
  public void go() {
    SkruiFabEditor ed = new SkruiFabEditor(this);
    List<SkruiFabEditor> editors = (List<SkruiFabEditor>) vars.get("editors");
    editors.add(ed);
  }

  public String varStr(String key) {
    return (String) vars.get(key);
  }

  public Date varDate(String key) {
    return (Date) vars.get(key);
  }
}
