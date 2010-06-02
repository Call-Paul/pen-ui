package org.six11.skrui.charrec;

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
    for (int i = 0; i < args.length; i = i + 2) {
      try {
        byte a = Byte.parseByte(args[i]);
        int ai = 128 + a;
        byte b = Byte.parseByte(args[i + 1]);
        int bi = 128 + b;
        int idx = ai * 256 + bi;
        double v = clam.get(idx);
        System.out.println("bytes " + a + " and " + b + " indicate the integers " + ai + " and "
            + bi);
        System.out.println("Unsigned short " + idx + " = " + v);
        System.out.println("Reversing that, the index of " + v + " is: "
            + ClampdLookupTable.getIndexOf(v));
      } catch (NumberFormatException ex) {
        ex.printStackTrace();
      }
    }
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
