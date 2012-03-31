package org.six11.sf;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.swing.JOptionPane;

import org.six11.util.Debug;
import static org.six11.util.Debug.bug;
import org.six11.util.args.Arguments;

/**
 * 
 **/
public class Main {

  private static final int NUM_NAG_TIMES = 10;
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
    Preferences prefs = Preferences.userNodeForPackage(Main.class);
    boolean videoWatched = prefs.getBoolean("videoWatched", false);
    int numUses = prefs.getInt("numUses", 0);
    bug("Number of uses: " + numUses);
    bug("Video watched: " + videoWatched);
    prefs.putInt("numUses", numUses + 1);
    String firstTime = "" //
        + "It looks like this is your first time using Sketch It, Make It (SIMI). This is designed\n" //
        + "to be used with a tablet (e.g. a Wacom or similar). If you use this with a mouse, or\n" //
        + "(horrors) a trackpad, you will hate it.\n"; //
    String videoPlease = "" //
        + "There is a lovely training video on Vimeo. This software is probably unlike anything\n" //
        + "you have used before. You should consider checking it out.\n";
    String numRemainingShows = "" //
        + "Watch the video or endure this dialog box "
        + (NUM_NAG_TIMES - numUses)
        + " more times before we stop bugging you.";
    if (!videoWatched && numUses <= NUM_NAG_TIMES) {
      Object[] options = {
          "Show me the training video >>", "Maybe later."
      };
      String msg = "";
      if (numUses == 0) {
        msg = firstTime + "\n" + videoPlease + "\n";
      } else {
        msg = videoPlease + "\n" + numRemainingShows + "\n";
      }
      int result = JOptionPane.showOptionDialog(ed.getApplicationFrame(), msg,
          "Show training video?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, //do not use a custom Icon
          options, //the titles of buttons
          options[0]); //default button title
      if (result == 0) {
        try {
          prefs.putBoolean("videoWatched", true);
          Desktop.getDesktop().browse(new URI("https://vimeo.com/39444494"));
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (URISyntaxException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    }
    boolean autoLog = prefs.getBoolean("autoLog", true);
    if (autoLog) {
      ed.getModel().setLogRecognitionEvents(true);
    }
  }

  public String varStr(String key) {
    return (String) vars.get(key);
  }

  public Date varDate(String key) {
    return (Date) vars.get(key);
  }
}
