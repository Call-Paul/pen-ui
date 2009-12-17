package org.six11.olive;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import org.six11.slippy.SlippyUtils;
import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.io.FileUtil;
import org.six11.util.io.StreamUtil;
import org.six11.util.io.SuffixFileFilter;

/**
 * This is a tool to create a jar file that contains a 'module' of slippy code mixed with Java
 * classes for editing them. Say you have a Hello World module named 'hello'. Your 'main' file is in
 * org.foo.hello.Hello, but it depends on the org.six11.olive slippy classes.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class SlippyBundler {

  /**
   * This is for building a jar file from the command line, also good for testing.
   */
  public static void main(String[] in) {
    if (in.length == 0) {
      System.out.println("Usage: SlippyBundler [command] [args...]");
      System.out.println("Possible commands are:");
      System.out.println("  bundle");
      System.out.println("  create");
      System.out.println("  deploy");
      System.out.println("  delete");
      System.out.println("  recent");
      System.out.println("  work");
      System.out.println("Generally you can get help like this: SlippyBundler [command] --help");
      System.exit(0);
    }
    String[] rest = new String[in.length - 1];
    for (int i = 1; i < in.length; i++) {
      rest[i - 1] = in[i];
    }
    if (in[0].equals("bundle")) {
      Bundle.main(rest);
    } else if (in[0].equals("create")) {
      Create.main(rest);
    } else if (in[0].equals("deploy")) {
      DeployVersion.main(rest);
    } else if (in[0].equals("delete")) {
      DeleteWorking.main(rest);
    } else if (in[0].equals("recent")) {
      GetRecent.main(rest);
    } else if (in[0].equals("work")) {
      MakeWorking.main(rest);
    } else {
      System.out.println("Where'd you go to school? Run without args to see help.");
    }
  }

  public static class Bundle {
    public static void main(String[] in) {
      Arguments args = new Arguments();
      args.setProgramName("bundle");
      args.setDocumentationProgram("Creates a Jar file that includes a particular "
          + "versioned Slippy module");
      args.addFlag("module", ArgType.ARG_REQUIRED, ValueType.VALUE_REQUIRED,
          "Specify the name of a module");
      args.addFlag("version", ArgType.ARG_REQUIRED, ValueType.VALUE_REQUIRED,
          "Specify the version number (positive integer), or 'working' for a user's working code.");
      args.addFlag("who", ArgType.ARG_REQUIRED, ValueType.VALUE_REQUIRED,
          "A user name to distinguish among many working copies");
      args.addFlag("baseDir", ArgType.ARG_REQUIRED, ValueType.VALUE_REQUIRED,
          "Where the root directory of all slippy code can be found.");
      args.addFlag("jar", ArgType.ARG_REQUIRED, ValueType.VALUE_REQUIRED,
          "The jar file to add files to.");

      args.addFlag("help", ArgType.ARG_OPTIONAL, ValueType.VALUE_IGNORED, "Shows useful help");
      args.parseArguments(in);
      if (args.hasFlag("help")) {
        System.out.println(args.getDocumentation());
        System.exit(0);
      }
      try {
        args.validate();
      } catch (Exception ex) {
        System.out.println(args.getUsage());
        System.out.println("To create a new module, execute org.six11.olive.SlippyBunder$Create");
        System.exit(0);
      }
      try {
        SlippyBundler bundler = new SlippyBundler(args.getValue("baseDir"));
        File target = bundler.getVersionedJar(args.getValue("module"), args.getValue("version"),
            args.getValue("who"));
        if (target != null && target.exists()) {
          System.out.println(target.getAbsolutePath() + " exists (" + target.length() + " bytes)");
        } else {
          File originalJar = new File(args.getValue("jar"));
          FileUtil.complainIfNotReadable(originalJar);
          File jarFile = bundler.bundle(args.getValue("module"), args.getValue("version"), args
              .getValue("who"), originalJar);
          System.out.println("I now have a nice jar file in: '" + jarFile.getAbsolutePath() + "'");
        }
      } catch (FileNotFoundException ex) {
        System.out.println(ex.getMessage());
      } catch (IOException ex) {
        System.out.println(ex.getMessage());
      }
    }
  }

  public static class Create {
    public static void main(String[] in) {
      Arguments args = new Arguments();
      args.setProgramName("create");
      args.setDocumentationProgram("Creates a new Slippy module beginning with version 1.");

      args.addFlag("baseDir", ArgType.ARG_REQUIRED, ValueType.VALUE_REQUIRED,
          "Where the root directory of the created module should be placed.");
      args.addFlag("oliveCode", ArgType.ARG_REQUIRED, ValueType.VALUE_REQUIRED,
          "Location of the org.six11.olive Slippy classes.");
      args.addFlag("module", ArgType.ARG_REQUIRED, ValueType.VALUE_REQUIRED,
          "Name of the module to create.");
      args.addFlag("help", ArgType.ARG_OPTIONAL, ValueType.VALUE_IGNORED, "Shows useful help");
      args.parseArguments(in);
      if (args.hasFlag("help")) {
        System.out.println(args.getDocumentation());
        System.exit(0);
      }
      try {
        args.validate();
      } catch (Exception ex) {
        System.out.println(args.getUsage());
        System.exit(0);
      }

      try {
        SlippyBundler bundler = new SlippyBundler(args.getValue("baseDir"));
        bundler.create(args.getValue("module"), args.getValue("oliveCode"));
      } catch (IOException ex) {
        System.out.println(ex.getMessage());
      }
    }
  }

  public static class MakeWorking {
    public static void main(String[] in) {
      Arguments args = new Arguments();
      args.setProgramName("work");
      args.setDocumentationProgram("Makes a working directory for a given module/version. "
          + "This is specific to a user.");

      args.addFlag("baseDir", ArgType.ARG_REQUIRED, ValueType.VALUE_REQUIRED,
          "Where the root directory of the module should be placed.");
      args.addFlag("module", ArgType.ARG_REQUIRED, ValueType.VALUE_REQUIRED,
          "Name of the module to create.");
      args.addFlag("version", ArgType.ARG_REQUIRED, ValueType.VALUE_REQUIRED,
          "Specify the version number (positive integer).");
      args.addFlag("who", ArgType.ARG_REQUIRED, ValueType.VALUE_REQUIRED,
          "A user name to distinguish among many working copies");

      args.addFlag("help", ArgType.ARG_OPTIONAL, ValueType.VALUE_IGNORED, "Shows useful help");
      args.parseArguments(in);
      if (args.hasFlag("help")) {
        System.out.println(args.getDocumentation());
        System.exit(0);
      }
      try {
        args.validate();
      } catch (Exception ex) {
        System.out.println(args.getUsage());
        System.exit(0);
      }

      try {
        SlippyBundler bundler = new SlippyBundler(args.getValue("baseDir"));
        bundler
            .makeWorking(args.getValue("module"), args.getValue("version"), args.getValue("who"));
      } catch (FileNotFoundException ex) {
        System.out.println(ex.getMessage());
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    }
  }

  public static class DeleteWorking {
    public static void main(String[] in) {
      Arguments args = new Arguments();
      args.setProgramName("delete");
      args.setDocumentationProgram("Deletes the working code for a given user. "
          + "(You can't delete a particular version with this tool.)");

      args.addFlag("baseDir", ArgType.ARG_REQUIRED, ValueType.VALUE_REQUIRED,
          "Where the root directory of the module should be placed.");
      args.addFlag("module", ArgType.ARG_REQUIRED, ValueType.VALUE_REQUIRED,
          "Name of the module to create.");
      args.addFlag("who", ArgType.ARG_REQUIRED, ValueType.VALUE_REQUIRED,
          "A user name to distinguish among many working copies");

      args.addFlag("help", ArgType.ARG_OPTIONAL, ValueType.VALUE_IGNORED, "Shows useful help");
      args.parseArguments(in);
      if (args.hasFlag("help")) {
        System.out.println(args.getDocumentation());
        System.exit(0);
      }
      try {
        args.validate();
      } catch (Exception ex) {
        System.out.println(args.getUsage());
        System.exit(0);
      }

      try {
        SlippyBundler bundler = new SlippyBundler(args.getValue("baseDir"));
        bundler.deleteWorking(args.getValue("module"), args.getValue("who"));
      } catch (IOException ex) {
        System.out.println(ex.getMessage());
      }
    }
  }

  public static class GetRecent {
    public static void main(String[] in) {
      Arguments args = new Arguments();
      args.setProgramName("recent");
      args.setDocumentationProgram("Tells you the most recent version of a given module.");

      args.addFlag("baseDir", ArgType.ARG_REQUIRED, ValueType.VALUE_REQUIRED,
          "Where the root directory of the module should be placed.");
      args.addFlag("module", ArgType.ARG_REQUIRED, ValueType.VALUE_REQUIRED,
          "Name of the module to create.");

      args.addFlag("help", ArgType.ARG_OPTIONAL, ValueType.VALUE_IGNORED, "Shows useful help");
      args.parseArguments(in);
      if (args.hasFlag("help")) {
        System.out.println(args.getDocumentation());
        System.exit(0);
      }
      try {
        args.validate();
      } catch (Exception ex) {
        System.out.println(args.getUsage());
        System.exit(0);
      }

      try {
        SlippyBundler bundler = new SlippyBundler(args.getValue("baseDir"));
        int recent = bundler.getMostRecentVersion(args.getValue("module"));
        System.out.println("Most recent version of module " + args.getValue("module") + " is "
            + recent);
      } catch (IOException ex) {
        System.out.println(ex.getMessage());
      }
    }
  }

  public static class DeployVersion {
    public static void main(String[] in) {
      Arguments args = new Arguments();
      args.setProgramName("deploy");
      args.setDocumentationProgram("Deploys a new version based on a given user's working code."
          + "The new version is always incremental over the current 'most recent' version.");

      args.addFlag("baseDir", ArgType.ARG_REQUIRED, ValueType.VALUE_REQUIRED,
          "Where the root directory of the module should be placed.");
      args.addFlag("module", ArgType.ARG_REQUIRED, ValueType.VALUE_REQUIRED,
          "Name of the module to create.");
      args.addFlag("who", ArgType.ARG_REQUIRED, ValueType.VALUE_REQUIRED,
          "A user name to distinguish among many working copies");

      args.addFlag("help", ArgType.ARG_OPTIONAL, ValueType.VALUE_IGNORED, "Shows useful help");
      args.parseArguments(in);
      if (args.hasFlag("help")) {
        System.out.println(args.getDocumentation());
        System.exit(0);
      }
      try {
        args.validate();
      } catch (Exception ex) {
        System.out.println(args.getUsage());
        System.exit(0);
      }
      try {
        SlippyBundler bundler = new SlippyBundler(args.getValue("baseDir"));
        bundler.deployVersion(args.getValue("module"), args.getValue("who"));
      } catch (IOException ex) {
        System.out.println(ex.getMessage());
      }
    }
  }

  /**
   * The base dir for modules.
   */
  private File baseDir;

  public SlippyBundler(String base) throws IOException {
    baseDir = new File(base);
    FileUtil.complainIfNotWriteable(baseDir);
  }

  public SlippyBundler(File base) throws IOException {
    baseDir = base;
    FileUtil.complainIfNotWriteable(baseDir);
  }

  public void deployVersion(String module, String who) throws FileNotFoundException {
    String fragment = getPathFragment(module, "working", who);
    File workingDir = new File(baseDir, fragment);
    if (workingDir.exists()) {
      int mostRecent = getMostRecentVersion(module);
      String newVersionFragment = getPathFragment(module, "" + (mostRecent + 1), who);
      File newVersionDir = new File(baseDir, newVersionFragment);
      workingDir.renameTo(newVersionDir);
      mostRecent = getMostRecentVersion(module);
      if (newVersionDir.exists()) {
        System.out.println("Deployed " + module + " version " + mostRecent);
      } else {
        System.out.println("Unknown error! Go into an uncontrolled panic!");
      }
    } else {
      System.out.println("Could not find a working directory for " + module + " for " + who);
    }
  }

  public int getMostRecentVersion(String module) throws FileNotFoundException {
    int ret = -1;
    File moduleDir = new File(baseDir, module + File.separator + "version");
    if (moduleDir.exists()) {
      for (File f : moduleDir.listFiles()) {
        if (f.isDirectory()) {
          try {
            ret = Math.max(Integer.parseInt(f.getName()), ret);
          } catch (NumberFormatException ignore) {
          }
        }
      }
    } else {
      throw new FileNotFoundException(moduleDir.getAbsolutePath() + " does not exist. Returning -1");
    }
    return ret;
  }

  public void deleteWorking(String module, String who) throws FileNotFoundException {
    String fragment = getPathFragment(module, "working", who);
    File workingDir = new File(baseDir, fragment);
    if (workingDir.exists()) {
      FileUtil.deleteTree(workingDir);
    }
  }

  public void makeWorking(String module, String version, String who) throws IOException {
    String fragment = getPathFragment(module, "working", who);
    File sourceDir = new File(baseDir, getPathFragment(module, version, who));
    File workingDir = new File(baseDir, fragment);
    if (workingDir.exists()) {
      System.out.println("Error: " + workingDir.getAbsolutePath()
          + " exists. I won't overwrite it.");
    } else {
      System.out.println("Copying stuff...\n  source: " + sourceDir.getAbsolutePath()
          + "\n  dest:   " + workingDir.getAbsolutePath());
      FileUtil.copyTree(sourceDir, workingDir, null, null);
      System.out
          .println("Success, possibly. I mean, I got to this line. That has to count for sumptin'.");
    }
  }

  /**
   * This creates a new module, located in $baseDir/module, and the files in oliveCode are copied
   * into that directory.
   * 
   * @param module
   *          the name of the new module
   * @param oliveCode
   *          the base directory of the org.six11.olive Slippy classes
   * @return the directory of the newly created module
   */
  public File create(String module, String oliveCode) throws FileNotFoundException,
      IllegalArgumentException {
    File modDir = new File(baseDir, module + File.separator + "version" + File.separator + "1");
    if (modDir.exists()) {
      throw new IllegalArgumentException("'" + modDir.getAbsolutePath() + "' exists already.");
    }
    if (!modDir.mkdirs()) {
      throw new FileNotFoundException("Couldn't create directory: '" + modDir.getAbsolutePath()
          + "'");
    }
    File oliveFiles = new File(oliveCode);
    if (oliveFiles.exists() && oliveFiles.isDirectory()) {
      // copy the tree from oliveFiles/* to modDir/*, preserving tree structure.
      SuffixFileFilter blacklist = new SuffixFileFilter("svn");
      try {
        FileUtil.copyTree(oliveFiles, modDir, null, blacklist);
      } catch (IOException ex) {
        ex.printStackTrace();
        throw new IllegalArgumentException("Couldn't write some files (check permissions and such)");
      }
    } else {
      bug("Warning: The Olive slippy code directory does not exist: "
          + oliveFiles.getAbsolutePath());
      bug("++ Please manually create this and copy the org.six11.olive.* slippy files there.");
      throw new FileNotFoundException("Olive Slippy files (org.six11.olive.*) not found.");
    }
    return modDir;
  }

  /**
   * Returns a String such as "hello-working-billy.jar" or "hello-7.jar".
   * 
   * @param module
   *          the name of the module.
   * @param version
   *          the integer version number, or the string 'working'.
   * @param who
   *          the user who is editing a working copy. This is only used when version is 'working'.
   */
  public static String makeVersionedJarName(String module, String version, String who) {
    String ret = null;
    if (module == null || module.length() == 0) {
      throw new IllegalArgumentException("module is invalid (you provided " + module + ")");
    }
    if (version.equals("working") && (who == null || who.length() == 0)) {
      throw new IllegalArgumentException("who is invalid for working version (you provided " + who
          + ")");
    } else if (!version.equals("working")) {
      try {
        Integer.parseInt(version);
      } catch (NumberFormatException ex) {
        throw new IllegalAccessError(
            "version is not 'working' and it is not an integer (you provided " + version + ")");
      }
    }
    if (version.equals("working")) {
      ret = module + "-" + version + "-" + who + ".jar";
    } else {
      ret = module + "-" + version + ".jar";
    }
    return ret;
  }

  private File getVersionedJar(String module, String version, String who) {
    return new File(baseDir, makeVersionedJarName(module, version, who));
  }

  /**
   * Gives a path fragment that you can glue to the end of a base directory to get a path for a
   * particular versioned source tree.
   * 
   * @return a String representing a path. This does NOT begin nor end with the / character.
   */
  private String getPathFragment(String module, String version, String who)
      throws FileNotFoundException {
    String fragment = null;
    if (version.equals("working")) {
      fragment = module + File.separator + "working" + File.separator + who;
    } else {
      try {
        int num = Integer.parseInt(version);
        if (num <= 0) {
          throw new FileNotFoundException("Version '" + version + "' unavailable.");
        }
        fragment = module + File.separator + "version" + File.separator + num;
      } catch (NumberFormatException ex) {
        throw new FileNotFoundException("Version '" + version + "' unavailable.");
      }
    }
    return fragment;
  }

  /**
   * Create a jar file that contains the versioned module slippy code. This does NOT look to see if
   * there is a cached version in baseDir.
   */
  public File bundle(String module, String version, String who, File jarFile)
      throws FileNotFoundException, IOException {
    String lowerPath = getPathFragment(module, version, who);
    File path = new File(baseDir, lowerPath);
    FileUtil.complainIfNotReadable(path);

    File contents = new File(path, "contents.txt");
    if (contents.exists()) { // overwrite existing contents file from a previous execution
      contents.delete();
    }
    FileOutputStream contentOut = new FileOutputStream(contents);
    String absLoadPath = path.getAbsolutePath();
    List<File> matches = FileUtil.searchForSuffix(".slippy", path);
    for (File m : matches) {
      if (m.getAbsolutePath().startsWith(absLoadPath)) {
        String fqClass = SlippyUtils.fileStrToCodestStr(m.getAbsolutePath().substring(
            absLoadPath.length()));
        StreamUtil.writeStringToOutputStream(fqClass + "\n", contentOut);
      }
    }
    contentOut.close();

    File targetJar = getVersionedJar(module, version, who);
    FileUtil.copy(jarFile, targetJar);
    String cmd = "jar uf " + targetJar.getAbsolutePath() + " -C " + path.getAbsolutePath() + " .";
    Process process = Runtime.getRuntime().exec(cmd);
    int result;
    try {
      result = process.waitFor();
      if (result == 0) {
        System.out.println("Bundled module " + module + ": " + targetJar.getAbsolutePath());
      }
    } catch (InterruptedException ex) {
      ex.printStackTrace();
    }

    return targetJar;
  }
  
  private static void bug(String what) {
    Debug.out("SlippyBundler", what);
  }
}
