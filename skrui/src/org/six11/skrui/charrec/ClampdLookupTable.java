package org.six11.skrui.charrec;

import java.util.Random;

import org.six11.util.Debug;

/**
 * A simple lookup table that lets you quickly convert two-byte sequences to doubles in the range
 * [0..1]. Obviously this only has 16 bits of precision, but dividing the range [0..1] into 65,536
 * pieces still gives you quite a lot to work with.
 * 
 * @author Gabe Johnson
 * 
 */
public class ClampdLookupTable {

  private static int boundary = (int) Math.pow(2, 16); // 65536 elements
  private static double incr = 1.0 / (double) (boundary - 1);
  double[] table;

  public static void main(String[] args) {
    ClampdLookupTable clam = new ClampdLookupTable();
    int LOTS = 1000000;
    // try it two ways: first, do not use a lookup table, but get the value of 1,000,000 int
    // indices. second, use the lookup table to get the value of 1,000,000 int indices
    Random rand = new Random(System.currentTimeMillis());
    int[] index = new int[LOTS];
    for (int i=0; i < index.length; i++) {
      index[i] = rand.nextInt(boundary);
    }
    long startA = System.nanoTime();
    for (int i=0; i < index.length; i++) {
      // convert index[i] from the 0-2^16 space to 0..1 double-space
      double result = (double) index[i] / boundary;
//      bug(index[i] + " --> " + Debug.num(result));
    }
    long endA = System.nanoTime();
    long startB = System.nanoTime();
    for (int i=0; i < index.length; i++) {
      clam.get(index[i]);
    }
    long endB = System.nanoTime();
    bug("Elapsed time (calculating every time): " + (endA - startA));
    bug("Elapsed time (using lookup table):     " + (endB - startB));
  }

  private static void bug(String what) {
    Debug.out("ClampdLookupTable", what);
  }
  
  public ClampdLookupTable() {
    table = new double[boundary]; // 65536 * 8 bytes = half a meg of memory. just so you know.
    for (int bytes = 0; bytes < boundary; bytes++) {
      table[bytes] = bytes * incr;
    }
  }

  public static int getIndexOf(double val) {
    int ret = 0;
    if (val > boundary) {
      ret = boundary - 1;
    } else if (val >= 0) {
      ret = (int) (val * boundary);
    }
    return ret;
  }

  public double get(int idx) {
    return table[idx];
  }
}
