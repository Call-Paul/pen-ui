package org.six11.skrui.charrec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.six11.util.Debug;

public class NBestList {

  List<NBest> nBestList;
  boolean smallerIsBetter;
  int maxSize;

  public NBestList(boolean smallerIsBetter, int maxSize) {
    this.nBestList = new ArrayList<NBest>();
    this.smallerIsBetter = smallerIsBetter;
    this.maxSize = maxSize;
  }

  public void addScore(Sample sample, double score) {
    NBest addMe = new NBest(sample, score);
    int where = Collections.binarySearch(nBestList, addMe);
    if (where < 0) {
      where = -(where + 1); // completely dumb. Thanks, Sun.
      nBestList.add(where, addMe);
      while (nBestList.size() >= maxSize) {
        nBestList.remove(nBestList.size() - 1);
      }
    }
  }

  @SuppressWarnings("unused")
  private static void bug(String what) {
    Debug.out("NBestList", what);
  }

  public List<NBest> getNBest() {
    return nBestList;
  }

  public List<NBest> getNBest(int n) {
    List<NBest> ret = new ArrayList<NBest>();
    for (int i = 0; i < Math.min(n, nBestList.size()); i++) {
      ret.add(nBestList.get(i));
    }
    return ret;
  }

  /**
   * Note: check with size() first to ensure the list has elements.
   */
  public NBest getWorst() {
    return nBestList.get(nBestList.size() - 1);
  }

  public int size() {
    return nBestList.size();
  }

  public class NBest implements Comparable<NBest> {

    public Sample sample;
    public double score;

    private NBest(Sample sample, double score) {
      this.sample = sample;
      this.score = score;
    }

    public int compareTo(NBest o) {
      int ret = 0;
      if (smallerIsBetter) {
        if (score < o.score) {
          ret = -1;
        } else if (score > o.score) {
          ret = 1;
        }
      } else {
        if (score < o.score) {
          ret = 1;
        } else if (score > o.score) {
          ret = -1;
        }
      }
      return ret;
    }

    public String toString() {
      return sample.toString() + " score: " + Debug.num(score);
    }
  }

}
