package org.six11.skrui.bayes;

import org.six11.util.Debug;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class ConditionalProbabilityTable {

  double[] data; // flat array of probabilities.
  String title;
  int[] dimensions;
  int[] size;
  String[] names;

  public ConditionalProbabilityTable(String title, String... varNames) {
    this.title = title;
    dimensions = new int[0];
    names = new String[0];
    addVariable(varNames);
  }

  /**
   * Returns an array telling you about the dimensionality of this table. getDimensions().length
   * tells you how many dimensions there are in total. getDimensions()[0] will tell you how many
   * possible values the 0th dimension can take on. And so on.
   */
  public int[] getDimensions() {
    return dimensions;
  }

  /**
   * Returns the number of values this probability table is primarily responsible for. It is NOT the
   * number of cells in the table. It is the number of elements in each row of the table. So if this
   * is a table for a binary random variable, this returns 2; for trinaries, it returns 3. Etc.
   * random variable, it returns an array with three. Etc. This is the same value as the LAST
   * element of getDimensions().
   */
  public int getNumValues() {
    return dimensions[dimensions.length - 1];
  }

  /**
   * Returns a vector of probabilities for the specified row.
   */
  public double[] getData(int... where) {
    int slot = getSlot(where);
    double[] ret = new double[getNumValues()];
    System.arraycopy(data, slot, ret, 0, ret.length);
    return ret;
  }

  /**
   * Adds another variable (dimension) to the table, where each possible value has a name. This
   * becomes the new 0th dimension. All previously added variables are slid over by one index, so
   * the old 0 is now at index 1.
   * 
   * This completely blows away the data in the table.
   */
  public void addVariable(String... varNames) {
    int newDim = varNames.length;
    int prevDims = dimensions.length;
    int[] oldDimensions = dimensions;
    dimensions = new int[prevDims + 1];
    dimensions[0] = newDim;
    System.arraycopy(oldDimensions, 0, dimensions, 1, oldDimensions.length);
    size = new int[dimensions.length];
    int prod = 1;
    int sum = 0;
    for (int i = dimensions.length - 1; i >= 0; i--) {
      size[i] = prod;
      prod = prod * dimensions[i];
      sum = sum + dimensions[i];
    }
    data = new double[prod];
    String[] oldNames = new String[names.length];
    System.arraycopy(names, 0, oldNames, 0, names.length);
    names = new String[sum];
    System.arraycopy(varNames, 0, names, 0, varNames.length);
    System.arraycopy(oldNames, 0, names, varNames.length, oldNames.length);
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
    if (where != null) {
      for (int i = 0; i < where.length; i++) {
        slot = slot + (where[i] * size[i]);
      }
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
    return getFormattedTable(null);
  }

  public String getFormattedTable(String pad) {
    if (pad == null) {
      pad = "";
    }
    StringBuilder buf = new StringBuilder();
    buf.append(pad + title + ":\n");
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
    for (int cursor = 0; cursor < data.length; cursor = cursor + mainDim) {
      for (int i = 0; i < extraDims; i++) {
        entries[row][i] = getName(i, cursor);
      }
      for (int i = 0; i < mainDim; i++) {
        int col = extraDims + ((i + cursor) % mainDim);
        entries[row][col] = Debug.num(data[i + cursor]);
      }
      row = row + 1;
    }
    int[] widths = new int[entries[0].length];
    for (int i = 0; i < widths.length; i++) {
      widths[i] = 6;
    }
    for (int i = 0; i < entries.length; i++) {
      for (int j = 0; j < entries[i].length; j++) {
        widths[j] = Math.max(widths[j], entries[i][j].length());
      }
    }

    // Now put the table into the buffer in nicely aligned columns/rows
    for (int i = 0; i < entries.length; i++) {
      for (int j = 0; j < entries[i].length; j++) {
        String l = entries[i][j];
        buf.append(pad + " " + getPadded(l, widths[j]));
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

  public void setRandomData() {
    for (int i = 0; i < data.length; i = i + getNumValues()) {
      double[] newData = BayesianNetwork.makeRandomDistribution(getNumValues());
      System.arraycopy(newData, 0, data, i, newData.length);
    }
  }

}
