package org.six11.olive;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.six11.slippy.Environment;
import org.six11.slippy.SlippyUtils;
import org.six11.util.Debug;
import org.six11.util.io.FileUtil;

/**
 * 
 *
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class DiskEnvironment extends Environment {

  @Override
  public String loadStringFromFile(String fullFileName) throws FileNotFoundException, IOException {
    return FileUtil.loadStringFromFile(fullFileName);
  }

  @Override
  public String getFullFileName(String className, String codesetStr) {
    return getLoadPath() + File.separatorChar + codesetStr.replace('.', File.separatorChar)
    + File.separator + className + ".slippy";
  }

  @Override
  public String classNameToFileName(String fqClass) {
    return getLoadPath() + File.separator + SlippyUtils.codesetStrToFileStr(fqClass);
  }

  @Override
  public void save(String fqClassName, String programString) {
    System.err.println("Writing program string:\n" + programString);
    String relativeFileName = SlippyUtils.codesetStrToFileStr(fqClassName);
    File absFile = new File(getLoadPath(), relativeFileName);
    FileUtil.writeStringToFile(absFile, programString, false);
  }
  
  public static void bug(String what) {
    Debug.out("DiskEnvironment", what);
  }

}
