package org.six11.olive;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.HeadlessException;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.*;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.six11.slippy.SlippyUtils;
import org.six11.util.Debug;
import org.six11.util.data.DelayedData;
import org.six11.util.gui.ApplicationFrame;
import org.six11.util.io.FileUtil;
import org.six11.util.io.HttpUtil;

/**
 * A dialog box that downloads and shows a tree structure of Slippy classes. The files may be
 * located on the server or on the local file system. The best way to use this class is the static
 * 'showClassChooser' method.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class ClassChooser {

  public static void main(String[] args) {
    final ApplicationFrame af = new ApplicationFrame("Test App");
    JButton goButton = new JButton("Go!");
    goButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        // String result = showClassChooser(af, "http://six11.org/olive/code/list/");
        String result = showClassChooser(af, "somePathString");
        bug("You chose: " + result);
      }
    });
    af.setSize(600, 480);
    af.center();
    af.add(goButton);
    af.setVisible(true);
  }

  private static void bug(String what) {
    Debug.out("ClassChooser", what);
  }

  private static DelayedData makeDelayedOptions(final String spec) {
    DelayedData ret = null;
    if (spec.startsWith("http")) {
      // The data we need is out there in the mind-bogglingly large vastness of the Internet
      ret = new DelayedData() {
        public void fetch() {
          Runnable r = new Runnable() {
            public void run() {
              HttpUtil web = new HttpUtil();
              String response;
              try {
                response = web.downloadUrlToString(spec);
                StringTokenizer toks = new StringTokenizer(response, "\n");
                String[] d = new String[toks.countTokens()];
                int i = 0;
                while (toks.hasMoreTokens()) {
                  d[i++] = toks.nextToken();
                }
                setData(d);
              } catch (IOException ex) {
                ex.printStackTrace();
              }
            }
          };
          new Thread(r).start();
        }
      };
    } else {
      // The data we need is local
      ret = new DelayedData() {
        public void fetch() {
          Runnable r = new Runnable() {
            public void run() {
              File specFile = new File(spec);
              List<File> matches = FileUtil.searchForSuffix(".slippy", specFile);
              List<String> codesetStrings = new ArrayList<String>();
              String absLoadPath = specFile.getAbsolutePath();
              for (File m : matches) {
                if (m.getAbsolutePath().startsWith(absLoadPath)) {
                  codesetStrings.add(SlippyUtils.fileStrToCodestStr(m.getAbsolutePath().substring(
                      absLoadPath.length())));

                }
              }
              String[] d = codesetStrings.toArray(new String[0]);
              setData(d);
            }
          };
          new Thread(r).start();
        }
      };
    }
    return ret;
  }

  /**
   * Show a dialog for finding slippy files.
   * 
   * @param parentComp
   *          the parent location, used to center the dialog.
   * @param where
   *          the location to start looking. If this begins with 'http' it will treat this as a
   *          network lookup and act accordingly. Otherwise it treats this as a local filesystem
   *          lookup.
   */
  public static String showClassChooser(Component parentComp, String where) {
    String ret = null;
    String whereStr = where.startsWith("http") ? where + "list/" : where;
    ClassChooser chooser = new ClassChooser(parentComp, whereStr);
    chooser.go();
    ret = chooser.getValue();
    return ret;
  }

  private JDialog dialog;
  private String result;
  private JTextField textEntry;

  private ClassChooser(Component parentComp, String optionsUrl) {
    result = null;

    DelayedData options = makeDelayedOptions(optionsUrl);

    Window window = getWindowForComponent(parentComp);
    if (window instanceof Frame) {
      dialog = new JDialog((Frame) window, "Choose a class", true);
    } else {
      dialog = new JDialog((Dialog) window, "Choose a class", true);
    }

    dialog.setLayout(new BorderLayout());
    JPanel textPanel = new JPanel();
    textPanel.setLayout(new BorderLayout());
    JPanel treePanel = new JPanel();
    treePanel.setLayout(new BorderLayout());
    JPanel buttPanel = new JPanel();
    textEntry = new JTextField();
    textEntry.setEditable(false);
    textPanel.add(textEntry, BorderLayout.CENTER);
    final JTree treeBrowser = new JTree(new DefaultMutableTreeNode("wait for data..."));
    treeBrowser.setScrollsOnExpand(true);
    treeBrowser.setRootVisible(true);
    treeBrowser.addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent ev) {
        TreePath tp = treeBrowser.getSelectionPath();
        if (tp != null) {
          Object[] components = tp.getPath();
          if (components.length == 3) {
            String val = components[1] + "." + components[2];
            setValue(val);
          } else {
            setValue(null);
          }
        } else {
          setValue(null);
        }
      }
    });
    MouseListener ml = new MouseAdapter() {
      public void mousePressed(MouseEvent ev) {
        int selRow = treeBrowser.getRowForLocation(ev.getX(), ev.getY());
        TreePath selPath = treeBrowser.getPathForLocation(ev.getX(), ev.getY());
        if (selPath != null && selPath.getPathCount() == 3 && selRow != -1
            && ev.getClickCount() == 2) {
          dialog.setVisible(false);
        }
      }
    };
    treeBrowser.addMouseListener(ml);

    JScrollPane treeScroller = new JScrollPane(treeBrowser);
    treePanel.add(treeScroller, BorderLayout.CENTER);
    JButton cancel = new JButton("Cancel");
    cancel.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        result = null;
        dialog.setVisible(false);
      }
    });
    JButton ok = new JButton("OK");
    ok.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        dialog.setVisible(false);
      }
    });
    buttPanel.add(cancel);
    buttPanel.add(ok);
    dialog.add(textPanel, BorderLayout.NORTH);
    dialog.add(treePanel, BorderLayout.CENTER);
    dialog.add(buttPanel, BorderLayout.SOUTH);
    dialog.setSize(400, 350);
    dialog.setLocationRelativeTo(parentComp);

    options.addPropertyChangeListener(new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent evt) {
        treeBrowser.setModel(new DefaultTreeModel(makeTree((String[]) evt.getNewValue())));
        treeBrowser.setRootVisible(false);
      }
    });
    options.fetch();
  }

  public void go() {
    dialog.setVisible(true);
  }

  public String getValue() {
    return result;
  }

  public void setValue(String v) {
    if (v == null) {
      textEntry.setText("");
    } else {
      textEntry.setText(v);
    }
    result = v;
  }

  static DefaultMutableTreeNode makeTree(String[] data) {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("Available Classes");
    SortedMap<String, SortedSet<String>> groups = new TreeMap<String, SortedSet<String>>();
    for (String elm : data) {
      String group = SlippyUtils.getCodesetName(elm);
      String clazz = SlippyUtils.getClassName(elm);
      if (!groups.containsKey(group)) {
        groups.put(group, new TreeSet<String>());
      }
      groups.get(group).add(clazz);
    }

    for (String group : groups.keySet()) {
      DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(group);
      root.add(groupNode);
      for (String clazz : groups.get(group)) {
        groupNode.add(new DefaultMutableTreeNode(clazz));
      }
    }
    return root;
  }

  static Window getWindowForComponent(Component parentComponent) throws HeadlessException {
    if (parentComponent instanceof Frame || parentComponent instanceof Dialog)
      return (Window) parentComponent;
    return getWindowForComponent(parentComponent.getParent());
  }
}
