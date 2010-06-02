package org.six11.skrui.charrec;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.six11.skrui.shape.Stroke;
import org.six11.util.Debug;
import org.six11.util.data.Statistics;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;
import org.six11.util.math.PCA;

public class OuyangRecognizer {

  public static int DOWNSAMPLE_GRID_SIZE = 12;

  private List<Callback> friends;
  private static Vec zero = new Vec(1, 0);
  private static Vec fortyFive = new Vec(1, 1).getUnitVector();
  private static Vec ninety = new Vec(0, 1);
  private static Vec oneThirtyFive = new Vec(-1, 1).getUnitVector();
  private Map<String, List<Sample>> symbols;
  private int numSymbols;
  private int nextID;
  private File corpus;

  public OuyangRecognizer() {
    friends = new ArrayList<Callback>();
    symbols = new HashMap<String, List<Sample>>();
    numSymbols = 0;
    nextID = 1;
  }

  public interface Callback {
    public void recognitionBegun();

    public void recognitionComplete(double[] present, double[] endpoint, double[] dir0,
        double[] dir1, double[] dir2, double[] dir3, NBestList nBestList);
  }

  public void addCallback(Callback friend) {
    friends.add(friend);
  }

  public void recognize(List<Stroke> strokes) {
    long start = System.currentTimeMillis();
    for (Callback c : friends) {
      c.recognitionBegun();
    }
    List<List<Pt>> normalized = getNormalizedStrokes(strokes);
    Statistics xData = new Statistics();
    Statistics yData = new Statistics();
    for (List<Pt> seq : normalized) {
      computeInitialFeatureValues(seq, xData, yData);
    }
    double xMean = xData.getMean();
    double yMean = yData.getMean();
    double scaleFactor = 1 / (2 * Math.max(xData.getStdDev(), yData.getStdDev()));

    for (List<Pt> seq : normalized) {
      for (Pt pt : seq) {
        pt.setLocation(scaleFactor * (pt.getX() - xMean), scaleFactor * (pt.getY() - yMean));
      }
    }

    // Populate the 24x24 feature images. Each point in the normalized, transformed list maps to one
    // of these grid locations (unless either x or y coordinate is beyond a threshold, meaning it is
    // too many standard deviations away from the mean.
    int gridSize = 24;
    int arraySize = gridSize * gridSize;
    double[] present = new double[arraySize];
    double[] endpoint = new double[arraySize];
    double[] dir0 = new double[arraySize];
    double[] dir1 = new double[arraySize];
    double[] dir2 = new double[arraySize];
    double[] dir3 = new double[arraySize];
    double t = 1.3;
    for (List<Pt> seq : normalized) {
      for (Pt pt : seq) {
        double percentX = getPercent(-t, t, pt.getX());
        double percentY = getPercent(-t, t, pt.getY());
        if (percentX < 1 && percentY < 1) {
          int gridX = getGridIndex(0, gridSize, percentX);
          int gridY = getGridIndex(0, gridSize, percentY);
          int idx = gridY * 24 + gridX;
          if (idx >= 0 && idx < arraySize) { // rarely a point will be outside the range.
            present[idx] = 1;
            endpoint[idx] = Math.max(endpoint[idx], pt.getDouble("endpoint"));
            dir0[idx] = Math.max(dir0[idx], pt.getDouble("dir0"));
            dir1[idx] = Math.max(dir1[idx], pt.getDouble("dir1"));
            dir2[idx] = Math.max(dir2[idx], pt.getDouble("dir2"));
            dir3[idx] = Math.max(dir3[idx], pt.getDouble("dir3"));
          }
        }
      }
    }
    checkNaN(endpoint);
    checkNaN(dir0);
    checkNaN(dir1);
    checkNaN(dir2);
    checkNaN(dir3);

    // Blur each feature image with a gaussian kernel
    double[] karl = getGaussianBlurKernel(3, 1.0);
    blur(present, karl);
    blur(endpoint, karl);
    blur(dir0, karl);
    blur(dir1, karl);
    blur(dir2, karl);
    blur(dir3, karl);
    present = downsample(present);
    endpoint = downsample(endpoint);
    dir0 = downsample(dir0);
    dir1 = downsample(dir1);
    dir2 = downsample(dir2);
    dir3 = downsample(dir3);
    double[] input = makeMasterVector(endpoint, dir0, dir1, dir2, dir3);

    // Now we have the feature images for the recently drawn symbol. Try to match it against the
    // symbols we know about.
    NBestList nBestList = new NBestList(true);
    Statistics scores = new Statistics();
    // double bestScore = Double.MAX_VALUE;
    int numSymbols = 0;
    for (String key : symbols.keySet()) {
      List<Sample> inst = symbols.get(key);
      for (int instIdx = 0; instIdx < inst.size(); instIdx++) {
        Sample sample = inst.get(instIdx); // data is 5x the length of the feature images.
        double[] data = sample.getData();
        int len = endpoint.length; // all feature images are the same length
        double[] dataPatch = new double[9];
        double[] inputPatch = new double[9];
        double sumOfMinScores = 0;
        for (int i = 0; i < len; i++) {
          // compare the 3x3 patch around input[i] with the nine patches in the vicinity of data[i]
          // do this for all five feature images.
          double minScore = Double.MAX_VALUE;
          for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
              double patchDifference = 0;
              for (int featureNumber = 0; featureNumber < 5; featureNumber++) {
                fillPatch(input, featureNumber, len, i, 0, 0, inputPatch);
                fillPatch(data, featureNumber, len, i, dx, dy, dataPatch);
                double diff = comparePatches(inputPatch, dataPatch);
                patchDifference = patchDifference + diff;
              }
              minScore = Math.min(minScore, patchDifference);
            }
          }
          sumOfMinScores = sumOfMinScores + minScore;
        }
        if (Double.isNaN(sumOfMinScores)) {
          bug("Got NaN comparing these vectors: ");
          bug(Debug.num(input));
          bug(Debug.num(data));
        }
        numSymbols++;
        nBestList.addScore(key, sumOfMinScores);
        scores.addData(sumOfMinScores);
      }
    }

    for (Callback c : friends) {
      c.recognitionComplete(present, endpoint, dir0, dir1, dir2, dir3, nBestList);
    }
    long finish = System.currentTimeMillis();
    long elapsed = finish - start;
    double perSym = (double) elapsed / (double) numSymbols;
    bug("Took " + elapsed + " ms total to examine " + numSymbols + " symbols. ("
        + Debug.num(perSym) + " ms per symbol)");
  }

  private void checkNaN(double[] numbers) {
    for (int i = 0; i < numbers.length; i++) {
      if (Double.isNaN(numbers[i])) {
        new RuntimeException("Found NaN. Check stacktrace.").printStackTrace();
        System.exit(0);
      }
    }
  }

  private double comparePatches(double[] srcPatch, double[] destPatch) {
    double sum = 0;
    for (int i = 0; i < srcPatch.length; i++) {
      double diff = srcPatch[i] - destPatch[i];
      sum = sum + (diff * diff);
    }
    return sum;
  }

  /**
   * 
   * @param data
   *          the 720-element long vector of data for a single sample. It is composed of the five
   *          feature images that are 144 cells. Think of them as being arranged in a 12x12 grid.
   * @param featureNumber
   *          Indicates the particular feature image to read. This should be [0..5).
   * @param featureIndex
   *          Indexes a cell inside a particular feature image in the range [0..144)
   * @param dx
   *          An offset in the x dimension within a 12x12 grid. In the range [-1..1].
   * @param dy
   *          Like dx but for y.
   * @param patch
   *          The nine values in the 3x3 vicinity of the target pixel are put here.
   */
  private void fillPatch(double[] data, int featureNumber, int featureLength, int featureIndex,
      int dx, int dy, double[] patch) {
    // bug("fillPatch(double[" + data.length + "]@" + data.hashCode() + ", " + featureNumber + ", "
    // + featureLength + ", " + featureIndex + ", " + dx + ", " + dy + ", double[" + patch.length
    // + "]@" + patch.hashCode());

    int base = featureNumber * featureLength; // a multiple of 144
    int nextBase = base + featureLength;
    int gridSize = (int) Math.rint(Math.sqrt(featureLength));
    int c = base + featureIndex;
    int t = c + dx + (gridSize * dy);

    int patchIdx = 0;
    for (int i = -1; i <= 1; i++) {
      for (int j = -1; j <= 1; j++) {
        int pixel = t + i + (gridSize * j);
        if (pixel < base || pixel >= nextBase) { // out of bounds for this 12x12 grid
          patch[patchIdx] = 0;
        } else {
          patch[patchIdx] = data[pixel];
        }
        patchIdx = patchIdx + 1;
      }
    }
  }

  private double[] downsample(double[] in) {
    int inN = (int) Math.rint(Math.sqrt(in.length));
    int outN = inN / 2;
    double[] ret = new double[outN * outN];
    int retIdx = 0;
    for (int i = 0; i < in.length; i++) {
      int x = i % inN;
      int y = i / inN;
      if (x % 2 == 0 && y % 2 == 0) {
        double v = Statistics.maximum(//
            val(in, inN, x, y), //
            val(in, inN, x + 1, y), //
            val(in, inN, x, y + 1), //
            val(in, inN, x + 1, y + 1));
        ret[retIdx++] = v;
      }
    }
    return ret;
  }

  private double val(double[] in, int inN, int x, int y) {
    return in[(y * inN) + x];
  }

  private double getPercent(double lower, double upper, double sample) {
    return (sample - lower) / (upper - lower);
  }

  private int getGridIndex(double lower, double upper, double percent) {
    return (int) Math.floor(lower + (percent * (upper - lower)));
  }

  private void computeInitialFeatureValues(List<Pt> seq, Statistics xData, Statistics yData) {
    // set the four direction features for all non-endpoints, and keep stats on where points are
    boolean repair = false;
    for (int i = 1; i < seq.size() - 1; i++) {
      Pt prev = seq.get(i - 1);
      Pt here = seq.get(i);
      Pt next = seq.get(i + 1);
      here.setDouble("endpoint", 0.0); // record that this is not an endpoint
      Vec dir = new Vec(prev, next).getUnitVector();
      if (Double.isNaN(dir.getX()) || Double.isNaN(dir.getY())) {
        // rarely, prev and next are the same point. So leave the values null for now, and
        // fill it in later using the average of neighbors.
        repair = true;
      } else {
        computeAngle(here, dir, zero, "dir0");
        computeAngle(here, dir, fortyFive, "dir1");
        computeAngle(here, dir, ninety, "dir2");
        computeAngle(here, dir, oneThirtyFive, "dir3");
      }
      xData.addData(here.getX());
      yData.addData(here.getY());
    }
    // cheat a little and copy the direction feature values to the endpoints
    copyAttribs(seq.get(seq.size() - 2), seq.get(seq.size() - 1), "dir0", "dir1", "dir2", "dir3");
    copyAttribs(seq.get(1), seq.get(0), "dir0", "dir1", "dir2", "dir3");

    // repair damage from the rare NaN silliness.
    if (repair) {
      bug("Repairing damage from NaN silliness...");
      String[] names = new String[] {
          "dir0", "dir1", "dir2", "dir3"
      };
      for (int i = 1; i < seq.size() - 1; i++) {
        Pt pt = seq.get(i);
        for (String name : names) {
          if (!pt.hasAttribute(name)) {
            bug(" ... repairing " + name);
            double prevVal = seq.get(i - 1).getDouble(name);
            double nextVal = seq.get(i + 1).getDouble(name);
            pt.setDouble(name, (prevVal + nextVal) / 2);
          }
        }
      }
    }
    // record the first and last points as endpoints.
    seq.get(0).setDouble("endpoint", 1.0);
    seq.get(seq.size() - 1).setDouble("endpoint", 1.0);

    // also add x/y coords for statistics
    xData.addData(seq.get(0).getX());
    yData.addData(seq.get(0).getY());
    xData.addData(seq.get(seq.size() - 1).getX());
    yData.addData(seq.get(seq.size() - 1).getY());

  }

  private void copyAttribs(Pt src, Pt dst, String... attribNames) {
    for (String attrib : attribNames) {
      dst.setDouble(attrib, src.getDouble(attrib));
    }
  }

  private void computeAngle(Pt pt, Vec dir, Vec cardinal, String attribName) {
    double angle = Functions.getAngleBetween(cardinal, dir);
    if (Math.abs(angle) > Math.PI / 2) {
      dir = dir.getFlip();
      angle = Functions.getAngleBetween(cardinal, dir);
    }
    if (Double.isNaN(angle)) {
      bug("Found NaN in computing angle: " + Debug.num(pt) + ", " + Debug.num(dir) + ", "
          + Debug.num(cardinal) + ", " + attribName);
      System.exit(0);
    }
    double difference = Math.abs(angle);
    double featureValue = Math.max(0, 1 - difference / (Math.PI / 4));
    if (Double.isNaN(featureValue)) {
      bug("Found NaN in computing featureValue: " + Debug.num(pt) + ", " + Debug.num(dir) + ", "
          + Debug.num(cardinal) + ", " + attribName);
      System.exit(0);
    }
    pt.setDouble(attribName, featureValue);
  }

  private static void bug(String what) {
    Debug.out("OuyangRecognizer", what);
  }

  private List<List<Pt>> getNormalizedStrokes(List<Stroke> strokes) {
    List<List<Pt>> ret = new ArrayList<List<Pt>>();
    for (Stroke stroke : strokes) {
      ret.add(Functions.getNormalizedSequence(stroke.getPoints(), 1.0));
    }
    return ret;
  }

  private void blur(double[] in, double[] kernel) {
    int kN = (int) Math.rint(Math.sqrt(kernel.length));
    int inN = (int) Math.rint(Math.sqrt(in.length));
    int h = kN / 2; // e.g. 5 / 2 == 2
    double[] result = new double[in.length];
    for (int inIdx = 0; inIdx < in.length; inIdx++) {
      int inX = inIdx % inN;
      int inY = inIdx / inN;
      double cellValue = 0;
      for (int kIdx = 0; kIdx < kernel.length; kIdx++) {
        int kX = kIdx % kN;
        int kY = kIdx / kN;
        int x = (inX - h) + kX;
        int y = (inY - h) + kY;
        double inV = 0;
        if (x >= 0 && y >= 0 && x < inN && y < inN) {
          inV = in[(y * inN) + x];
        }
        double kV = kernel[kIdx];
        cellValue = cellValue + (inV * kV);
      }
      result[inIdx] = Math.min(Math.max(0, cellValue), 1);
    }
    System.arraycopy(result, 0, in, 0, in.length);
    checkNaN(in);
  }

  private double[] getGaussianBlurKernel(int n, double sigma) {
    double[] ret = new double[n * n];
    int baseNum = (n / 2) - 1;
    double sum = 0;
    for (int i = 0; i < n * n; i++) {
      int xOffset = i % n;
      int yOffset = i / n;
      int xIdx = baseNum + xOffset;
      int yIdx = baseNum + yOffset;
      ret[i] = getGaussianCellValue((double) xIdx, (double) yIdx, sigma);
      sum = sum + ret[i];
    }
    for (int i = 0; i < n * n; i++) {
      ret[i] = ret[i] / sum;
    }
    return ret;
  }

  private double getGaussianCellValue(double x, double y, double sigma) {
    double denom = 2 * Math.PI * Math.pow(sigma, 2);
    double left = 1 / denom;
    double rightDenom = 2 * Math.pow(sigma, 2);
    double rightNumer = Math.pow(x, 2) + Math.pow(y, 2);
    double exponent = -(rightNumer / rightDenom);
    double expVal = Math.exp(exponent);
    double ret = left * expVal;
    return ret;
  }

  public void calculatePrincipleComponents() {
    // Make a mondo-matrix using all the data (!) in the symbol table and get the first 120
    // principle components.
    int featureLength = DOWNSAMPLE_GRID_SIZE * DOWNSAMPLE_GRID_SIZE;
    int allFeatureLength = featureLength * 5;
    double[][] mondo = new double[numSymbols][allFeatureLength];
    int symbolIdx = 0;
    for (String key : symbols.keySet()) {
      for (Sample sample : symbols.get(key)) {
        System.arraycopy(sample.getData(), 0, mondo[symbolIdx], 0, allFeatureLength);
        symbolIdx++;
      }
    }
    PCA pca = new PCA(mondo);
    int totalComps = pca.getNumComponents();
    List<PCA.PrincipleComponent> bestComps = pca.getDominantComponents(120);
    bug("Generated " + totalComps + " principle components.");
    bug("Here are the principle components:");
    int pcCount = 0;
    for (PCA.PrincipleComponent pc : bestComps) {
      System.out.println(pcCount + ", " + Debug.num(pc.eigenValue, 6));
    }

    // TODO: project the original data (mondo) into the new coordinate space and store the result on
    // disk since that is a very long operation to perform.
  }

  public void setCorpus(File f) {
    long start = System.currentTimeMillis();
    corpus = f;
    if (corpus.exists() && corpus.canRead()) {
      try {
        DataInputStream in = new DataInputStream(new FileInputStream(corpus));
        int numRead = 0;
        while (in.available() > 0) {
          Sample sample = Sample.readSample(in);
          remember(sample);
          nextID = Math.max(nextID, sample.getID()) + 1;
          numRead++;
        }
        long finished = System.currentTimeMillis();
        long elapsed = finished - start;
        int s = (int) elapsed / 1000;
        int ms = (int) elapsed % 1000;
        bug("Read " + numRead + " symbols from " + corpus.getAbsolutePath() + " in " + s + " s "
            + ms + " ms");
      } catch (FileNotFoundException ex) {
        ex.printStackTrace();
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    }
  }

  /**
   * Stores a 720-element long feature vector to disk. The format is as follows:
   * 
   * <ul>
   * <li>An integer ID for this particular record. This must be unique among all other records.</li>
   * <li>A UTF string indicating what the feature vector represents, e.g. 'a' or 'xor gate'.
   * (variable length)</li>
   * <li>The length of the array N (should be 720, but conceivably could change)</li>
   * <li>N unsigned short values representing the values [0..2^16). These are indexes into a lookup
   * table that has 2^16 doubles in the range 0..1.</li>
   * </ul>
   * 
   * @param label
   * @param endpoint
   * @param dir0
   * @param dir1
   * @param dir2
   * @param dir3
   */
  public void store(String label, double[] endpoint, double[] dir0, double[] dir1, double[] dir2,
      double[] dir3) {
    double[] master = makeMasterVector(endpoint, dir0, dir1, dir2, dir3);
    Sample thisSample = new Sample(nextID, label, master);
    remember(thisSample);
    if (corpus != null) {
      try {
        DataOutputStream out = new DataOutputStream(new FileOutputStream(corpus, true));
        thisSample.write(out);
      } catch (FileNotFoundException ex) {
        ex.printStackTrace();
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    }
    nextID++;
  }

  private void remember(Sample sample) {
    if (!symbols.containsKey(sample.getLabel())) {
      symbols.put(sample.getLabel(), new ArrayList<Sample>());
    }
    symbols.get(sample.getLabel()).add(sample);
    numSymbols = numSymbols + 1;
  }

  private double[] makeMasterVector(double[] endpoint, double[] dir0, double[] dir1, double[] dir2,
      double[] dir3) {
    int len = endpoint.length;
    double[] master = new double[5 * len];
    System.arraycopy(endpoint, 0, master, len * 0, len);
    System.arraycopy(dir0, 0, master, len * 1, len);
    System.arraycopy(dir1, 0, master, len * 2, len);
    System.arraycopy(dir2, 0, master, len * 3, len);
    System.arraycopy(dir3, 0, master, len * 4, len);
    return master;
  }

  public int getNumSymbols() {
    return numSymbols;
  }
}
