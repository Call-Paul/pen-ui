package org.six11.skrui.charrec;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

import Jama.Matrix;

import org.six11.skrui.charrec.NBestList.NBest;
import org.six11.skrui.shape.Stroke;
import org.six11.util.Debug;
import org.six11.util.data.Statistics;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;
import org.six11.util.math.PCA;

public class OuyangRecognizer {

  private static final int PCA_COMPONENTS = 128;

  public static int DOWNSAMPLE_GRID_SIZE = 12;

  private List<Callback> friends;
  private static Vec zero = new Vec(1, 0);
  private static Vec fortyFive = new Vec(1, 1).getUnitVector();
  private static Vec ninety = new Vec(0, 1);
  private static Vec oneThirtyFive = new Vec(-1, 1).getUnitVector();
  private Map<String, List<Sample>> symbols;
  private Matrix pcaSpace; // the pca-derived coordinate system.
  private int pcaSpaceN; // number of samples used to derive pcaSpace.
  private double[] dimensionMeans;
  private Map<String, Dendogram> dendograms;

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

    Set<Sample> topCandidates = null;
    if (pcaSpace != null && dendograms != null) {
      Matrix inputPcaCoords = pcaSpace.times(createColumnVector(input)).transpose();
      double[] inputPcaCoordsArray = inputPcaCoords.getRowPackedCopy();
      topCandidates = searchDendograms(10, inputPcaCoordsArray);
    } else {
      topCandidates = new HashSet<Sample>();
      for (String key : symbols.keySet()) {
        List<Sample> instances = symbols.get(key);
        topCandidates.addAll(instances);
      }
    }
    // Now we have the feature images for the recently drawn symbol. Try to match it against the
    // symbols we know about.

    NBestList nBestList = new NBestList(true, 10);
    Statistics scores = new Statistics();

    for (Sample sample : topCandidates) {
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
      nBestList.addScore(sample, sumOfMinScores);
      scores.addData(sumOfMinScores);
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

  private Set<Sample> searchDendograms(int n, double[] inputPcaCoordsArray) {
    NBestList nBest = new NBestList(true, n);
    for (String label : dendograms.keySet()) {
      Dendogram dendo = dendograms.get(label);
      dendo.search(inputPcaCoordsArray, nBest);
    }
    Set<Sample> bestSamples = new HashSet<Sample>();
    for (NBest b : nBest.getNBest()) {
      bestSamples.add(b.sample);
    }
    return bestSamples;
  }

  private Matrix createColumnVector(double[] input) {
    Matrix ret = new Matrix(input, input.length);
    return ret;
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

  public void loadOrCalculatePrincipleComponents(double[][] mondo, SortedSet<Sample> allSamples,
      File pcaFile) {
    // Make a mondo-matrix using all the data (!) in the symbol table and get the first 120
    // principle components.
    boolean pcaFileOK = loadPrincipleComponents(pcaFile);
    if (!pcaFileOK) {
      calculatePrincipleComponents(mondo, pcaFile);
    } else {
      // have to calculate dimension means, which would have been taken care of if we run
      // calculatePrincipleComponents.
      calculateDimensionMeans(mondo);
    }

    bug("pcaSpace: " + pcaSpace.getRowDimension() + " x " + pcaSpace.getColumnDimension());
    bug("dimensionMeans: " + dimensionMeans.length + " elements");

    // The old way of getting transformed data:
    // Matrix xformedData = pcaSpace.times(adjustedInput).transpose();

    // now each row in xformedData is the coordinate for all samples
    Sample[] allSamplesArray = allSamples.toArray(new Sample[allSamples.size()]);
    bug("allSamplesArray: " + allSamplesArray.length + " elements");
    for (int i = 0; i < allSamplesArray.length; i++) {
      Sample thisSample = allSamplesArray[i];
      calculatePCACoordinate(thisSample);
    }
    bug("Populating dendograms...");
    long dendoStart = System.currentTimeMillis();
    dendograms = new HashMap<String, Dendogram>();
    for (Sample sample : allSamples) {
      if (dendograms.get(sample.getLabel()) == null) {
        dendograms.put(sample.getLabel(), new Dendogram());
      }
      dendograms.get(sample.getLabel()).add(sample);
    }
    List<Dendogram> sortedBySize = new ArrayList<Dendogram>();
    Comparator<Dendogram> dendoSorter = new Comparator<Dendogram>() {
      public int compare(Dendogram o1, Dendogram o2) {
        int ret = 0;
        if (o1.size() > o2.size()) {
          ret = -1;
        } else if (o1.size() < o2.size()) {
          ret = 1;
        }
        return ret;
      }
    };
    for (String key : dendograms.keySet()) {
      Dendogram dendo = dendograms.get(key);
      dendo.computeClusters();
      sortedBySize.add(dendo);
    }
    Collections.sort(sortedBySize, dendoSorter);
    for (Dendogram dendo : sortedBySize) {
      bug(dendo.getClusters(1)[0].exemplar.getLabel() + ": " + dendo.size() + " "
          + makeStars(dendo.size()));
    }

    long dendoEnd = System.currentTimeMillis();
    bug("Done populating " + dendograms.size() + " dendograms (" + (dendoEnd - dendoStart) + " ms)");
  }

  private String makeStars(int size) {
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < size; i++) {
      buf.append("*");
    }
    return buf.toString();
  }

  private void calculatePCACoordinate(Sample sample) {
    sample.setDimensionMeans(dimensionMeans);
    double[] meanAdjustedRowVec = sample.getDataMinusMean();
    Matrix meanAdjustedColVec = new Matrix(meanAdjustedRowVec, meanAdjustedRowVec.length);
    Matrix xformedData = pcaSpace.times(meanAdjustedColVec).transpose();
    sample.setPCACoordinate(xformedData.getArray()[0]);
  }

  private void calculateDimensionMeans(double[][] mondo) {
    int numDimensions = mondo[0].length;
    int numSamples = mondo.length;
    dimensionMeans = new double[numDimensions];

    for (int dimIdx = 0; dimIdx < numDimensions; dimIdx++) {
      for (int sampIdx = 0; sampIdx < numSamples; sampIdx++) {
        dimensionMeans[dimIdx] = dimensionMeans[dimIdx] + mondo[sampIdx][dimIdx];
      }
    }
    for (int i = 0; i < dimensionMeans.length; i++) {
      dimensionMeans[i] = dimensionMeans[i] / numSamples;
    }
  }

  private void calculatePrincipleComponents(double[][] mondo, File pcaFile) {
    bug("Performing PCA on " + mondo.length + " records with " + mondo[0].length + " dimensions...");
    
    // 1. calculate all the principle components
    PCA pca = new PCA(mondo);
    
    // 2. Retain only the "best" components (that have the greatest variance)
    List<PCA.PrincipleComponent> bestComps = pca.getDominantComponents(PCA_COMPONENTS);
    
    // 3. Store the dimension means
    dimensionMeans = pca.getMeans();
    
    // 4. Set the pca coordinate space and save it to disk
    pcaSpace = PCA.getDominantComponentsMatrix(bestComps).transpose();
    if (pcaFile != null) {
      writePCACoordinateSystem(mondo.length, pcaFile);
    }
    bug("... done");
  }

  private boolean loadPrincipleComponents(File pcaFile) {
    boolean pcaFileOK = false;

    if (pcaFile != null && pcaFile.canRead()) {
      // int (numOrigRecords), int (row), int (col), double* (data)
      try {
        DataInputStream pcaIn = new DataInputStream(new FileInputStream(pcaFile));
        pcaSpaceN = pcaIn.readInt();
        if (needPCAUpdate()) {
          bug("Loading PCA coordinates isn't an option because the corpus has grown too much.");
          pcaFileOK = false;
        } else {
          int rows = pcaIn.readInt();
          int cols = pcaIn.readInt();
          double[][] pcaSpaceData = new double[rows][cols];
          for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
              pcaSpaceData[i][j] = pcaIn.readDouble();
            }
          }
          pcaSpace = new Matrix(pcaSpaceData);
          pcaFileOK = true;
        }
        pcaIn.close();
      } catch (FileNotFoundException ex) {
        ex.printStackTrace();
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    }
    return pcaFileOK;
  }

  private void writePCACoordinateSystem(int numOriginalRecords, File pcaFile) {
    bug("Writing " + pcaSpace.getRowDimension() + " x " + pcaSpace.getColumnDimension()
        + " PCA coordinate space to disk...");
    try {
      DataOutputStream pcaOut = new DataOutputStream(new FileOutputStream(pcaFile));
      pcaOut.writeInt(numOriginalRecords);
      pcaOut.writeInt(pcaSpace.getRowDimension());
      pcaOut.writeInt(pcaSpace.getColumnDimension());
      double[][] rawPca = pcaSpace.getArray();
      for (int i = 0; i < rawPca.length; i++) {
        for (int j = 0; j < rawPca[i].length; j++) {
          pcaOut.writeDouble(rawPca[i][j]);
        }
      }
      pcaOut.close();
      bug("Wrote " + pcaOut.size() + " bytes to PCA file " + pcaFile.getAbsolutePath());
    } catch (FileNotFoundException ex) {
      bug("Can not find file: " + pcaFile.getAbsolutePath()
          + ": so I am not writing pca data to disk.");
      ex.printStackTrace();
    } catch (IOException ex) {
      bug("File " + pcaFile.getAbsolutePath() + " exists but I can not write to it.");
      ex.printStackTrace();
    }
  }

  public double[][] makeMondo() {
    int featureLength = DOWNSAMPLE_GRID_SIZE * DOWNSAMPLE_GRID_SIZE;
    int allFeatureLength = featureLength * 5;
    double[][] mondo = new double[numSymbols][allFeatureLength];
    return mondo;
  }

  public SortedSet<Sample> fillMondoData(double[][] mondo) {
    int allFeatureLength = mondo[0].length;
    int symbolIdx = 0;
    SortedSet<Sample> allSamples = new TreeSet<Sample>();
    for (String key : symbols.keySet()) {
      for (Sample sample : symbols.get(key)) {
        allSamples.add(sample);
      }
    }
    for (Sample s : allSamples) {
      System.arraycopy(s.getData(), 0, mondo[symbolIdx], 0, allFeatureLength);
      symbolIdx++;
    }
    return allSamples;
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
    if (pcaSpace != null && dimensionMeans != null) {
      if (needPCAUpdate()) {
        bug("The corpus has grown more than 10% since the PCA coordinates were last calculated.");
        bug("Restarting the application would make things happier.");
        bug("pcaSpaceN: " + pcaSpaceN);
        bug("numSymbols: " + numSymbols);
        bug("numSymbols * 1.1: " + (numSymbols * 1.1));
      }
      calculatePCACoordinate(sample);
      if (dendograms.get(sample.getLabel()) == null) {
        dendograms.put(sample.getLabel(), new Dendogram());
      }
      dendograms.get(sample.getLabel()).add(sample);
      dendograms.get(sample.getLabel()).computeClusters();
    }
    symbols.get(sample.getLabel()).add(sample);
    numSymbols = numSymbols + 1;
  }

  private boolean needPCAUpdate() {
    return (pcaSpaceN * 1.1) < numSymbols;
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
