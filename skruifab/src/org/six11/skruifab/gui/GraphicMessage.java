package org.six11.skruifab.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;

import org.six11.util.Debug;
import org.six11.util.gui.Components;

public class GraphicMessage {

  private static Font BIG_FONT = new Font("Fanwood", Font.BOLD, 24);
  private static Font SMALL_FONT = new Font("Fanwood", Font.PLAIN, 14);

  public static GraphicMessage makeImportant(String msg) {
    return new GraphicMessage(msg, Color.BLACK, Color.YELLOW, BIG_FONT, 400);
  }

  public static GraphicMessage makeStandard(String msg) {
    return new GraphicMessage(msg, Color.BLACK, new Color(240, 240, 240), SMALL_FONT, 160);
  }

  long timeCreated;
  BufferedImage img;
  String text;
  Color textColor;
  Color bgColor;
  Font font;
  int maxWidth;

  private GraphicMessage(String text, Color textColor, Color bgColor, Font font, int maxWidth) {
    this.text = text;
    this.textColor = textColor;
    this.bgColor = bgColor;
    this.font = font;
    this.maxWidth = maxWidth;
    this.timeCreated = System.currentTimeMillis();
    img = createImage();
  }

  public BufferedImage getImage() {
    return img;
  }

  private BufferedImage createImage() {
    float w = 0;
    float h = 0;
    AttributedString attribString = new AttributedString(text);
    attribString.addAttribute(TextAttribute.FOREGROUND, textColor, 0, text.length());
    attribString.addAttribute(TextAttribute.FONT, font, 0, text.length());
    AttributedCharacterIterator attribCharIterator = attribString.getIterator();
    FontRenderContext frc = new FontRenderContext(null, true, true);
    LineBreakMeasurer lbm = new LineBreakMeasurer(attribCharIterator, frc);
    float hpad = 6;
    float vpad = 4;
    float x = 0;
    float y = 0;
    List<TextLayout> lines = new ArrayList<TextLayout>();
    while (lbm.getPosition() < text.length()) {
      TextLayout layout = lbm.nextLayout(maxWidth);
      lines.add(layout);
      y = y + layout.getAscent() + layout.getDescent() + layout.getLeading();
      w = Math.max(w, layout.getPixelBounds(frc, x, y).width);
      h = (int) y;
    }
    w = w + hpad * 2f;
    h = h + vpad * 2f;
    BufferedImage ret = new BufferedImage((int) Math.ceil(w), (int) Math.ceil(h),
        BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = (Graphics2D) ret.getGraphics();
    Components.antialias(g);
    Rectangle2D bounds = new Rectangle2D.Double(0, 0, w - 1, h - 1);
    g.setStroke(new BasicStroke(1.5f));
    g.setColor(bgColor);
    g.fill(bounds);
    g.setColor(textColor);
    g.draw(bounds);
    x = hpad;
    y = vpad;
    g.setFont(font);
    for (TextLayout layout : lines) {
      y = y + layout.getAscent();
      layout.draw(g, x, y);
      y = y + layout.getDescent() + layout.getLeading();
    }
    return ret;
  }

  private static void bug(String what) {
    Debug.out("GraphicMessage", what);
  }

  public double getWidth() {
    return (double) getImage().getWidth();
  }

  public double getHeight() {
    return (double) getImage().getHeight();
  }

  /**
   * Gives the age of this message in milliseconds.
   */
  public long getAge() {
    return System.currentTimeMillis() - timeCreated;
  }

}
