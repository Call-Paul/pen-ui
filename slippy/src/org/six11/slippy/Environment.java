package org.six11.slippy;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Represents an operating environment for the Olive IDE. This could be a web-based environment
 * where all Slippy code resides on a server, or it could be a local computer with unfettered
 * filesystem access.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public abstract class Environment {
  private String loadPath;

  /**
   * Sets the load path for finding Slippy code. The interpretation of this is
   * implementation-dependent.
   */
  public void setLoadPath(String loadPath) {
    this.loadPath = loadPath;
  }

  /**
   * Gives the load path (also implementation dependent).
   */
  public String getLoadPath() {
    return loadPath;
  }

  /**
   * Loads a program source string from a fully-qualified file name.
   */
  public abstract String loadStringFromFile(String fullFileName) throws FileNotFoundException,
      IOException;

  /**
   * Gives you the fully qualified file name given a class name and codeset string, based on the
   * load path. For example (in a disk environment), if className is "BadGuy" and "codesetStr" is
   * "org.foobar.game", and the load path is "/home/bob/game", it will return
   * "/home/bob/game/org/foobar/game/BadGuy.slippy". In a web environment the full 'file' name might
   * actually be a URL.
   */
  public abstract String getFullFileName(String className, String codesetStr);

  /**
   * Converts a fully qualified class name into a fully qualified file name using the load path. If
   * it receives "org.foobar.game.BadGuy" it returns "/home/bob/game/org/foobar/game/BadGuy.slippy"
   * (assuming the load path is bob's home directory).
   */
  public abstract String classNameToFileName(String fqClass);

  /**
   * Saves a file. The file is derived from the fully qualified class name, and the contents by the
   * programString. The exact mechanism depends on the implementation, of course.
   */
  public abstract void save(String fqClassName, String programString);

}
