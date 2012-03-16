package org.six11.sf;

import static org.six11.util.Debug.bug;

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

public class Notebook {

  public final static long AUTO_SAVE_TIMEOUT = 4 * 1000; // e.g. 20 * 1000 = 20 seconds
  private SketchBook model;
  private File notebookDir;
  private Page currentPage;
  private Set<Page> pages;
  private boolean shouldLoadDisk;
  private boolean shouldLoadDLs;

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
    this.shouldLoadDisk = true;
    this.shouldLoadDLs = true;
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
  
  public File getMainFileDirectory() {
    return notebookDir;
  }

  public void loadFromDisk() {
    shouldLoadDisk = false;
    bug("Ok trying to load from " + notebookDir.getAbsolutePath());
    File mainFile = getMainFile();
    File[] pageFiles = notebookDir.listFiles(pagesFilter);
    bug("" + pageFiles.length + " pages");
    String mainStr = FileUtil.loadStringFromFile(mainFile);
    int pageNum = 0;
    if (mainStr.length() > 0) {
      try {
        JSONObject mainJson = new JSONObject(mainStr);
        pageNum = mainJson.optInt("current-page", 0); // defaults to 0
      } catch (JSONException e) {
        e.printStackTrace();
      }
    }
    bug("It seems there are " + pageFiles.length + " page files.");
    for (int i = 0; i < pageFiles.length; i++) {
      try {
        bug("Loading page " + i);
        String pageStr = FileUtil.loadStringFromFile(pageFiles[i]);
        JSONObject obj = new JSONObject(pageStr);
        Page p = new Page(model, obj);
        addPage(p);
        bug("Loaded page " + p.getPageNumber());
      } catch (JSONException ex) {
        bug("Can't read page file: " + pageFiles[i].getAbsolutePath());
      }
    }
    Page maybeCurrent = getPage(pageNum);
    if (maybeCurrent == null) {
      maybeCurrent = new Page(model, pageNum);
      addPage(maybeCurrent);
    }
    currentPage = maybeCurrent;
  }

  public void addPage(Page page) {
    pages.add(page);
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
      addPage(p);
      existing = p;
    }
    return existing;
  }

  public void setCurrentPage(Page target) {
    currentPage = target;
    model.clearAll();
    if (currentPage.getSnapshotMachine().length() == 0) {
      currentPage.getSnapshotMachine().takeSnapshotImmediately();
    }
    model.undoRedoComplete();
    model.getEditor().getGrid().repaint();
  }

  public boolean shouldLoadFromDisk() {
    return shouldLoadDisk;
  }
  
  public boolean shouldLoadDisplayLists() {
    return shouldLoadDLs;
  }
  
  public void setDisplayListsLoaded() {
    shouldLoadDLs = false;
  }

  public void maybeSave(boolean ignoreTimeout) {
    long now = System.currentTimeMillis();
    for (Page page : pages) {
      if (page.getSnapshotMachine().isDirty()) {
        long dur = now - page.getSnapshotMachine().getLastDirtyTime();
        if (ignoreTimeout || (dur > AUTO_SAVE_TIMEOUT)) {
          long start = System.currentTimeMillis();
          int chars = 0;
          try {
            JSONObject obj = page.save();
            File pageFile = new File(notebookDir, "page-" + page.getPageNumber() + ".json");
            String pageStr = obj.toString();
            chars = pageStr.length();
            FileUtil.writeStringToFile(pageFile, obj.toString(), false);
            page.getSnapshotMachine().clearDirty();
          } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
          long elapsed = System.currentTimeMillis() - start;
          bug("Wrote page " + page.getPageNumber() + ": " + chars + " chars in " + elapsed + " ms");
        }
      } 
    }
  }
}
