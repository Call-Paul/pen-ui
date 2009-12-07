package org.six11.olive;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.six11.util.gui.ColoredTextPane;

public class RedirectedOutputStream extends ByteArrayOutputStream {
  
  private String lineSeparator;
  private ColoredTextPane text;

  RedirectedOutputStream(ColoredTextPane text) {
    super();
    lineSeparator = System.getProperty("line.separator");
    this.text = text;
  }

  public void flush() throws IOException {
    String record;
    synchronized (this) {
      super.flush();
      record = this.toString();
      super.reset();
      if (record.length() == 0 || record.equals(lineSeparator)) {
        return;
      }
      text.append(Color.BLACK, record + "\n");
    }
  }
}
