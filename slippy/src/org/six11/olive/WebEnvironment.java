package org.six11.olive;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.six11.slippy.Environment;
import org.six11.util.Debug;
import org.six11.util.io.StreamUtil;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class WebEnvironment extends Environment {

  List<String> classes = null;

  @Override
  public String loadStringFromFile(String fullFileName) throws FileNotFoundException, IOException {
    bug("loadStringFromFile gets \"" + fullFileName + "\"");
    InputStream in = getClass().getResourceAsStream(fullFileName);
    String programSource = StreamUtil.inputStreamToString(in);
    bug("loadStringFromFile found program source of length " + programSource.length());
    return programSource;
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

  @Override
  public void save(String fqClassName, String programString) {
    bug("save is not implemented in WebEnvironment yet");
    // HttpUtil w;
    // try {
    // w = getWebClassLoader();
    // HttpURLConnection con = w.initPostConnection(getLoadPath() + "save/" + fqClassName);
    // String encodedString = URLEncoder.encode(programString, "UTF-8");
    // StreamUtil.writeStringToOutputStream(encodedString, con.getOutputStream());
    // con.getOutputStream().close();
    // con.getInputStream(); // not sure why but this is necessary for the above to work.
    // } catch (MalformedURLException ex) {
    // ex.printStackTrace();
    // } catch (IOException ex) {
    // ex.printStackTrace();
    // }
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
      bug("Loading /contents.txt...");
      InputStream in = getClass().getResourceAsStream("/contents.txt");
      if (in == null) {
        bug("Couldn't load contents.txt file.");
      } else {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        try {
          while (reader.ready()) {
            classes.add(reader.readLine());
          }
        } catch (IOException ex) {
          ex.printStackTrace();
        }
      }
    }
    return classes.toArray(new String[0]);
  }

  /**
   * Adds the given fully-qualified class name to our list.
   */
  @Override
  public void addFile(String fqClassName) {
    if (classes == null) {
      listClasses();
    }
    classes.add(fqClassName);
  }

}
