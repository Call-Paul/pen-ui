package org.six11.skrui.bayes;

import java.util.Random;
import java.util.Set;

import org.six11.util.Debug;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public abstract class Support {

  static Random rand = new Random(System.currentTimeMillis());

  public static void main(String[] args) {
    Debug.useColor = false;
    Debug.useTime = false;
    Debug.setDecimalOutputFormat("0.000");
    // BayesianNetwork net = BayesianNetwork.makeSimpleNetwork();
    BayesianNetwork net = BayesianNetwork.makeNeapolitanExample310();
    // net.mondoDebug(true);
    net.initializeNetwork();
    net.updateTree(net.nodes.get(2).slotKey(0));
    net.updateTree(net.nodes.get(1).slotKey(0));
    mondoDebug(net, false);
  }

  static void mondoDebug(BayesianNetwork net, boolean initializeNodes) {
    if (initializeNodes) {
      for (Node n : net.nodes) {
        n.initializePropagationSlots();
      }
    }
    for (Node n : net.nodes) {
      mondoDebug(n);
    }
  }

  static void mondoDebug(Node node) {
    StringBuilder buf = new StringBuilder();
    buf.append("Mondo Debug for node \"" + node.name + "\"\n");
    buf.append("  parents: (" + node.parents.size() + ")\n");

    for (Node n : node.parents) {
      buf.append("    " + n.name + "\n");
    }

    buf.append("  children: (" + node.children.size() + ")\n");
    for (Node n : node.children) {
      buf.append("    " + n.name + "\n");
    }

    buf.append("  childMessages:\n");
    for (Node k : node.childMessages.keySet()) {

      buf.append("    " + k.name + ": ");
      double[] msgs = node.childMessages.get(k);
      for (int i = 0; i < msgs.length; i++) {
        buf.append(node.getStateNames()[i] + "=" + msgs[i] + " ");
      }
      buf.append("\n");
    }

    buf.append("  parentMessages:\n");
    for (Node k : node.parentMessages.keySet()) {
      buf.append("    " + k.name + ": ");
      double[] msgs = node.parentMessages.get(k);
      for (int i = 0; i < msgs.length; i++) {
        buf.append(node.getStateNames()[i] + "=" + msgs[i] + " ");
      }
      buf.append("\n");
    }

    buf.append("  child message values:\n");
    for (int i = 0; i < node.childValues.length; i++) {
      buf.append("    " + node.getStateNames()[i] + ": " + Debug.num(node.childValues[i]) + "\n");
    }

    buf.append("  informed beliefs:\n");
    for (Set<SlotKey> observed : node.beliefs2.keySet()) {
      if (observed.size() == 0) {
        buf.append("    ¯: " + Debug.num(node.beliefs2.get(observed)) + "\n");
      } else {
        buf.append("    " + Debug.num(observed, " ") + ": "
            + Debug.num(node.beliefs2.get(observed)) + "\n");
      }
    }

    buf.append("  conditional probability table:\n");
    buf.append(Support.getFormattedTable(node.cpt, "    "));
    buf.append(Support.dashes(70) + "\n");

    System.out.println(buf.toString());
  }

  static double[] makeRandomDistribution(int slots) {
    double[] ret = new double[slots];
    for (int i = 0; i < ret.length; i++) {
      ret[i] = rand.nextDouble();
    }
    return normalize(ret);
  }

  /**
   * This makes a normalized array of doubles. The proportions of the input array are maintained,
   * but the elements of the output array sum to 1.0.
   */
  public static double[] normalize(double[] in) {
    double sum = 0;
    for (int i = 0; i < in.length; i++) {
      sum = sum + in[i];
    }
    double z = 1.0 / sum;
    double[] ret = new double[in.length];
    for (int i = 0; i < in.length; i++) {
      ret[i] = in[i] * z;
    }
    return ret;
  }

  static void printObserveBox(SlotKey obsSlotKey) {
    String obsMessage = "Obverving node " + obsSlotKey.node + " = " + obsSlotKey;
    System.out.println();
    System.out.println(" +" + dashes(obsMessage.length() + 2) + "+");
    System.out.println(" | " + obsMessage + " |");
    System.out.println(" +" + dashes(obsMessage.length() + 2) + "+");
    System.out.println();
  }

  static String dashes(int n) {
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < n; i++) {
      buf.append("-");
    }
    return buf.toString();
  }

  static void out(String what) {
    System.out.println(what);
  }

  static String getFormattedTable(ConditionalProbabilityTable cpt, String pad) {
    if (pad == null) {
      pad = "";
    }
    StringBuilder buf = new StringBuilder();
    buf.append(pad + cpt.title + ":\n");
    int prod = 1;
    int extraDims = cpt.dimensions.length - 1;
    int mainDim = cpt.dimensions[extraDims];
    for (int i = 0; i < cpt.dimensions.length - 1; i++) {
      prod = prod * cpt.dimensions[i];
    }
    // first number is height, second is width
    String[][] entries = new String[prod + 1][cpt.dimensions[cpt.dimensions.length - 1]
        + (cpt.dimensions.length - 1)];
    for (int i = 0; i < extraDims; i++) {
      entries[0][i] = "--";
    }
    for (int i = 0; i < mainDim; i++) {
      entries[0][extraDims + i] = cpt.names[(cpt.names.length - mainDim) + i];
    }
    int row = 1;
    for (int cursor = 0; cursor < cpt.data.length; cursor = cursor + mainDim) {
      for (int i = 0; i < extraDims; i++) {
        entries[row][i] = cpt.getName(i, cursor);
      }
      for (int i = 0; i < mainDim; i++) {
        int col = extraDims + ((i + cursor) % mainDim);
        entries[row][col] = Debug.num(cpt.data[i + cursor]);
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

  static String getPadded(String s, int totalWidth) {
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < totalWidth - s.length(); i++) {
      buf.append(" ");
    }
    buf.append(s);
    return buf.toString();
  }

}
