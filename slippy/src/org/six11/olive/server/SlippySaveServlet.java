package org.six11.olive.server;

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.six11.olive.DiskEnvironment;
import org.six11.util.Debug;
import org.six11.util.io.FileUtil;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class SlippySaveServlet extends SlippyServlet {
  
//  public final static String CACHE_DIR_PARAM = "jarVendorCacheDir"; // must agree with web.xml
//  public final static String MODULE_DIR_PARAM = "moduleDir"; // must agree with web.xml

  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
      IOException {
    String module = req.getParameter("module");
    String who = req.getParameter("who");
    String src = req.getParameter("source");
    String fqClass = req.getParameter("fqClass");    
    File baseDir = getModuleDir();
    String lowerPath = SlippyBundler.getPathFragment(module, "working", who);
    File path = new File(baseDir, lowerPath);
    FileUtil.complainIfNotWriteable(path);
    DiskEnvironment env = new DiskEnvironment(path.getAbsolutePath());
    env.save(fqClass, src);
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.getOutputStream().close();
    bug("Saved " + who + "'s " + module + "/" + fqClass + " (" + src.length() + " bytes)");
  }
  
//  protected File getCacheDir() {
//    String dir = getServletContext().getInitParameter(CACHE_DIR_PARAM);
//    return new File(dir);
//  }
//
//  protected File getModuleDir() {
//    String dir = getServletContext().getInitParameter(MODULE_DIR_PARAM);
//    return new File(dir);
//  }
  
  private static void bug(String what) {
    Debug.out("SlippySaveServlet", what);
  }
}
