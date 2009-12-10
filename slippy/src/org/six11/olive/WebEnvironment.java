package org.six11.olive;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URLEncoder;

import org.six11.slippy.Environment;
import org.six11.util.io.HttpUtil;
import org.six11.util.io.StreamUtil;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class WebEnvironment extends Environment {

  private HttpUtil web;

  @Override
  public String loadStringFromFile(String fullFileName) throws FileNotFoundException, IOException {
    HttpUtil wcl = getWebClassLoader();
    return wcl.downloadUrlToString(fullFileName);
  }

  private HttpUtil getWebClassLoader() throws MalformedURLException {
    if (web == null) {
      web = new HttpUtil();
    }
    return web;
  }

  @Override
  public String getFullFileName(String className, String codesetStr) {
    return getLoadPath() + "request/" + (codesetStr.length() > 0 ? codesetStr + "." : "")
        + className;
  }

  @Override
  public String classNameToFileName(String fqClass) {
    return getLoadPath() + "request/" + fqClass;
  }

  @Override
  public void save(String fqClassName, String programString) {
    HttpUtil w;
    try {
      w = getWebClassLoader();
      HttpURLConnection con = w.initPostConnection(getLoadPath() + "save/" + fqClassName);
      String encodedString = URLEncoder.encode(programString, "UTF-8");
      StreamUtil.writeStringToOutputStream(encodedString, con.getOutputStream());
      con.getOutputStream().close();
      con.getInputStream(); // not sure why but this is necessary for the above to work.
    } catch (MalformedURLException ex) {
      ex.printStackTrace();
    } catch (IOException ex) {
      ex.printStackTrace();
    }
  }

}
