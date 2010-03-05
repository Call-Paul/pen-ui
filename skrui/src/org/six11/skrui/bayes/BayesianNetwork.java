package org.six11.skrui.bayes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

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
    Debug.setDecimalOutputFormat("0.000");
//    BayesianNetwork net = BayesianNetwork.makeSimpleNetwork();
    BayesianNetwork net = BayesianNetwork.makeNeapolitanExample310();
    net.initializeNetwork();
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
  }
  
  public void initializeNetwork() {
    for (Node n : nodes) {
      n.initializePropagationSlots();
    }
    for (Node n : nodes) {
      for (int i=0; i < n.getStateCount(); i++) {
        n.setChildValue(i, 1); // Compute lambda values
      }
      for (Node z : n.parents) {
        for (int i=0; i < z.getStateCount(); i++) {
          z.setChildMessage(n, i, 1); // Compute lambda messages
        }
      }
      for (Node y : n.children) {
        for (int i=0; i < n.getStateCount(); i++) {
          n.setParentMessage(y, i, 1); // Initialize pi messages
        }
      }
      Set<Node> roots = getRoots(nodes);
      for (Node r : roots) {
        for (int i=0; i < r.getStateCount(); i++) {
          // P(r|a) = P(r)
          // pi(r) = P(r)
        }
        for (Node x : r.children) {
          // send_pi_msg(r, x)
        }
      }
    }
    
    for (Node n : nodes) {
      n.mondoDebug();
    }
  }
    
  private Set<Node> getRoots(List<Node> all) {
    Set<Node> ret = new HashSet<Node>();
    for (Node n : all) {
      if (n.parents.size() == 0) {
        ret.add(n);
      }
    }
    return ret;
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
    }, 0); // Qualifier 0 refers to parent's slot-0 variable: Sunny
    rain.cpt.setRow(new double[] {
        0.3, 0.7
    }, 1); // Qualifier 1 refers to parent's slot-1 variable: Cloudy
    BayesianNetwork net = new BayesianNetwork();
    net.addNode(clouds);
    net.addNode(rain);
    for (Node n : net.getNodes()) {
      System.out.print(n.cpt.toString());
    }
    return net;
  }

  public static BayesianNetwork makeNeapolitanExample310() {
    Node b = new Node("Burglary", "b1", "b2");
    Node f = new Node("Freight Truck", "f1", "f2");
    Node a = new Node("Alarm", "a1", "a2");
    Node.link(b, a);
    Node.link(f, a);
    b.cpt.setRow(new double[] {
       0.005, 0.995 
    });
    f.cpt.setRow(new double[] {
        0.03, 0.97 
     });
    a.cpt.setRow(new double[] {
        0.992, 1-0.992
    }, 0, 0);
    a.cpt.setRow(new double[] {
        0.99, 1-0.99
    }, 1, 0);
    a.cpt.setRow(new double[] {
        0.2, 1-0.2
    }, 0, 1);
    a.cpt.setRow(new double[] {
        0.003, 1-0.003
    }, 1, 1);
    BayesianNetwork net = new BayesianNetwork();
    net.addNode(b);
    net.addNode(f);
    net.addNode(a);
    return net;
  }
  
  public void addNode(Node n) {
    nodes.add(n);
  }

  public List<Node> getNodes() {
    return nodes;
  }

}
