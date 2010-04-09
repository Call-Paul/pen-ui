package org.six11.skrui.data;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.six11.util.Debug;
import org.six11.util.pen.Sequence;
import org.six11.util.pen.SequenceIO;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class TimeGraph {

  public static void main(String[] args) throws FileNotFoundException, IOException {
    List<Sequence> strokes = SequenceIO.readAll(new BufferedReader(new FileReader(args[0])));
    TimeGraph tg = new TimeGraph();
    for (Sequence seq : strokes) {
      tg.add(seq);
    }
  }

  List<Sequence> sequences;

  public TimeGraph() {
    sequences = new ArrayList<Sequence>();
  }

  public void add(Sequence seq) {
    if (seq.size() > 1) {
      sequences.add(seq);
    }
  }
  
  public void remove(Sequence seq) {
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
  public List<Sequence> getRecent(double timeout) {
    List<Sequence> ret = new ArrayList<Sequence>();
    if (sequences.size() > 1) {
      ret.add(sequences.get(sequences.size() - 1));
      for (int i = sequences.size() - 2; i >= 0; i--) {
        Sequence prev = sequences.get(i);
        Sequence next = sequences.get(i + 1);
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
