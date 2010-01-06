package org.six11.olive;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.*;

import org.six11.slippy.SlippyStyledDocumentColorizer;
import org.six11.util.Debug;
import org.six11.util.data.TextUtil;
import org.six11.util.gui.ColoredTextPane;

/**
 * This is the main editing component for Slippy source files. It consists of tabs, each of which
 * holds a colored text pane. At bottom are some status labels that hold information about where the
 * cursor is.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class SlippyEditor extends JComponent {

  private ColoredTextPane editor;
  private JPanel statusPanel;
  private Map<String, JLabel> statusLabels;
  private String fqClassName; // the fully qualified class name the editor is for.
  private SlippyStyledDocumentColorizer colorizer = new SlippyStyledDocumentColorizer();
  private boolean dirty = false;
  private Set<PropertyChangeListener> pcListeners;

  public SlippyEditor(String fqClassName) {
    this();
    this.fqClassName = fqClassName;
  }

  public SlippyEditor() {
    setLayout(new BorderLayout());

    pcListeners = new HashSet<PropertyChangeListener>();
    JScrollPane textScroller = new JScrollPane();
    textScroller.getVerticalScrollBar().setUnitIncrement(16);

    editor = new ColoredTextPane(textScroller.getViewport());
    editor.setBackground(Color.WHITE);
    editor.setEditable(true);
    editor.setLineWrap(false);
    editor.addCaretListener(new CaretListener() {
      public void caretUpdate(CaretEvent e) {
        int dot = e.getDot();
        String text = editor.getText();
        TextUtil.IntPair linecol = TextUtil.getLineAndColumn(text, dot);
        setStatus("Line", "" + linecol.line);
        setStatus("Col", "" + linecol.col);
      }
    });
    final MutableAttributeSet green = new SimpleAttributeSet();
    StyleConstants.setBackground(green, new Color(0x22, 0xee, 0x5b));
    StyledDocument doc = new DefaultStyledDocument() {
      @Override
      public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
        if (str.equals("\t")) {
          str = "  "; // replace tabs with two spaces
        }
        setDirty(true);
        super.insertString(offs, str, green);
      }

      public void remove(int offs, int len) throws BadLocationException {
        setDirty(true);
        super.remove(offs, len);
      }

      public void replace(int offset, int length, String text, AttributeSet attrs)
          throws BadLocationException {
        setDirty(true);
        super.replace(offset, length, text, attrs);
      }

    };
    editor.setDocument(doc);
    statusPanel = new JPanel();

    statusPanel.setLayout(new FlowLayout());
    statusLabels = new HashMap<String, JLabel>();
    statusLabels.put("Line", new JLabel());
    statusLabels.put("Col", new JLabel());
    setStatus("Line", "0");
    setStatus("Col", "0");
    statusPanel.add(statusLabels.get("Line"));
    statusPanel.add(statusLabels.get("Col"));
    add(statusPanel, BorderLayout.SOUTH);
    textScroller.setViewportView(editor);
    add(textScroller, BorderLayout.CENTER);
  }

  protected void bug(String what) {
    Debug.out("SlippyEditor", what);
  }

  public ColoredTextPane getTextPane() {
    return editor;
  }

  private final void setStatus(String whichLabel, String value) {
    statusLabels.get(whichLabel).setText(whichLabel + ": " + value);
  }

  public String getFQClassName() {
    return fqClassName;
  }

  public void colorize() {
    try {
      colorizer.walk(editor.getStyledDocument());
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  public void setDirty(boolean v) {
    if (dirty != v) {
      PropertyChangeEvent ev = new PropertyChangeEvent(this, "dirty", dirty, v);
      dirty = v;
      firePCE(ev);
    }
  }

  public void addDirtyListener(PropertyChangeListener lis) {
    if (!pcListeners.contains(lis)) {
      pcListeners.add(lis);
    }
  }

  private void firePCE(PropertyChangeEvent ev) {
    for (PropertyChangeListener lis : pcListeners) {
      lis.propertyChange(ev);
    }
  }

  /**
   * Tells you if the document has been edited since the last time it was declared unedited (e.g. a
   * save).
   */
  public boolean isDirty() {
    return dirty;
  }
}
