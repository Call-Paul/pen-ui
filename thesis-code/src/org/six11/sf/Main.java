package org.six11.sf;

import static org.six11.util.Debug.bug;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.six11.util.gui.ApplicationFrame;

/**
 * 
 **/
public class Main {

  static Map<String, Object> vars;

  public static void main(String[] args) {
    initVars();
    ApplicationFrame af = new ApplicationFrame("SkruiFab (build " + varStr("dateString") + " at "
        + varStr("timeString") + ")");
    af.setSize(600, 400);
    
    af.center();
    af.setVisible(true);
  }
  
  private static void initVars() {
    vars = new HashMap<String, Object>();
    vars.put("date", new Date());
    vars.put("dateString", new SimpleDateFormat("MMM dd yyyy").format(varDate("date")));
    vars.put("timeString", new SimpleDateFormat("K:mm a").format(varDate("date")));
  }

  public static String varStr(String key) {
    return (String) vars.get(key);
  }

  public static Date varDate(String key) {
    return (Date) vars.get(key);
  }
}
