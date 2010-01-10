package org.six11.olive.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;

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

  public static final String MOD_INFO_PROPS = "module-info.properties";
  public static final String MAIN_FILE = "main.txt";
  public static final String CONTENTS_FILE = "contents.txt";
  public static final String CREATOR_FILE = "creator.txt";

  private static boolean outputEnabled = false;
  
  /**
   * 
   * 
   * @author Gabe Johnson <johnsogg@cmu.edu>
   */
  public class Version {
    public String module;
    public String version;
    public Date when;
    public String who;

    public String getModule() { // getters to placate JSTL
      return module;
    }

    public String getVersion() {
      return version;
    }

    public String getWho() {
      return who;
    }

    public Version(String module, String version, Date when, String who) {
      this.module = module;
      this.version = version;
      this.when = when;
      this.who = who;
    }

    public String toString() {
      return who + "@" + module + "#" + version;
    }
  }

  public static Comparator<Version> sortByVersionThenName = new Comparator<Version>() {
    public int compare(Version o1, Version o2) {
      int ret = 0;
      try {
        int v1 = Integer.parseInt(o1.version);
        int v2 = Integer.parseInt(o2.version);
        ret = (new Integer(v1).compareTo(new Integer(v2)));
      } catch (NumberFormatException ex) {
        if (o1.who != null && o2.who != null) {
          ret = o1.who.compareTo(o2.who);
        } else {
          ret = o1.when.compareTo(o2.when);
        }
      }
      return ret;
    }
  };

  /**
   * This is for building a jar file from the command line, also good for testing.
   */
  public static void main(String[] in) {
    outputEnabled = true;
    if (in.length == 0) {
      out("Usage: SlippyBundler [command] [args...]");
      out("Possible commands are:");
      out("  bundle");
      out("  create");
      out("  delete");
      out("  deploy");
      out("  main");
      out("  recent");
      out("  work");
      out("  versions");
      out("Generally you can get help like this: SlippyBundler [command] --help");
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
    } else if (in[0].equals("versions")) {
      GetVersions.main(rest);
    } else if (in[0].equals("main")) {
      MakeMain.main(rest);
    } else {
      out("Where'd you go to school? Run without args to see help.");
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
        out(args.getDocumentation());
        System.exit(0);
      }
      try {
        args.validate();
      } catch (Exception ex) {
        out(args.getUsage());
        out("To create a new module, execute org.six11.olive.SlippyBunder$Create");
        System.exit(0);
      }
      try {
        SlippyBundler bundler = new SlippyBundler(args.getValue("baseDir"));
        File target = bundler.getVersionedJar(args.getValue("module"), args.getValue("version"),
            args.getValue("who"));
        if (target != null && target.exists()) {
          out(target.getAbsolutePath() + " exists (" + target.length() + " bytes)");
        } else {
          File originalJar = new File(args.getValue("jar"));
          FileUtil.complainIfNotReadable(originalJar);
          File jarFile = bundler.bundle(args.getValue("module"), args.getValue("version"), args
              .getValue("who"), originalJar);
          out("I now have a nice jar file in: '" + jarFile.getAbsolutePath() + "'");
        }
      } catch (FileNotFoundException ex) {
        out(ex.getMessage());
      } catch (IOException ex) {
        out(ex.getMessage());
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
        out(args.getDocumentation());
        System.exit(0);
      }
      try {
        args.validate();
      } catch (Exception ex) {
        out(args.getUsage());
        System.exit(0);
      }

      try {
        SlippyBundler bundler = new SlippyBundler(args.getValue("baseDir"));
        bundler.create(args.getValue("module"), args.getValue("oliveCode"));
      } catch (IOException ex) {
        out(ex.getMessage());
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
        out(args.getDocumentation());
        System.exit(0);
      }
      try {
        args.validate();
      } catch (Exception ex) {
        out(args.getUsage());
        System.exit(0);
      }

      try {
        SlippyBundler bundler = new SlippyBundler(args.getValue("baseDir"));
        bundler
            .makeWorking(args.getValue("module"), args.getValue("version"), args.getValue("who"));
      } catch (FileNotFoundException ex) {
        out(ex.getMessage());
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
        out(args.getDocumentation());
        System.exit(0);
      }
      try {
        args.validate();
      } catch (Exception ex) {
        out(args.getUsage());
        System.exit(0);
      }

      try {
        SlippyBundler bundler = new SlippyBundler(args.getValue("baseDir"));
        bundler.deleteWorking(args.getValue("module"), args.getValue("who"));
      } catch (IOException ex) {
        out(ex.getMessage());
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
        out(args.getDocumentation());
        System.exit(0);
      }
      try {
        args.validate();
      } catch (Exception ex) {
        out(args.getUsage());
        System.exit(0);
      }

      try {
        SlippyBundler bundler = new SlippyBundler(args.getValue("baseDir"));
        Version recent = bundler.getMostRecentVersion(args.getValue("module"));
        out("Most recent version of module " + args.getValue("module") + " is "
            + recent.version);
      } catch (IOException ex) {
        out(ex.getMessage());
      }
    }
  }

  public static class MakeMain {
    public static void main(String[] in) {
      Arguments args = new Arguments();
      args.setProgramName("main");
      args.setDocumentationProgram("Sets a new main class for a module working copy.");

      args.addFlag("baseDir", ArgType.ARG_REQUIRED, ValueType.VALUE_REQUIRED,
          "Where the root directory of the module should be placed.");
      args.addFlag("module", ArgType.ARG_REQUIRED, ValueType.VALUE_REQUIRED,
          "Name of the module to create.");
      args.addFlag("who", ArgType.ARG_REQUIRED, ValueType.VALUE_REQUIRED,
          "A user name to distinguish among many working copies");
      args.addFlag("fqClass", ArgType.ARG_REQUIRED, ValueType.VALUE_REQUIRED,
          "The fully qualified class name (e.g. com.foo.MyThing) that should execute when "
              + "the module is run.");
      args.addFlag("help", ArgType.ARG_OPTIONAL, ValueType.VALUE_IGNORED, "Shows useful help");
      args.parseArguments(in);
      if (args.hasFlag("help")) {
        out(args.getDocumentation());
        System.exit(0);
      }
      try {
        args.validate();
      } catch (Exception ex) {
        out(args.getUsage());
        System.exit(0);
      }
      try {
        SlippyBundler bundler = new SlippyBundler(args.getValue("baseDir"));
        bundler.makeMain(args.getValue("module"), args.getValue("who"), args.getValue("fqClass"));
      } catch (IOException ex) {
        out(ex.getMessage());
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
        out(args.getDocumentation());
        System.exit(0);
      }
      try {
        args.validate();
      } catch (Exception ex) {
        out(args.getUsage());
        System.exit(0);
      }
      try {
        SlippyBundler bundler = new SlippyBundler(args.getValue("baseDir"));
        bundler.deployVersion(args.getValue("module"), args.getValue("who"));
      } catch (IOException ex) {
        out(ex.getMessage());
      }
    }
  }

  public static class GetVersions {
    public static void main(String[] in) {
      Arguments args = new Arguments();
      args.setProgramName("versions");
      args.setDocumentationProgram("Lists the modules, version numbers, and when they were made.");

      args.addFlag("baseDir", ArgType.ARG_REQUIRED, ValueType.VALUE_REQUIRED,
          "Where the root directory of the module should be placed.");
      args.addFlag("module", ArgType.ARG_OPTIONAL, ValueType.VALUE_REQUIRED,
          "List all versions of the given module, rather than only the most recent "
              + "version of all modules.");

      args.addFlag("help", ArgType.ARG_OPTIONAL, ValueType.VALUE_IGNORED, "Shows useful help");
      args.parseArguments(in);
      if (args.hasFlag("help")) {
        out(args.getDocumentation());
        System.exit(0);
      }
      try {
        args.validate();
      } catch (Exception ex) {
        out(args.getUsage());
        System.exit(0);
      }
      try {
        SlippyBundler bundler = new SlippyBundler(args.getValue("baseDir"));
        List<Version> v = null;
        if (args.hasFlag("module")) {
          bug("Showing versions for module " + args.getValue("module"));
          v = bundler.getAllVersions(args.getValue("module"));
        } else {
          v = bundler.getAllVersions();
        }
        int longestVersionString = 0;
        for (Version ver : v) {
          longestVersionString = Math.max((ver.version + "").length(), longestVersionString);
        }
        for (Version ver : v) {
          for (int i = ((ver.version + "").length()); i >= 0; i--) {
            System.out.print(" ");
          }
          System.out.print(ver.version);
          System.out.print("   " + ver.module + "\n");
        }
      } catch (IOException ex) {
        out(ex.getMessage());
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

  public String deployVersion(String module, String who) throws FileNotFoundException {
    String ret = "Nothing :(";
    String fragment = getPathFragment(module, "working", who);
    File workingDir = new File(baseDir, fragment);
    if (workingDir.exists()) {
      Version mostRecent = getMostRecentVersion(module);
      int nextVersion = Integer.parseInt(mostRecent.version) + 1;
      String newVersionFragment = getPathFragment(module, "" + nextVersion, who);
      File newVersionDir = new File(baseDir, newVersionFragment);
      File creatorFile = new File(workingDir, CREATOR_FILE);
      FileUtil.writeStringToFile(creatorFile, who, false);
      workingDir.renameTo(newVersionDir);
      mostRecent = getMostRecentVersion(module);
      if (newVersionDir.exists()) {
        out("Deployed " + module + " version " + mostRecent);
        ret = newVersionFragment;
      } else {
        out("Unknown error! Go into an uncontrolled panic!");
      }
    } else {
      out("Could not find a working directory for " + module + " for " + who);
    }
    return ret;
  }
  
  public boolean makeMain(String module, String who, String fqClass) throws FileNotFoundException {
    boolean ret = false;
    String frag = getPathFragment(module, "working", who);
    File workingDir = new File(baseDir, frag);
    File newMainFile = new File(workingDir, SlippyUtils.codesetStrToFileStr(fqClass));
    if (newMainFile.exists() && newMainFile.canRead()) {
      out("Found source file...");
      File mainFile = new File(workingDir, MAIN_FILE);
      FileUtil.writeStringToFile(mainFile, fqClass, false);
      out("Established " + fqClass + " as the new main class for " + module);
      ret = true;
    } else {
      out("Could not find source file.");
    }
    return ret;
  }

  public Version getMostRecentVersion(String module) throws FileNotFoundException {
    Version ret = new Version(module, "-1", null, null);
    File moduleDir = new File(baseDir, module + File.separator + "version");
    if (moduleDir.exists()) {
      for (File f : moduleDir.listFiles()) {
        if (f.isDirectory()) {
          try {
            if (Integer.parseInt(f.getName()) > Integer.parseInt(ret.version)) {
              ret.version = f.getName();
              ret.when = new Date(f.lastModified());
              ret.who = getCreator(f);
            }
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
      out("Error: " + workingDir.getAbsolutePath()
          + " exists. I won't overwrite it.");
    } else {
      out("Copying stuff...\n  source: " + sourceDir.getAbsolutePath()
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
  public static String getPathFragment(String module, String version, String who)
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
  
  public String getContentsList(String module, String version, String who) throws IOException {
    String frag = getPathFragment(module, version, who);
    File path = new File(baseDir, frag);
    FileUtil.complainIfNotReadable(path);
    StringBuffer buf = new StringBuffer();
    String absLoadPath = path.getAbsolutePath();
    List<File> matches = FileUtil.searchForSuffix(".slippy", path);
    for (File m : matches) {
      if (m.getAbsolutePath().startsWith(absLoadPath)) {
        String fqClass = SlippyUtils.fileStrToCodestStr(m.getAbsolutePath().substring(
            absLoadPath.length()));
        buf.append(fqClass + "\n");
      }
    }
    return buf.toString();
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

    // Write a 'contents.txt' file that lists each fully-qualified class name, one per line.
    File contents = new File(path, CONTENTS_FILE);
    if (contents.exists()) { // overwrite existing contents file from a previous execution
      contents.delete();
    }
    FileOutputStream contentOut = new FileOutputStream(contents);
    StreamUtil.writeStringToOutputStream(getContentsList(module, version, who), contentOut);
    contentOut.close();

    // Write 'module-info.properties', a Properties file that contains information about the
    // jar file: module name, user name, version, and main slippy class.
    File modInfo = new File(path, MOD_INFO_PROPS);
    File mainFile = new File(path, MAIN_FILE); // this file might not exist
    String main = null;
    if (mainFile.exists() && mainFile.canRead()) {
      main = FileUtil.loadStringFromFile(mainFile).trim();
    }
    Properties modInfoProps = new Properties();
    modInfoProps.setProperty("module", module);
    modInfoProps.setProperty("who", who);
    modInfoProps.setProperty("version", version);
    if (main != null) {
      modInfoProps.setProperty("main", main);
    }
    modInfoProps.store(new FileOutputStream(modInfo), null);

    File targetJar = getVersionedJar(module, version, who);
    FileUtil.copy(jarFile, targetJar);
    String cmd = "jar uf " + targetJar.getAbsolutePath() + " -C " + path.getAbsolutePath() + " .";
    Process process = Runtime.getRuntime().exec(cmd);
    int result;
    try {
      result = process.waitFor();
      if (result == 0) {
        out("Bundled module " + module + ": " + targetJar.getAbsolutePath());
      }
    } catch (InterruptedException ex) {
      ex.printStackTrace();
    }

    return targetJar;
  }
  
  private static void out(String what) {
    if (outputEnabled) {
      System.out.println(what);
    }
  }

  private static void bug(String what) {
    Debug.out("SlippyBundler", what);
  }

  private static String getCreator(File f) {
    String ret = null;
    File creatorFile = new File(f, "creator.txt");
    if (creatorFile.exists() && creatorFile.canRead()) {
      ret = FileUtil.loadStringFromFile(creatorFile).trim();
    }
    return ret;
  }

  public List<Version> getAllVersions(String module) {
    List<Version> v = new ArrayList<Version>();
    // Get numeric deployed versions
    File moduleDir = new File(baseDir, module + File.separator + "version");
    if (moduleDir.exists()) {
      for (File f : moduleDir.listFiles()) {
        if (f.isDirectory()) {
          try {
            String who = getCreator(f);
            v.add(new Version(module, f.getName(), new Date(f.lastModified()), who));
          } catch (NumberFormatException ignore) {
          }
        }
      }
    }
    // Get working versions
    moduleDir = new File(baseDir, module + File.separator + "working");
    if (moduleDir.exists()) {
      for (File f : moduleDir.listFiles()) {
        if (f.isDirectory()) {
          v.add(new Version(module, "working", new Date(f.lastModified()), f.getName()));
        }
      }
    }
    Collections.sort(v, sortByVersionThenName);
    return v;
  }

  public List<Version> getAllVersions() {
    List<Version> v = new ArrayList<Version>();
    for (File modDir : baseDir.listFiles()) {
      if (modDir.isDirectory()) {
        try {
          v.add(getMostRecentVersion(modDir.getName()));
        } catch (Exception ex) {
          bug("Found whacked out module directory: " + modDir.getAbsolutePath());
        }
      }
    }
    Collections.sort(v, sortByVersionThenName);
    return v;
  }
}
