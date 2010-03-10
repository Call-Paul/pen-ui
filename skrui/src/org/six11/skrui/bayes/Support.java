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

  public static boolean showOutput = true;
  public static boolean showLocation = true;
  public static boolean showImportant = true;

  public static void main(String[] args) {
    Debug.useColor = false;
    Debug.useTime = false;
    Debug.setDecimalOutputFormat("0.000");
    // BayesianNetwork net = Support.makeSimpleNetwork();
    BayesianNetwork net = Support.makeNeapolitanExample310();
    // net.mondoDebug(true);
    net.initializeNetwork();
    net.updateTree(net.nodes.get(2).slotKey(0));
    net.updateTree(net.nodes.get(1).slotKey(0));
    mondoDebug(net, false);
  }

  public static void mondoDebug(BayesianNetwork net, boolean initializeNodes) {
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
    for (Set<SlotKey> observed : node.beliefs.keySet()) {
      if (observed.size() == 0) {
        buf.append("    ¯: " + Debug.num(node.beliefs.get(observed)) + "\n");
      } else {
        buf.append("    " + Debug.num(observed, " ") + ": " + Debug.num(node.beliefs.get(observed))
            + "\n");
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
    String obsMessage = "Observing " + obsSlotKey.node + " = " + obsSlotKey;
    Support.important("");
    Support.important(" +" + dashes(obsMessage.length() + 2) + "+");
    Support.important(" | " + obsMessage + " |");
    Support.important(" +" + dashes(obsMessage.length() + 2) + "+");
    Support.important("");
  }

  static String dashes(int n) {
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < n; i++) {
      buf.append("-");
    }
    return buf.toString();
  }

  static void out(String what) {
    if (showOutput) {
      System.out.println(what);
    }
  }

  static void where(String where) {
    if (showLocation) {
      System.out.println(where);
    }
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

  public static void important(String what) {
    if (showImportant) {
      System.out.println(what);
    }
  }

  public static BayesianNetwork makeSimpleNetwork() {
    Node clouds = new Node("Cloudy", "Sunny", "Cloudy");
    Node rain = new Node("Rain", "Yes", "No");
    Node.link(clouds, rain);
    clouds.cpt.setRow(new double[] {
        0.6, 0.4
    }); // No qualifier necessary because clouds has no parent.
    rain.cpt.setRow(new double[] {
        0.01, 0.99
    }, clouds.slotKey(0)); // Qualifier 0 refers to parent's slot-0 variable: Sunny
    rain.cpt.setRow(new double[] {
        0.3, 0.7
    }, clouds.slotKey(1)); // Qualifier 1 refers to parent's slot-1 variable: Cloudy
    BayesianNetwork net = new BayesianNetwork();
    net.addNode(clouds);
    net.addNode(rain);
    for (Node n : net.getNodes()) {
      System.out.print(n.cpt.toString());
    }
    return net;
  }

  /**
   * Creates the bayesian network shown in figure 3.10 of Neapolitan's book
   * "Learning Bayesian Networks". The named slots (b1, b2, f1, etc.) agree with his terms. All my
   * numbers check out with his worked examples.
   */
  public static BayesianNetwork makeNeapolitanExample310() {
    Node b = new Node("Burglary", "b1", "b2");
    Node f = new Node("Freight Truck", "f1", "f2");
    Node a = new Node("Alarm", "a1", "a2");
    Node.link(f, a);
    Node.link(b, a);
    b.setProbabilityDistribution(new double[] {
        0.005, 0.995
    // P (B)
        });
    f.setProbabilityDistribution(new double[] {
        0.03, 0.97
    // P (F)
        });

    a.setProbabilityDistribution(new double[] {
        0.992, 1 - 0.992
    }, b.slotKey(0), f.slotKey(0)); // P(A | b1, f1)

    a.setProbabilityDistribution(new double[] {
        0.99, 1 - 0.99
    }, b.slotKey(0), f.slotKey(1)); // P(A | b1, f2)

    a.setProbabilityDistribution(new double[] {
        0.2, 1 - 0.2
    }, b.slotKey(1), f.slotKey(0)); // P(A | b2, f1)

    a.setProbabilityDistribution(new double[] {
        0.003, 1 - 0.003
    }, b.slotKey(1), f.slotKey(1)); // P(A | b2, f2)

    BayesianNetwork net = new BayesianNetwork();
    net.addNode(b);
    net.addNode(f);
    net.addNode(a);
    return net;
  }

}
