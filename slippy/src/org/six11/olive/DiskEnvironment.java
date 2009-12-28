package org.six11.olive;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

  public DiskEnvironment(String loadPath) {
    super();
    setLoadPath(loadPath);
  }
  
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
    String relativeFileName = SlippyUtils.codesetStrToFileStr(fqClassName);
    File absFile = new File(getLoadPath(), relativeFileName);
    FileUtil.writeStringToFile(absFile, programString, false);
  }

  public static void bug(String what) {
    Debug.out("DiskEnvironment", what);
  }

  @Override
  public String[] listClasses() {
    File specFile = new File(getLoadPath());
    List<File> matches = FileUtil.searchForSuffix(".slippy", specFile);
    List<String> codesetStrings = new ArrayList<String>();
    String absLoadPath = specFile.getAbsolutePath();
    for (File m : matches) {
      if (m.getAbsolutePath().startsWith(absLoadPath)) {
        codesetStrings.add(SlippyUtils.fileStrToCodestStr(m.getAbsolutePath().substring(
            absLoadPath.length())));
      }
    }
    return codesetStrings.toArray(new String[0]);
  }

  @Override
  public void addFile(String fqClassName) {
    // this doesn't need to do anything, since listClasses() hits the disk every time. If that
    // changes, then addFile probably should be fleshed out.
  }

}
