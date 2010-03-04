package org.six11.skrui.bayes;

import org.six11.util.Debug;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class ConditionalProbabilityTable {

  double[] data; // flat array of probabilities.
  int[] dimensions;
  int[] size;
  String[] names;
  String title;

  public ConditionalProbabilityTable(String title, int... dims) {
    this.title = title;
    dimensions = new int[dims.length];
    size = new int[dims.length];
    int prod = 1;
    int sum = 0;
    for (int i = 0; i < dims.length; i++) {
      dimensions[i] = dims[i];
      prod = prod * dims[i];
      sum = sum + dims[i];
    }
    prod = 1;
    for (int i = dimensions.length - 1; i >= 0; i--) {
      size[i] = prod;
      prod = prod * dimensions[i];
    }
    data = new double[prod];
    names = new String[sum];
  }

  private void bug(String what) {
    Debug.out("CPT", what);
  }

  private String getName(int dim, int cell) {
    int extraDims = dimensions.length - 1;
    int row = cell / dimensions[extraDims];
    int[] changeFactor = new int[extraDims];

    int prod = 1;
    for (int i = extraDims - 1; i >= 0; i--) {
      changeFactor[i] = prod;
      prod = prod * dimensions[i];
    }
    int[] boundary = new int[extraDims + 1];
    int sum = 0;
    for (int i = 0; i <= extraDims; i++) {
      boundary[i] = sum;
      sum = sum + dimensions[i];
    }
    int howmany = boundary[dim + 1] - boundary[dim];
    int subidx = (row / changeFactor[dim]) % howmany;
    int idx = boundary[dim] + subidx;
    return names[idx];
  }

  private int getSlot(int... where) {
    int slot = 0;
    for (int i = 0; i < where.length; i++) {
      slot = slot + (where[i] * size[i]);
    }
    return slot;
  }

  public void setRow(double[] values, int... where) {
    int slot = getSlot(where);
    for (int i = 0; i < values.length; i++) {
      data[slot + i] = values[i];
    }
  }

  public void setCell(double value, int... where) {
    data[getSlot(where)] = value;
  }

  public void setNames(String... namen) {
    if (namen.length <= names.length) {
      for (int i = 0; i < namen.length; i++) {
        names[i] = namen[i];
      }
    }
  }

  public String[] getNames() {
    return names;
  }

  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(title + ":\n");
    // top row has blank cells for the first n-1 dimensions, followed by each of the last
    // dimension's names.
    int prod = 1;
    int extraDims = dimensions.length - 1;
    int mainDim = dimensions[extraDims];
    for (int i = 0; i < dimensions.length - 1; i++) {
      prod = prod * dimensions[i];
    }
    // first number is height, second is width
    String[][] entries = new String[prod + 1][dimensions[dimensions.length - 1]
        + (dimensions.length - 1)];
    for (int i = 0; i < extraDims; i++) {
      entries[0][i] = "--";
    }
    for (int i = 0; i < mainDim; i++) {
      entries[0][extraDims + i] = names[(names.length - mainDim) + i];
    }
    int row = 1;
    for (int cursor = 0; cursor < data.length; cursor = cursor + 4) {
      // for the rest of the rows, first extraDims columns are names
      for (int i = 0; i < extraDims; i++) {
        entries[row][i] = getName(i, cursor);
      }
      for (int i = 0; i < mainDim; i++) {
        entries[row][extraDims + ((i + cursor) % mainDim)] = Debug.num(data[i + cursor]);
      }
      row = row + 1;
    }
    int[] widths = new int[entries[0].length];
    for (int i=0; i < widths.length; i++) {
      widths[i] = 6;
    }
    for (int i = 0; i < entries.length; i++) {
      for (int j = 0; j < entries[i].length; j++) {
        widths[j] = Math.max(widths[j], entries[i][j].length());
      }
    }

    for (int i = 0; i < entries.length; i++) {
      for (int j = 0; j < entries[i].length; j++) {
        String l = entries[i][j];
         buf.append(" " + getPadded(l, widths[j]));
      }
      buf.append("\n");
    }
    buf.append("\n");
    return buf.toString();
  }

  private String getPadded(String s, int totalWidth) {
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < totalWidth - s.length(); i++) {
      buf.append(" ");
    }
    buf.append(s);
    return buf.toString();
  }

}
