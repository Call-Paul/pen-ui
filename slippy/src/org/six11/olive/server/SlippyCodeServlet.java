package org.six11.olive.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.six11.slippy.SlippyHtmlSyntaxColorizer;
import org.six11.slippy.SlippyUtils;
import org.six11.util.Debug;
import org.six11.util.io.FileUtil;
import org.six11.util.io.StreamUtil;

/**
 * A servlet that lets you get lists of slippy files within a module, or actual slippy code. This
 * can be HTML, with full syntax colorization.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class SlippyCodeServlet extends SlippyServlet {

  /**
   * 
   */
  public SlippyCodeServlet() {
    super();
  }

  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
      IOException {
    String module = req.getParameter("module");
    String mode = req.getParameter("mode");
    String version = req.getParameter("version");
    String who = req.getParameter("who");
    String fqClass = req.getParameter("fqClass");
    boolean html = "true".equals(req.getParameter("html"));
    boolean frames = "true".equals(req.getParameter("frames"));

    if ("list".equals(mode)) {
      String pathFrag = SlippyBundler.getPathFragment(module, version, who);
      File moduleDir = new File(getModuleDir(), pathFrag);
      File mainFile = new File(moduleDir, SlippyBundler.MAIN_FILE);
      String mainClass = null;
      if (mainFile.exists() && mainFile.canRead()) {
        mainClass = FileUtil.loadStringFromFile(mainFile).trim();
      }
      String absLoadPath = moduleDir.getAbsolutePath();
      List<File> matches = FileUtil.searchForSuffix(".slippy", moduleDir);
      OutputStream out = resp.getOutputStream();
      resp.setContentType("text/html");
      for (File m : matches) {
        if (m.getAbsolutePath().startsWith(absLoadPath)) {
          String slippyClass = SlippyUtils.fileStrToCodestStr(m.getAbsolutePath().substring(
              absLoadPath.length()));
          outputFqClass(slippyClass, html, out, module, version, who, frames ? "codeFrame" : null,
              mainClass);
        }
      }
    } else if ("view".equals(mode)) {
      doView(resp, module, version, who, fqClass);
    } else if ("download".equals(mode)) {
      doDownload(resp, module, version, who, fqClass);
    }
  }

  private void doDownload(HttpServletResponse resp, String module, String version, String who,
      String fqClass) throws IOException {
    String pathFrag = SlippyBundler.getPathFragment(module, version, who);
    File moduleDir = new File(getModuleDir(), pathFrag);
    String slippyFileName = SlippyUtils.codesetStrToFileStr(fqClass);
    File slippyFile = new File(moduleDir, slippyFileName);
    try {
      resp.setContentType("text/plain");
      resp.setContentLength((int) slippyFile.length());
      StreamUtil.writeFileToOutputStream(slippyFile, resp.getOutputStream());
    } catch (Exception ex) {
      resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Couldn't retrieve slippy class " + fqClass
          + " for module " + module + "#" + version
          + ("working".equals(version) ? "(" + who + ")" : ""));
    }
  }

  private void doView(HttpServletResponse resp, String module, String version, String who,
      String fqClass) throws IOException {
    String pathFrag = SlippyBundler.getPathFragment(module, version, who);
    File moduleDir = new File(getModuleDir(), pathFrag);
    String slippyFileName = SlippyUtils.codesetStrToFileStr(fqClass);
    File slippyFile = new File(moduleDir, slippyFileName);

    boolean success = false;
    if (slippyFile.exists()) {
      SlippyHtmlSyntaxColorizer colorizer = new SlippyHtmlSyntaxColorizer();
      String colorFile = getServletContext().getInitParameter("colors");
      try {
        if (colorFile != null && colorFile.length() > 0) {
          colorizer.setStyleProperties(colorFile);
        }
        String htmlString = colorizer.walk(slippyFile.getAbsolutePath(), true);
        resp.setContentType("text/html");
        resp.setContentLength(htmlString.length());
        StreamUtil.writeStringToOutputStream(htmlString, resp.getOutputStream());
        bug("View: " + pathFrag + " " + slippyFileName + " (" + slippyFile.length() + " bytes)");
        success = true;
      } catch (FileNotFoundException ex) {
        bug("Warning: colorizer properties file does not exist: " + colorFile);
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }
    if (!success) {
      resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Couldn't render slippy class " + fqClass
          + " for module " + module + "#" + version
          + ("working".equals(version) ? "(" + who + ")" : ""));
    }
  }

  private void outputFqClass(String fqClass, boolean html, OutputStream out, String module,
      String version, String who, String targetFrame, String mainClass) throws IOException {
    if (html) {
      StringBuilder line = new StringBuilder();
      String contextPath = getServletContext().getContextPath();
      String servletPath = "code";
      line.append("<nobr><a href=\"" + contextPath + "/" + servletPath + "?mode=view&module="
          + module);
      line.append("&version=" + version);
      if (version.equals("working")) {
        line.append("&who=" + who);
      }
      line.append("&fqClass=" + fqClass);
      line.append("\"");
      if (targetFrame != null && targetFrame.length() > 0) {
        line.append(" target=\"" + targetFrame + "\"");
      }
      line.append(">" + fqClass + "</a>");
      if (mainClass != null && mainClass.equals(fqClass)) {
        line.append(" *main-class*");
      }
      line.append("</nobr><br />\n");
      StreamUtil.writeStringToOutputStream(line.toString(), out);
    } else {
      StreamUtil.writeStringToOutputStream(fqClass + "\n", out);
    }
  }

  private static void bug(String what) {
    Debug.out("SlippyCodeServlet", what);
  }
}
