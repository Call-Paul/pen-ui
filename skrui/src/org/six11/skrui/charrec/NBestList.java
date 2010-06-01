package org.six11.skrui.charrec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class NBestList {

  SortedSet<NBest> nBestList;
  boolean smallerIsBetter;

  public NBestList(boolean smallerIsBetter) {
    nBestList = new TreeSet<NBest>();
    this.smallerIsBetter = smallerIsBetter;
  }

  public void addScore(String label, double score) {
    nBestList.add(new NBest(label, score));
  }

  public List<NBest> getNBest(int n) {
    List<NBest> ret = new ArrayList<NBest>();
    Set<String> labels = new HashSet<String>();
    for (NBest hit : nBestList) {
      if (!labels.contains(hit.label)) {
        labels.add(hit.label);
        ret.add(hit);
      }
      if (ret.size() == n) {
        break;
      }
    }
    return ret;
  }
  
  public int size() {
    return nBestList.size();
  }

  public class NBest implements Comparable<NBest> {

    public String label;
    public double score;

    private NBest(String label, double score) {
      this.label = label;
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
  }

}
