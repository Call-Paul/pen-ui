package org.six11.sf.rec;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.six11.sf.SketchBook;

public abstract class RecognizedRawItem {

  public static final String NO_OP = "No Operation";
  public static final String ENCIRCLE_STENCIL_TO_SELECT = "Encircle Stencil Selection";
  public static final String ENCIRCLE_ENDPOINTS_TO_MERGE = "Encircle Endpoints to Merge";
  public static final String SCRIBBLE_TO_ERASE = "Scribble to Erase";
  public static final String FAT_DOT_REFERENCE_POINT = "Fat Dot Reference Point";
  public static final String FAT_DOT_SELECT = "Fat Dot Selection";
  public static final String OVERTRACE_TO_SELECT_SEGMENT = "Overtrace to Select Segment";
  public static final String ENCIRCLE_GUIDE_POINT_TO_DELETE = "Encircle Guide Points to Delete";
  public static final String ENCIRCLE_TO_T_MERGE = "Encircle Enpoints to T-Junction";
  
  private boolean ok;
  private String name;
  private Set<String> trumpedItems;

  public RecognizedRawItem(boolean ok, String name, String... trumps) {
    this.ok = ok;
    this.name = name;
    this.trumpedItems = new HashSet<String>();
    Collections.addAll(trumpedItems, trumps);
//    StringBuilder buf = new StringBuilder();
//    if (trumpedItems.size() == 0) {
//      buf.append("Nothing");
//    } else {
//      for (String s : trumpedItems) {
//        buf.append(s + " ");
//      }
//    }
//    bug("RecognizedRawItem: " + name + " trumps: " + buf);
  }

  public boolean isOk() {
    return ok;
  }
  
  public String toString() {
    return name + " [ok=" + ok + "]";
  }
  
  public boolean trumps(RecognizedRawItem other) {
    return trumpedItems.contains(other.name);
  }

  public abstract void activate(SketchBook model);

  /**
   * Makes a 'no-op' recognized item. This can be used as a default recognition result in case there
   * aren't any real ones.
   */
  public static RecognizedRawItem noop() {
    return new RecognizedRawItem(false, RecognizedRawItem.NO_OP) {
      public void activate(SketchBook model) {
      }
    };
  }
}
