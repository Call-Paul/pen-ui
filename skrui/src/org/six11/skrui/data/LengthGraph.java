package org.six11.skrui.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.six11.skrui.shape.Primitive;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class LengthGraph {

  static final Comparator<Primitive> sortByLen = new Comparator<Primitive>() {

    public int compare(Primitive o1, Primitive o2) {
      return ((Double) o1.getLength()).compareTo(o2.getLength());
    }

  };

  List<Primitive> primitives;

  public LengthGraph() {
    primitives = new ArrayList<Primitive>();
  }

  public void add(Primitive p) {
    int where = Collections.binarySearch(primitives, p, sortByLen);
    if (where < 0) {
      where = (where + 1) * -1;
    }
    primitives.add(where, p);
  }
  
  public void remove(Primitive p) {
    primitives.remove(p);
  }

  public Set<Primitive> getSimilar(double length, double tolerance) {
    Set<Primitive> ret = new HashSet<Primitive>();
    double a = length - (tolerance / 2);
    double b = length + (tolerance / 2);
    int idxA = search(a);
    int idxB = search(b);
    for (int i = idxA; i < idxB; i++) {
      ret.add(primitives.get(i));
    }
    return ret;
  }

  private int search(double target) {
    return search(0, primitives.size(), target);
  }

  private int search(int low, int high, double target) {
    int mid = (low + high) / 2;
    double thisLength = primitives.get(mid).getLength();
    if (thisLength == target) {
      return mid;
    } else {
      if (high - low == 1) {
        if (primitives.get(low).getLength() > target) {
          return low;
        } else {
          return high;
        }
      }
      if (target < thisLength) {
        return search(low, mid, target);
      } else {
        return search(mid, high, target);
      }
    }
  }

}
