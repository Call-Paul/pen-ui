package org.six11.skrui.charrec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.Stack;

import org.six11.util.Debug;

/**
 * This class provides support for making hierarchical clusters of Sample objects.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Dendogram {

  public static class Cluster {

    // a compound cluster has some samples inside it, kept in an array.
    Sample[] points;

    // cluster has center: center of mass of samples.
    double[] center;

    // cluster has exemplar: the closest sample to center
    Sample exemplar;

    // cluster has radius r, which is the max dist from r to any point in the cluster
    double radius;

    // a cluster rank increases from the leaves to the root.
    int rank;

    // Clusters might be composed of sub-clusters. Retain references to them.
    Cluster a, b;

    // Create a cluster out of some set of samples.
    public Cluster(int rank, Sample... samps) {
      build(rank, samps);
    }

    public Cluster(int rank, Cluster clusterA, Cluster clusterB) {
      this.a = clusterA;
      this.b = clusterB;
      Sample[] samps = new Sample[a.points.length + b.points.length];
      System.arraycopy(a.points, 0, samps, 0, a.points.length);
      System.arraycopy(b.points, 0, samps, a.points.length, b.points.length);
      build(rank, samps);
    }

    private final void build(int rank, Sample... samps) {
      this.rank = rank;
      this.points = new Sample[samps.length];
      System.arraycopy(samps, 0, points, 0, points.length);

      // set the cluster center-of-mass
      double[] sum = new double[points[0].getPCACoordinate().length];
      for (int i = 0; i < sum.length; i++) {
        for (int j = 0; j < points.length; j++) {
          sum[i] += points[j].getPCACoordinate()[i];
        }
      }
      center = new double[sum.length];
      for (int i = 0; i < sum.length; i++) {
        center[i] = sum[i] / points.length;
      }

      // find the sample that is closest to and farthest from this center of mass
      double closest = Double.MAX_VALUE;
      radius = 0;
      for (Sample point : points) {
        double d = point.pcaSquaredDist(center);
        if (d < closest) {
          exemplar = point;
          closest = d;
        }
        radius = Math.max(radius, d);
      }

      // vomit out the rank and radius
      // bug("Cluster " + exemplar.getLabel() + " rank " + rank + " has radius: " +
      // Debug.num(radius));
    }

    // dist between two clusters is the dist between their exemplars, not center of mass. This
    // actually returns the squared distance, but since it would not make a difference in the end, I
    // will spare the cycles and not do the square root.
    public double dist(Cluster other) {
      return exemplar.pcaSquaredDist(other.exemplar);
    }

    public double getRadius() {
      return radius;
    }

    public Sample getCenter() {
      return exemplar;
    }

    public Cluster getSubclusterA() {
      return a;
    }

    public Cluster getSubclusterB() {
      return b;
    }
  }

  private Set<Sample> samples;
  private List<Cluster> rankedClusters;

  public Dendogram() {
    samples = new HashSet<Sample>();
    rankedClusters = new ArrayList<Cluster>();
  }

  public void add(Sample s) {
    samples.add(s);
  }

  public void computeClusters() {
    List<Cluster> remainingClusters = new ArrayList<Cluster>();
    rankedClusters.clear();
    for (Sample s : samples) {
      Cluster cluster = new Cluster(0, s);
      remainingClusters.add(cluster);
      rankedClusters.add(cluster);
    }
    int nextRank = 1;
    while (remainingClusters.size() > 1) {
      merge(nextRank, remainingClusters);
      nextRank++;
    }
  }

  private static void bug(String what) {
    Debug.out("Dendogram", what);
  }

  private void merge(int nextRank, List<Cluster> remainingClusters) {
    // one iteration of the clustering algorithm. Determine which two clusters are the closest, call
    // them (a) and (b). Make a new cluster (ab), remove (a) and (b) individually from the
    // remainingClusters list, and add (ab).
    double nearestDist = Double.MAX_VALUE;
    Cluster nearestA = null;
    Cluster nearestB = null;
    for (int i = 0; i < remainingClusters.size(); i++) {
      Cluster a = remainingClusters.get(i);
      for (int j = i + 1; j < remainingClusters.size(); j++) {
        Cluster b = remainingClusters.get(j);
        double thisDist = a.dist(b);
        if (thisDist < nearestDist) {
          nearestDist = thisDist;
          nearestA = a;
          nearestB = b;
        }
      }
    }
    if (nearestA != null && nearestB != null) {
      Cluster ab = new Cluster(nextRank, nearestA, nearestB);
      rankedClusters.add(0, ab);
      remainingClusters.remove(nearestA);
      remainingClusters.remove(nearestB);
      remainingClusters.add(ab);
    }
  }

  public Cluster[] getClusters(int n) {
    Cluster[] ret = new Cluster[Math.min(n, rankedClusters.size())];
    for (int i = 0; i < ret.length; i++) {
      ret[i] = rankedClusters.get(i);
    }
    return ret;
  }

  public int size() {
    return samples.size();
  }

  public void search(double[] inputPcaCoordsArray, NBestList nBest) {
    // Look in this dendogram to find suitable matches. Begin at the root cluster. See how far apart
    // the input vector is from the cluster. If the distance is greater than the cluster's
    // radius, that means the input vector can not be found in this cluster, so this and any
    // children will be dead ends.
    // However if the input vector is inside this cluster, two things should happen: add this
    // cluster to the nBest list, and then descend into both children, if there are any.
    Stack<Cluster> todo = new Stack<Cluster>();
    todo.push(rankedClusters.get(0));
    int numAvoided = 0;
    while (!todo.isEmpty()) {
      Cluster cluster = todo.pop();
      double dist = Sample.pcaSquaredDist(inputPcaCoordsArray, cluster.exemplar.getPCACoordinate());
      if (nBest.size() == 0 || dist < nBest.getWorst().score) {
        nBest.addScore(cluster.exemplar, dist);
        if (cluster.a != null) {
          todo.push(cluster.a);
        }
        if (cluster.b != null) {
          todo.push(cluster.b);
        }
      } else {
        numAvoided = numAvoided + cluster.points.length;
      }
    }
  }

  public double getRadius() {
    return rankedClusters.get(0).radius;
  }
}
