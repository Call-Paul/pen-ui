package org.six11.olive;

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.six11.util.Debug;
import org.six11.util.io.StreamUtil;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class SlippyBundlerServlet extends HttpServlet {

  public final static String OLIVE_CODE_PARAM = "oliveSlippyCode"; // must agree with web.xml

  public SlippyBundlerServlet() {
    super();
  }

  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
      IOException {
    String mode = req.getParameter("mode");
    bug("mode: " + mode);
    if (mode.equals("create")) {
      create(req, resp);
    }
  }

  protected void forwardTo(String servletPath, HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    getServletContext().getRequestDispatcher(servletPath).forward(req, resp);
  }

  private void create(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    try {
      String module = req.getParameter("module");
      File moduleDir = JarVendor.getModuleDir(this);
      SlippyBundler bundler = new SlippyBundler(moduleDir);
      File modDir = bundler.create(module, getInitParameter(OLIVE_CODE_PARAM));
      bug("Made a new module directory at: " + modDir.getAbsolutePath());
      req.setAttribute("msg", "Made a new module directory at: " + modDir.getAbsolutePath());
      forwardTo("/manage.jsp", req, resp);
      // resp.sendRedirect(getServletContext().getContextPath() + "/manage.jsp");
    } catch (Exception ex) {
      StreamUtil.writeStringToOutputStream(ex.getMessage(), resp.getOutputStream());
    }
  }

  private static void bug(String what) {
    Debug.out("SlippyBundlerServlet", what);
  }

}
