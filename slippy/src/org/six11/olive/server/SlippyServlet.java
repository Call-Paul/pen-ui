package org.six11.olive.server;

import java.io.File;

import javax.servlet.http.HttpServlet;

/**
 * 
 *
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class SlippyServlet extends HttpServlet {

  public final static String CACHE_DIR_PARAM = "jarVendorCacheDir"; // must agree with web.xml
  public final static String MODULE_DIR_PARAM = "moduleDir"; // must agree with web.xml
  public final static String ORIGINAL_JAR_PARAM = "originalJar"; // must agree with web.xml
  public final static String OLIVE_CODE_PARAM = "oliveSlippyCode"; // must agree with web.xml


  public SlippyServlet() {
    super();
  }
  
  protected File getCacheDir() {
    String dir = getServletContext().getInitParameter(CACHE_DIR_PARAM);
    return new File(dir);
  }

  protected File getModuleDir() {
    String dir = getServletContext().getInitParameter(MODULE_DIR_PARAM);
    return new File(dir);
  }

  protected File getOriginalJarFile() {
    String f = getServletContext().getRealPath("/")
        + getServletContext().getInitParameter(ORIGINAL_JAR_PARAM);
    return new File(f);
  }

}
