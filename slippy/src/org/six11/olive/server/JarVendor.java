package org.six11.olive.server;

import java.io.File;
import java.io.IOException;
import java.util.StringTokenizer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.six11.olive.server.SlippyBundler.Version;
import org.six11.util.Debug;
import org.six11.util.io.FileUtil;
import org.six11.util.io.StreamUtil;

/**
 * This builds a jar file that contains all the slippy code necessary to run (or edit) a module.
 * This includes the org.six11.olive code as well as any other code written by programmers.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class JarVendor extends SlippyServlet {

  public JarVendor() {
    super();
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
    File cacheDir = getCacheDir();
    File moduleDir = getModuleDir();
    if (!ensure(cacheDir) || !ensure(moduleDir)) {
      bug("One of the directories is whack");
      return;
    }

    // Parse the file string.
    String requestedFile = req.getPathInfo().substring(1);
    bug("Requested file: " + requestedFile);
    if (requestedFile.endsWith(".jar")) {
      giveJar(requestedFile, resp);
    } else if (requestedFile.endsWith("-contents.txt")) {
      giveContents(requestedFile, resp);
    }
  }
  
  /**
   * @param requestedFile is in the format: someModule-working-billy-contents.txt
   */
  private void giveContents(String requestedFile, HttpServletResponse resp) throws IOException {
    File moduleDir = getModuleDir();

    requestedFile = requestedFile.replace("-contents.txt", "");
    StringTokenizer tokens = new StringTokenizer(requestedFile, "-", false);
    String module = tokens.nextToken();
    String version = tokens.nextToken();
    String who = tokens.nextToken();
    if (tokens.hasMoreTokens()) {
      who = tokens.nextToken();
    }
    bug("Giving contents for " + module + " " + version + " " + who);
    SlippyBundler bundler = new SlippyBundler(moduleDir);
    String contents = bundler.getContentsList(module, version, who);
    
    // Write contents to output using the correct content type.
    String contentType = "text/plain";
    resp.setContentType(contentType);
    StreamUtil.writeStringToOutputStream(contents, resp.getOutputStream());
    bug("Writing contents list to web request:\n" + contents);
    resp.getOutputStream().close();
  }

  private void giveJar(String requestedFile, HttpServletResponse resp) throws IOException {
    File cacheDir = getCacheDir();
    File moduleDir = getModuleDir();

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
    File jarFile = new File(cacheDir, targetJarName);
    if (version.equals("working")) {
      bug("The user requested a working jar. Building it from disk without looking at the cache.");
    } else {
      bug("Ideally I would like to return " + jarFile.getAbsolutePath() + ". Does it exist? "
          + jarFile.exists());
    }
    if (version.equals("working") || !jarFile.exists()) {
      try {
        File originalJarFile = getOriginalJarFile();
        FileUtil.complainIfNotWriteable(originalJarFile);

        SlippyBundler bundler = new SlippyBundler(moduleDir);
        String lowerPath = SlippyBundler.getPathFragment(module, version, who);
        File path = new File(moduleDir, lowerPath);
        if (!path.exists() && version.equals("working")) {
          bug("Making working directory for user " + who + " based on most recent version.");
          // The directory for the working code doesn't exist, so create it.
          Version mostRecent = bundler.getMostRecentVersion(module);
          bundler.makeWorking(module, "" + mostRecent.version, who);
          bug("Made working directory for " + path.getAbsolutePath());
        } else if (!path.exists()) {
          bug("Making working directory for user " + who + " for version " + version);
          bundler.makeWorking(module, version, who);
          bug("Made working directory for " + path.getAbsolutePath());
        }
        File bundle = bundler.bundle(module, version, who, originalJarFile);
        bundle.renameTo(jarFile);
        bug("Produced bundle: " + bundle.getAbsolutePath());
      } catch (Exception ex) {
        bug("Got exception in there.");
        ex.printStackTrace();
      }
    } else {
      bug("Yes!");
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
