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
 * A simple servlet that accepts POST requests that contain source code. It saves source files to
 * disk, creating them if necessary.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class SlippySaveServlet extends SlippyServlet {

  /**
   * This demands URL-encoded parameters: 'module', 'who', 'source', and 'fqClass'. The 'source'
   * param is the complete source code for a file; 'fqClass' specifies which file that is. It
   * doesn't check to ensure the source code is actually the same as the fqClass provided, so be
   * smart.
   */
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

  private static void bug(String what) {
    Debug.out("SlippySaveServlet", what);
  }
}
