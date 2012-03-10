package org.six11.sf;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.json.JSONException;
import org.json.JSONObject;
import org.six11.util.Debug;
import org.six11.util.io.FileUtil;

import static org.six11.util.Debug.bug;

public class Notebook {

  private SketchBook model;
  private File notebookDir;
  private Page currentPage;
  private Set<Page> pages;

  private static FilenameFilter pagesFilter = new FilenameFilter() {
    public boolean accept(File dir, String fileName) {
      return fileName.startsWith("page-") && fileName.endsWith(".json");
    }
  };

  public final static String SIMI_MAIN_FILE_NAME = "simi-notebook.json";

  public Notebook(SketchBook model, File notebookDir) {
    this.model = model;
    this.notebookDir = notebookDir;
    this.pages = new HashSet<Page>();
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
      String dirStr = prefs.get("notebook-dir", System.getProperty("user.home") + File.separator
          + "SIMI");
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
    ret.load();
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

  public Page getCurrentPage() {
    return currentPage;
  }

  private File getMainFile() {
    File mainFile = new File(notebookDir, SIMI_MAIN_FILE_NAME);
    return mainFile;
  }

  private void load() {
    File mainFile = getMainFile();
    File[] pageFiles = notebookDir.listFiles(pagesFilter);
    String mainStr = FileUtil.loadStringFromFile(mainFile);
    int pageNum = 0;
    if (mainStr.length() > 0) {
      try {
        JSONObject mainJson = new JSONObject(mainStr);
        pageNum = mainJson.optInt("current-page", 0); // defaults to 0
        bug("It seems there are " + pageFiles.length + " page files.");
        for (int i = 0; i < pageFiles.length; i++) {
          try {
            String pageStr = FileUtil.loadStringFromFile(pageFiles[i]);
            JSONObject obj = new JSONObject(pageStr);
            Page p = new Page(model, obj);
            pages.add(p);
            bug("Loaded page " + p.getPageNumber());
          } catch (JSONException ex) {
            bug("Can't read page file: " + pageFiles[i].getAbsolutePath());
          }
        }
      } catch (JSONException e) {
        e.printStackTrace();
      }
    }
    Page maybeCurrent = getPage(pageNum);
    if (maybeCurrent == null) {
      maybeCurrent = new Page(model, pageNum);
      pages.add(maybeCurrent);
    }
    currentPage = maybeCurrent;
  }

  public Page getPage(int pageNum) {
    // TODO: 'pages' should be a sorted set
    Page ret = null;
    for (Page p : pages) {
      if (p.getPageNumber() == pageNum) {
        ret = p;
        break;
      }
    }
    return ret;
  }

  public Set<Page> getPages() {
    return pages;
  }

  public Page addPage(int pg) {
    Page existing = getPage(pg);
    if (existing == null) {
      Page p = new Page(model, pg);
      pages.add(p);
      existing = p;
    }
    return existing;
  }

}
