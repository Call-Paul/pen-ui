package org.six11.skruifab;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.six11.skruifab.gui.GraphicMessage;
import org.six11.skruifab.gui.PenButton;
import org.six11.util.Debug;
import static org.six11.util.Debug.num;

public class SketchHUD extends JComponent {

  private List<GraphicMessage> messages;
  private Timer msgTimer;
  private List<PenButton> buttons; // e.g. the 'interpret' button

  public SketchHUD() {
    this.messages = new ArrayList<GraphicMessage>();
    this.buttons = new ArrayList<PenButton>();
    setOpaque(false);
    msgTimer = new Timer(1000, new ActionListener() {
      public void actionPerformed(ActionEvent ev) {
        whackMessageStack();
      }
    });
    msgTimer.stop();
    setLayout(null);
    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        updateLayout();
      }
      
    });
  }

  public void paintComponent(Graphics g1) {
    Graphics2D g = (Graphics2D) g1;
    drawMessages(g);
    drawButtons(g);
  }
  
  private void updateLayout() {
    int yCursor = 0;
    int yPad = 12;
    int xPad = 12;
    int right = getWidth();
    for (PenButton b : buttons) {
      yCursor = yCursor + yPad;
      int xCursor = right - xPad - b.getWidth();
      b.setBounds(xCursor, yCursor, b.getWidth(), b.getHeight());
      yCursor = yCursor + b.getHeight();
    } 
  }

  public void addMessage(final GraphicMessage msg) {
    Runnable r = new Runnable() {
      public void run() {
        messages.add(msg);
        whackMessageStack();
      }
    };
    SwingUtilities.invokeLater(r);
  }

  private void whackMessageStack() {
    synchronized (messages) {
      msgTimer.setDelay(1000 / 15);
      Collection<GraphicMessage> expired = new HashSet<GraphicMessage>();
      for (GraphicMessage msg : messages) {
        if (msg.getAge() > 7000) {
          expired.add(msg);
        }
      }
      messages.removeAll(expired);
      if (messages.size() > 0) {
        msgTimer.setRepeats(true);
        msgTimer.start();
      } else {
        msgTimer.stop();
        msgTimer.setRepeats(false);
      }
      repaint();
    }
  }

  private void drawMessages(Graphics2D g) {
    synchronized (messages) {
      double x, y;
      double vsep = 8; // vertical distance between messages
      double vpad = 16; // vertical distance from bottom to 1st message
      // x is always hpad + msg width away from right-most edge
      double hpad = 16;
      y = getHeight() - vpad;
      for (GraphicMessage msg : messages) {
        x = getWidth() - (msg.getWidth() + hpad);
        y = y - (msg.getHeight() + vsep);
        paintMessage(g, msg, x, y);
      }
    }
  }

  private void drawButtons(Graphics2D g) {
    for (PenButton b : buttons) {
      b.paint(g);
    }
  }

  private void paintMessage(Graphics2D g, GraphicMessage msg, double x, double y) {
    BufferedImage msgImage = msg.getImage();
    long age = msg.getAge();
    float alpha = 1f;
    if (age > 3000) {
      alpha = 1f - ((float) (age - 3000) / (float) 4000);
    }
    float[] scale = new float[] {
        1f, 1f, 1f, alpha
    };
    float[] offset = new float[] {
        0f, 0f, 0f, 0f
    };
    RescaleOp transpOp = new RescaleOp(scale, offset, null);
    g.drawImage(msgImage, transpOp, (int) x, (int) y);
  }

  public void addButton(PenButton b) {
    add(b);
    buttons.add(b);
    bug("Added button. there are now " + buttons.size() + " buttons");
  }

  private static void bug(String what) {
    Debug.out("SketchHUD", what);
  }
}
