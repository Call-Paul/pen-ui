package org.six11.sf;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import org.json.JSONArray;
import org.json.JSONException;
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
    try {
      load(obj);
    } catch (JSONException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  public Page(SketchBook model, int pageNum) {
    this.model = model;
    this.snapshotMachine = new SnapshotMachine(model);
    this.pageNum = pageNum;
    this.rect = new Rectangle();
  }

  public void load(JSONObject obj) throws JSONException {
    pageNum = obj.getInt("pageNum");
    int currentIdx = obj.getInt("snapshotCursor");
    JSONArray pageArr = obj.getJSONArray("snapshots");
    for (int i = 0; i < pageArr.length(); i++) {
      try {
        Snapshot snap = new Snapshot(model, pageArr.getJSONObject(i));
        snapshotMachine.push(snap);
        bug("Loaded from disk: page " + pageNum + " / snap " + i);
      } catch (Exception ex) {
        bug("Warning: was unable to load snapshot at snapshots[" + i + "]");
      }
    }
    snapshotMachine.setCurrentIdx(currentIdx);
  }

  public JSONObject save() throws JSONException {
    JSONArray snaps = new JSONArray();
    bug("Adding " + snapshotMachine.length() + " snapshots for page " + pageNum);
    for (int i = 0; i < snapshotMachine.length(); i++) {
      Snapshot snap = snapshotMachine.get(i);
      JSONObject json = snap.getJSONRoot();
      snaps.put(json);
    }
    JSONObject ret = new JSONObject();
    ret.put("pageNum", pageNum);
    ret.put("snapshotCursor", getSnapshotMachine().getCurrentIdx());
    ret.put("snapshots", snaps);
    return ret;
  }

  public boolean hasModelData() {
    return getSnapshotMachine().length() > 0;
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

  public void clearThumb() {
    setTinyThumb(null);
  }

}
