package org.six11.skrui.charrec;

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

  @SuppressWarnings("unused")
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
