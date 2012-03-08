package org.six11.sf;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.six11.util.Debug;

import static org.six11.util.Debug.bug;

public class Notebook {
  
  
  private SketchBook model;
  private File notebookDir;

  public final static String SIMI_MAIN_FILE_NAME = "simi-notebook.json";
  
  public Notebook(SketchBook model, File notebookDir) {
    this.model = model;
    this.notebookDir = notebookDir;
    bug("Created notebook file in " + notebookDir.getAbsolutePath());
  }
  
  public static Notebook loadLast(SketchBook model) {
    Notebook ret = null;
    Preferences prefs = Preferences.userNodeForPackage(Notebook.class);
    try {
      String[] keys = prefs.keys();
      for (String k : keys) {
        bug("  pref: " + k + " = " + prefs.get(k, "no-value"));
      }
      String dirStr = prefs.get("notebook-dir", System.getProperty("user.home") + File.separator + "SIMI");
      File rootDir = new File(dirStr);
      if (!rootDir.exists()) {
        rootDir.mkdir();
      }
      Set<File> notebookDirs = new HashSet<File>();
      for (File child : rootDir.listFiles()) {
        if (detectNotebookDir(child)) {
          notebookDirs.add(child);
        }
      }
      File mostRecent = null;
      for (File nbDir : notebookDirs) {
        if (mostRecent == null) {
          mostRecent = new File(nbDir, SIMI_MAIN_FILE_NAME);
        } else {
          File mainFile = new File(nbDir, SIMI_MAIN_FILE_NAME);
          if (mainFile.lastModified() > mostRecent.lastModified()) {
            mostRecent = mainFile;
          }
        }
      }
      if (mostRecent != null) {
        bug("Found most recent notebook in " + mostRecent.getAbsolutePath());
        ret = new Notebook(model, mostRecent.getParentFile());
      } else {
        bug("No notebooks found. Making a default one based on the date.");
        String today = Debug.nowFilenameFriendly();
        File nbDir = initNewNotebook(rootDir, today);
        ret = new Notebook(model, nbDir);
      }
    } catch (BackingStoreException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return ret;
  }
  
  private static File initNewNotebook(File rootDir, String today) {
    File nbDir = new File(rootDir, today);
    nbDir.mkdir();
    File mainFile = new File(nbDir, SIMI_MAIN_FILE_NAME);
    try {
      mainFile.createNewFile();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return nbDir;
  }

  private static boolean detectNotebookDir(File f) {
    boolean ret = false;
    if (f.isDirectory()) {
      File[] kids = f.listFiles(new FilenameFilter() {
        public boolean accept(File dir, String name) {
          return name.equals(SIMI_MAIN_FILE_NAME);
        }
      });
      if (kids.length == 1) {
        ret = true;
      }
    }
    return ret;
  }
}
