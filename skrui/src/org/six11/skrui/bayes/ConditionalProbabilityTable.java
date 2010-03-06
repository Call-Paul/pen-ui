package org.six11.skrui.bayes;

import java.util.ArrayList;
import java.util.List;

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
  List<Node> variables;

  public ConditionalProbabilityTable(String title, Node variable) {
    this.title = title;
    dimensions = new int[0];
    names = new String[0];
    variables = new ArrayList<Node>();
    addVariable(variable);
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
  public double[] getData(SlotKey[] keys) {
    int slot = getSlot(keys);
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
  public void addVariable(Node variable) {
    variables.add(0, variable);
    String[] varNames = variable.stateNames;
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

  private int[] getChangeFactors() {
    int extraDims = dimensions.length - 1;
    int[] ret = new int[extraDims];
    int prod = 1;
    for (int i = extraDims - 1; i >= 0; i--) {
      ret[i] = prod;
      prod = prod * dimensions[i];
    }
    return ret;
  }

  private String getName(int dim, int cell) {
    int extraDims = dimensions.length - 1;
    int row = cell / dimensions[extraDims];
    int[] changeFactor = getChangeFactors();
    int[] boundary = getNameBoundaries();
    int howmany = boundary[dim + 1] - boundary[dim];
    int subidx = (row / changeFactor[dim]) % howmany;
    int idx = boundary[dim] + subidx;
    return names[idx];
  }

  public int[] getNameBoundaries() {
    int extraDims = dimensions.length - 1;
    int[] ret = new int[extraDims + 1];
    int sum = 0;
    for (int i = 0; i <= extraDims; i++) {
      ret[i] = sum;
      sum = sum + dimensions[i];
    }
    return ret;
  }

  public SlotKey[][] getParentIndexCombinations() {
    int numRows = getNumRows();
    SlotKey[][] ret = new SlotKey[numRows][size.length - 1];
    for (int i = 0; i < numRows; i++) {
      ret[i] = getParentIndices(i);
    }
    return ret;
  }

  public int getNumRows() {
    int prod = 1;
    for (int i = 0; i < dimensions.length - 1; i++) { // last dim entry is for 'this', so ignore it.
      prod = prod * dimensions[i];
    }
    return prod;
  }

  public SlotKey[] getParentIndices(int row) {
    SlotKey[] ret = new SlotKey[size.length - 1];
    int[] cf = getChangeFactors();
    for (int i = 0; i < cf.length; i++) {
      int d = row / cf[i];
      int m = d % size[i];
      ret[i] = new SlotKey(variables.get(i), m);
    }
    // StringBuilder buf = new StringBuilder();
    // int[] bounds = getNameBoundaries();
    // for (int i=0; i < ret.length; i++) {
    // buf.append(ret[i] + "=" + (names[bounds[i] + ret[i]]) + "  ");
    // }
    // bug("getParentIndices for row " + row + ": " + buf.toString());
    return ret;
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
  
  private int getSlot(SlotKey[] keys) {
    int slot = 0;
    if (keys != null) {
      for (int i=0; i < keys.length; i++) {
        slot = slot + (keys[i].index * size[variables.indexOf(keys[i].node)]);
      }
    }
    return slot;
  }

  public void setRow(double[] values, SlotKey... keys) {
    int slot = getSlot(keys);
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

  /**
   * This gives you the name of one of the columns. It ignores the parents. So if the CPT is for a
   * binary variable with states 'Raining' and 'Not Raining', but it depends on 'Cloudy', it will
   * return 'Raining' or 'Not Raining' depending if you ask for name 0 or 1. The 'Cloudy' parents
   * are totally ignored.
   */
  public String getStateName(int idx) {
    int start = names.length - dimensions[dimensions.length - 1];
    return names[start + idx];
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

  public Node getMainVariable() {
    return variables.get(variables.size() - 1);
  }

}
