package org.six11.skrui.charrec;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a single hand-drawn sample that has been analyzed into feature vectors, blurred,
 * downsampled, beaten, and dragged through the streets of Cairo. Actually it has not been beaten.
 * 
 * @author Gabe Johnson
 */
public class Sample implements Comparable<Sample> {

  private static ClampdLookupTable clampd = new ClampdLookupTable();
  private int id;
  private String label;
  private double[] data;
  private double[] dataMinusMean;
  private double[] pcaCoordinate;

  public Sample(int id, String label, double[] master) {
    this.id = id;
    this.label = label;
    this.data = master;
  }

  public int getID() {
    return id;
  }
  
  public String getLabel() {
    return label;
  }
  
  public double[] getData() {
    return data;
  }
  
  public String toString() {
    return "Sample[" + id + ": " + label + "]";
  }

  public static Sample readSample(DataInputStream in) throws IOException {
    int sampleId = in.readInt();
    String sampleLabel = in.readUTF();
    int len = in.readInt();
    double[] master = new double[len];
    for (int i = 0; i < len; i++) {
      int clampdIndex = in.readUnsignedShort();
      master[i] = clampd.get(clampdIndex);
    }
    return new Sample(sampleId, sampleLabel, master);
  }
  
  public void write(DataOutputStream out) throws IOException {
    out.writeInt(id);
    out.writeUTF(label);
    out.writeInt(data.length);
    for (double val : data) {
      int clampdIndex = ClampdLookupTable.getIndexOf(val);
      out.writeShort(clampdIndex);
    }
  }

  public int compareTo(Sample o) {
    int ret = 0;
    if (id < o.id) {
      ret = -1;
    } else if (id > o.id){
      ret = 1;
    }
    return ret;
  }

  public void setPCACoordinate(double[] coord) {
    this.pcaCoordinate = coord;
  }
  
  public double[] getPCACoordinate() {
    return pcaCoordinate;
  }

  public double pcaSquaredDist(double[] otherCoord) {
    return pcaSquaredDist(pcaCoordinate, otherCoord);
  }

  public double pcaSquaredDist(Sample sample) {
    return pcaSquaredDist(pcaCoordinate, sample.pcaCoordinate);
  }
  
  public static double pcaSquaredDist(double[] v1, double[] v2) {
    double sum = 0;
    for (int i=0; i < v1.length; i++) {
      double us = v1[i];
      double them = v2[i];
      double compDist = (them - us) * (them - us); 
      sum = sum + compDist;
    }
    return sum;
  }

  public void setDimensionMeans(double[] dimensionMeans) {
    dataMinusMean = new double[data.length];
    for (int i=0; i < dimensionMeans.length; i++) {
      dataMinusMean[i] = data[i] - dimensionMeans[i];
    }
  }
  
  public double[] getDataMinusMean() {
    return dataMinusMean;
  }
}
