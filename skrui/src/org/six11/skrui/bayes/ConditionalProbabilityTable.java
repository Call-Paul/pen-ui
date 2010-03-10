package org.six11.skrui.bayes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.six11.util.Debug;

/**
 * This is something of an unholy hack. It is a probability table that can accommodate any number of
 * related random variables. One of those RVs is the 'main' variable, which is the one given to the
 * constructor.
 * 
 * This is backed by a flat array of doubles. It figures out which cell to use based on the number
 * of variables and how many states they have.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class ConditionalProbabilityTable {

  String title;
  double[] data; // flat array of probabilities.
  int[] dimensions; // dimensions and size arrays tell me how to find data. dimensions stores the
  int[] size; // number of states in each RV. size[i] stores the product of dimensions[i+1..n].

  String[] names; // used for debugging, and is highly useful.
  List<Node> variables; // references the original random variables.

  /**
   * Make a new probability table with the given name. Initially it is only for the single given
   * variable (this tables 'main' variable), but you can add as many as you like.
   * 
   * @param title
   * @param variable
   */
  public ConditionalProbabilityTable(String title, Node variable) {
    this.title = title;
    clear();
    addVariable(variable);
  }

  public void clear() {
    dimensions = new int[0];
    names = new String[0];
    variables = new ArrayList<Node>();
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
   * random variable, it returns an array with three. This is the same value as the LAST element of
   * getDimensions().
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

  String getName(int dim, int cell) {
    int extraDims = dimensions.length - 1;
    int row = cell / dimensions[extraDims];
    int[] changeFactor = getChangeFactors();
    int[] boundary = getNameBoundaries();
    int howmany = boundary[dim + 1] - boundary[dim];
    int subidx = (row / changeFactor[dim]) % howmany;
    int idx = boundary[dim] + subidx;
    return names[idx];
  }

  int[] getNameBoundaries() {
    int extraDims = dimensions.length - 1;
    int[] ret = new int[extraDims + 1];
    int sum = 0;
    for (int i = 0; i <= extraDims; i++) {
      ret[i] = sum;
      sum = sum + dimensions[i];
    }
    return ret;
  }

  public SlotKey[][] getParentIndexCombinations(SlotKey... include) {
    int numRows = getNumRows();
    Set<SlotKey> demand = new HashSet<SlotKey>();
    List<SlotKey[]> retList = new ArrayList<SlotKey[]>();
    if (include != null) {
      for (int i = 0; i < include.length; i++) {
        demand.add(include[i]);
      }
    }
    for (int i = 0; i < numRows; i++) {
      List<SlotKey> combo = getParentIndices(i);
      if (demand.size() > 0) {
        if (combo.containsAll(demand)) {
          retList.add(combo.toArray(new SlotKey[size.length - 1]));
        }
      } else {
        retList.add(combo.toArray(new SlotKey[size.length - 1]));
      }
    }
    SlotKey[][] ret = new SlotKey[retList.size()][size.length - 1];
    for (int i = 0; i < retList.size(); i++) {
      for (int j = 0; j < size.length - 1; j++) {
        ret[i][j] = retList.get(i)[j];
      }
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

  List<SlotKey> getParentIndices(int row) {
    List<SlotKey> ret = new ArrayList<SlotKey>();
    int[] cf = getChangeFactors();
    for (int i = 0; i < cf.length; i++) {
      int d = row / cf[i];
      int m = d % size[i];
      ret.add(new SlotKey(variables.get(i), m));
    }
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
      for (int i = 0; i < keys.length; i++) {
        try {
          slot = slot + (keys[i].index * size[variables.indexOf(keys[i].node)]);
        } catch (ArrayIndexOutOfBoundsException ex) {
          bug("Index variables.indexOf[keys[i].node)] : " + variables.indexOf(keys[i].node));
          bug("variables: " + Debug.num(variables, " "));
          bug("keys[i]: " + keys[i]);
          bug("keys[i].node: " + keys[i].node);
          System.exit(0);
        }
      }
    }
    return slot;
  }

  private static void bug(String what) {
    Debug.out("CPT", what);
  }

  /**
   * Sets the probability values for a given row. This does NOT ensure the data are normalized.
   */
  public void setRow(double[] values, SlotKey... keys) {
    int slot = getSlot(keys);
    for (int i = 0; i < values.length; i++) {
      data[slot + i] = values[i];
    }
  }

  /**
   * Sets a particular cell's value, using the cryptic int-indexing approach.
   */
  public void setCell(double value, int... where) {
    data[getSlot(where)] = value;
  }

  /**
   * Sets the names for the RV states. Not strictly needed but for debugging this is basically rad.
   */
  public void setNames(String... namen) {
    if (namen.length <= names.length) {
      for (int i = 0; i < namen.length; i++) {
        names[i] = namen[i];
      }
    }
  }

  /**
   * Gives the names of the RV states.
   */
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

  /**
   * Makes a nicely formatted text table. A sure hit at parties.
   */
  public String toString() {
    return Support.getFormattedTable(this, null);
  }

  /**
   * Don't want to bother setting data in your table? Leave it to chance. This generates and sets a
   * normalized vector for each row in the table.
   */
  public void setRandomData() {
    for (int i = 0; i < data.length; i = i + getNumValues()) {
      double[] newData = Support.makeRandomDistribution(getNumValues());
      System.arraycopy(newData, 0, data, i, newData.length);
    }
  }

  /**
   * Returns the variable that was used to construct this table.
   */
  public Node getMainVariable() {
    return variables.get(variables.size() - 1);
  }

}
