package org.six11.skrui.data;

import java.util.ArrayList;
import java.util.List;

import org.six11.skrui.shape.Stroke;
import org.six11.util.Debug;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class TimeGraph {

  List<Stroke> sequences;

  public TimeGraph() {
    sequences = new ArrayList<Stroke>();
  }

  public void add(Stroke seq) {
    if (seq.size() > 1) {
      sequences.add(seq);
    }
  }
  
  public void remove(Stroke seq) {
    sequences.remove(seq);
  }

  @SuppressWarnings("unused")
  private static void bug(String what) {
    Debug.out("TimeGraph", what);
  }

  /**
   * Gets a list of recent sequences in chronological order. It will return sequences that begin no
   * later than the supplied timeout after the previous one ended. That is,
   * 
   * <pre>
   * retVal[i+1].start.time - retVal[i].end.time < timeout
   * </pre>
   * 
   * The return list is in chronological order: oldest first. The most recent sequence is always
   * returned in the last slot, no matter how long ago it was made.
   */
  public List<Stroke> getRecent(double timeout) {
    List<Stroke> ret = new ArrayList<Stroke>();
    if (sequences.size() > 1) {
      ret.add(sequences.get(sequences.size() - 1));
      for (int i = sequences.size() - 2; i >= 0; i--) {
        Stroke prev = sequences.get(i);
        Stroke next = sequences.get(i + 1);
        long elapsed = next.getFirst().getTime() - prev.getLast().getTime();
        if (elapsed < timeout) {
          ret.add(0, prev);
        } else {
          break;
        }
      }
    }
    return ret;
  }
}
