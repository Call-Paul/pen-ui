package org.six11.skrui.bayes;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.six11.util.Debug;

/**
 * This code was translated from 'markov.py' by Marcus Frean and Tony Vignaux.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class BayesianNetwork {

  static Random rand = new Random(System.currentTimeMillis());

  public static void main(String[] args) {
    Debug.useColor = false;
    Debug.useTime = false;
    new BayesianNetwork();
  }

  private static void bug(String what) {
    Debug.out("BayesianNetwork", what);
  }

  public static double[] makeRandomDistribution(int slots) {
    double[] ret = new double[slots];
    for (int i = 0; i < ret.length; i++) {
      ret[i] = rand.nextDouble();
    }
    return normalize(ret);
  }

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

  List<Node> nodes;

  public BayesianNetwork() {
    nodes = new ArrayList<Node>();
    Node clouds = new Node("Cloudy", "Sunny", "Cloudy");
    Node rain = new Node("Rain", "Yes", "No");
    Node.link(clouds, rain);
    clouds.cpt.setRow(new double[] {
        0.6, 0.4
    }); // No qualifier necessary because clouds has no parent.
    rain.cpt.setRow(new double[] {
        0.01, 0.99
    }, 0); // Qualifier 0 refers to parent's slot-0 variable: Sunny
    rain.cpt.setRow(new double[] {
        0.3, 0.7
    }, 1); // Qualifier 1 refers to parent's slot-1 variable: Cloudy
    nodes.add(clouds);
    nodes.add(rain);
    for (Node n : nodes) {
      System.out.print(n.cpt.toString());
    }
  }

}
