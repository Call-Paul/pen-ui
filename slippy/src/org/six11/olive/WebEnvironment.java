package org.six11.olive;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

import javax.swing.JApplet;

import org.six11.slippy.Environment;
import org.six11.slippy.SlippyUtils;
import org.six11.util.Debug;
import org.six11.util.io.HttpUtil;
import org.six11.util.io.StreamUtil;

import static org.six11.olive.server.SlippyBundler.MOD_INFO_PROPS;

/**
 * Implementation of Environment that is appropriate for use in applets. This assumes the IDE is
 * running in a web browser and has no special access to the local system. All source code is
 * available on the server (and can be viewed using a fancy web UI).
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class WebEnvironment extends Environment {

  List<String> classes = null;
  String module;
  String who;
  String version;
  String main;
  JApplet applet;

  public WebEnvironment(JApplet applet) {
    super();
    this.applet = applet;
    InputStream in = getClass().getResourceAsStream("/" + MOD_INFO_PROPS);
    Properties modProps = new Properties();
    try {
      modProps.load(in);
      this.module = modProps.getProperty("module");
      this.who = modProps.getProperty("who");
      this.version = modProps.getProperty("version");
      this.main = modProps.getProperty("main");
    } catch (IOException ex) {
      ex.printStackTrace();
      bug("Could not load /" + MOD_INFO_PROPS);
    }
  }

  @Override
  public String loadStringFromFile(String fullFileName) throws FileNotFoundException, IOException {
    String ret = "";
    try {
      InputStream in = getClass().getResourceAsStream(fullFileName);
      if (in == null) {
        String fqClass = SlippyUtils.fileStrToCodestStr(fullFileName);
        HttpUtil ht = new HttpUtil();
        StringBuilder buf = new StringBuilder();
        ht.setParam("module", module, buf);
        ht.setParam("version", version, buf);
        ht.setParam("who", who, buf);
        ht.setParam("fqClass", fqClass, buf);
        ht.setParam("mode", "download", buf);
        String u = applet.getCodeBase() + "code?" + buf.toString();
        ret = ht.downloadUrlToString(u);
      } else {
        ret = StreamUtil.inputStreamToString(in);
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    return ret;

  }

  /**
   * Returns a 'full' file name, which for a web environment is a slash, the load path, and the
   * fully qualified slippy file (with slashes and .slippy).
   */
  @Override
  public String getFullFileName(String className, String codesetStr) {
    return "/" + getLoadPathSlashed() + codesetStr.replace('.', '/') + "/" + className + ".slippy";
  }

  /**
   * Returns a 'full' file name, which is a slash, the load path, and the fully qualified slippy
   * file (with slashes and .slippy).
   * 
   * @param fqClass
   *          a fully-qualified class name, e.g. "org.six11.game.BadGuy".
   */
  @Override
  public String classNameToFileName(String fqClass) {
    return "/" + getLoadPathSlashed() + fqClass.replace('.', '/') + ".slippy";
  }

  /**
   * Save the given class along with the provided source code.
   */
  @Override
  public void save(String fqClassName, String programString) {
    HttpUtil w = new HttpUtil();
    StringBuilder buffer = new StringBuilder();
    w.setParam("module", module, buffer);
    w.setParam("who", who, buffer);
    w.setParam("fqClass", fqClassName, buffer);
    w.setParam("source", programString, buffer);
    try {
      w.post(applet.getCodeBase() + "save", buffer);
    } catch (IOException ex) {
      ex.printStackTrace();
      bug("Couldn't save!");
    }
  }

  private static void bug(String what) {
    Debug.out("WebEnvironment", what);
  }

  /**
   * In the web environment, a 'contents.txt' file is expected to be present in the jar. This file
   * has a list of fully qualified class names. So simply read it, one line per class. Cache the
   * result.
   */
  @Override
  public String[] listClasses() {
    if (classes == null) {
      classes = new ArrayList<String>();
      HttpUtil ht = new HttpUtil();
      String contentsUriFragment = module + "-" + version + "-" + who + "-contents.txt";
      try {
        String contents = ht.downloadUrlToString(applet.getCodeBase() + "jar/"
            + contentsUriFragment);
        StringTokenizer toks = new StringTokenizer(contents, "\n");
        while (toks.hasMoreTokens()) {
          classes.add(toks.nextToken());
        }
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    }
    return classes.toArray(new String[0]);
  }

  /**
   * Adds the given fully-qualified class name to our list. This is necessary in a web environment
   * because the initial list of available classes is included in the jar file, but we can't write
   * to that list directly. Instead we have to cache the list when we first need it, and henceforth
   * use that cached list for everything.
   */
  @Override
  public void addFile(String fqClassName) {
    if (classes == null) {
      listClasses();
    }
    classes.add(fqClassName);
  }

  /**
   * Uses the module info to issue a web request to make fqClassName the main executable for this
   * working copy.
   */
  @Override
  public void makeMain(String fqClassName) {
    HttpUtil w = new HttpUtil();
    StringBuilder buffer = new StringBuilder();
    w.setParam("mode", "main", buffer);
    w.setParam("module", module, buffer);
    w.setParam("who", who, buffer);
    w.setParam("fqClass", fqClassName, buffer);
    try {
      w.post(applet.getCodeBase() + "bundler", buffer);
      System.out.println(fqClassName + " is now the main class for module " + who + "@" + module);
    } catch (IOException ex) {
      ex.printStackTrace();
      bug("Failed to make " + fqClassName + " the main class for module " + who + "@" + module);
    }
  }

  public boolean isWeb() {
    return true;
  }

  public void quit() {
    applet.getAppletContext().showDocument(applet.getCodeBase());
  }
}
