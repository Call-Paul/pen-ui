package org.six11.sf;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import org.json.JSONObject;
import static org.six11.util.Debug.bug;

public class Page {

  private SnapshotMachine snapshotMachine;
  private int pageNum;
  private SketchBook model;
  private BufferedImage tinyThumb;
  private transient BufferedImage cachedThumb;
  private transient Rectangle rect;

  public Page(SketchBook model, JSONObject obj) {
    this.model = model;
    this.snapshotMachine = new SnapshotMachine(model);
    this.rect = new Rectangle();
    load(obj);
  }

  public Page(SketchBook model, int pageNum) {
    this.model = model;
    this.snapshotMachine = new SnapshotMachine(model);
    this.pageNum = pageNum;
    this.rect = new Rectangle();
  }

  private void load(JSONObject obj) {
    // TODO
  }

  public SnapshotMachine getSnapshotMachine() {
    return snapshotMachine;
  }

  public int getPageNumber() {
    return pageNum;
  }

  public BufferedImage getTinyThumb() {
    return tinyThumb;
  }

  public void setTinyThumb(BufferedImage im) {
    this.tinyThumb = im;
    this.setCachedThumb(null);
    bug("Totally set the tiny thumbnail.");
    model.getEditor().getGrid().repaint();
  }

  public BufferedImage getCachedThumb() {
    return cachedThumb;
  }

  public void setCachedThumb(BufferedImage cachedThumb) {
    this.cachedThumb = cachedThumb;
  }

  public void setRectangle(int x, int y, int w, int h) {
    rect.setFrame(x, y, w, h);
  }

  public Rectangle getRectangle() {
    return rect;
  }
  
  public String toString() {
    return "Page " + pageNum;
  }

}
