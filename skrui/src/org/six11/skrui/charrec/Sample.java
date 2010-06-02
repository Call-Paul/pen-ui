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
public class Sample {

  private static ClampdLookupTable clampd = new ClampdLookupTable();
  private int id;
  private String label;
  private double[] data;

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
  
}
