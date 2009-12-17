package org.six11.olive;

import java.io.File;
import java.io.IOException;
import java.util.StringTokenizer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.six11.util.Debug;
import org.six11.util.io.FileUtil;
import org.six11.util.io.StreamUtil;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class JarVendor extends HttpServlet {

  public final static String CACHE_DIR_PARAM = "jarVendorCacheDir"; // must agree with web.xml
  public final static String MODULE_DIR_PARAM = "moduleDir"; // must agree with web.xml
  public final static String ORIGINAL_JAR_PARAM = "originalJar"; // must agree with web.xml

  // protected final static SessionFactory sessionFactory;
  // static {
  // sessionFactory = new AnnotationConfiguration().configure().buildSessionFactory();
  // }

  public JarVendor() {
    super();
  }

  /**
   * Gives the root directory for cached jar files. Wonky staticness because SlippyBundlerServlet
   * needs this as well, and I didn't want to duplicate code.
   */
  protected static File getCacheDir(HttpServlet serv) {
    String dir = serv.getInitParameter(CACHE_DIR_PARAM);
    return new File(dir);
  }

  /**
   * Gives the root directory for slippy source files. Wonky staticness because SlippyBundlerServlet
   * needs this as well, and I didn't want to duplicate code.
   */
  protected static File getModuleDir(HttpServlet serv) {
    String dir = serv.getInitParameter(MODULE_DIR_PARAM);
    return new File(dir);
  }

  /**
   * Gives the original jar files that contains all the Java classes for Olive and Slippy to work.
   * Wonky staticness because SlippyBundlerServlet needs this as well, and I didn't want to
   * duplicate code.
   */
  protected static File getOriginalJarFile(HttpServlet serv) {
    String f = serv.getServletContext().getRealPath("/")
        + serv.getInitParameter(ORIGINAL_JAR_PARAM);
    bug("original jar file: " + f);
    return new File(f);
  }

  private boolean ensure(File dir) {
    boolean ret = false;
    if (!dir.exists()) {
      boolean res = dir.mkdirs() && dir.canRead() && dir.canWrite();
      if (res) {
        bug("Successfully made path '" + dir.getAbsolutePath() + "'.");
        ret = true;
      } else {
        bug(" ** Error creating directory '" + dir.getAbsolutePath() + "'");
        bug(" **   I can't create this directory, or read or write files there.");
        bug(" **   This is probably a problem with user permissions. Check which user your web ");
        bug(" **   application is running as, and ensure that user can create directories up to ");
        bug(" **   and including the one specified in the web.xml file. Bailing.");
      }
    } else {
      ret = true;
    }
    return ret;
  }

  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
      IOException {
    // Get the base directory for cached jars, and create it if necessary.
    File cacheDir = getCacheDir(this);
    File moduleDir = getModuleDir(this);
    if (!ensure(cacheDir) || !ensure(moduleDir)) {
      bug("One of the directories is whack");
      return;
    }

    // Parse the file string.
    String requestedFile = req.getPathInfo().substring(1);
    bug("Requested file: " + requestedFile);
    requestedFile = requestedFile.replace(".jar", "");
    StringTokenizer tokens = new StringTokenizer(requestedFile, "-", false);
    String module = tokens.nextToken();
    String version = tokens.nextToken();
    String who = "";
    if (tokens.hasMoreTokens()) {
      who = tokens.nextToken();
    }

    // Conjure the jar to deliver, either from the cache, or by creating/caching it.
    String targetJarName = SlippyBundler.makeVersionedJarName(module, version, who);
    File originalJarFile = getOriginalJarFile(this);
    FileUtil.complainIfNotWriteable(originalJarFile);
    File jarFile = new File(cacheDir, targetJarName);
    if (!jarFile.exists()) {
      SlippyBundler bundler = new SlippyBundler(moduleDir);
      File bundle = bundler.bundle(module, version, who, originalJarFile);
      bundle.renameTo(jarFile);
      bug("Produced bundle: " + bundle.getAbsolutePath());
    }

    // Write the file to output using the correct content type.
    String contentType = "application/java-archive";
    resp.setContentType(contentType);
    StreamUtil.writeFileToOutputStream(jarFile, resp.getOutputStream());
    resp.getOutputStream().close();
  }

  private static void bug(String what) {
    Debug.out("JarVendor", what);
  }

}
