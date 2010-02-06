package org.six11.skrui;

import java.io.IOException;

import org.six11.util.Debug;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public abstract class Batch {

  // batch.sh 2.0 0.75 20 foo.pdf
  // batch.sh [curve-median-multiplier] [speed-multiplier] [spline-thresh] [pdf-file]
  public static void main(String[] args) {
    double curveMedianMin = 1.5;
    double curveMedianStep = 0.1;
    double curveMedianMax = 5.0;
    double speedMin = 0.6;
    double speedStep = 0.05;
    double speedMax = 1;
    double splineThreshMin = 5;
    double splineThreshStep = 5;
    double splineThreshMax = 100;

    int round = 1;

    double curveMedian = curveMedianMin;
    double speed = speedMin;
    double splineThresh = splineThreshMin;

    while (curveMedian <= curveMedianMax) {
      speed = speedMin;
      while (speed <= speedMax) {
        splineThresh = splineThreshMin;
        while (splineThresh <= splineThreshMax) {
          StringBuilder buf = new StringBuilder();
          buf.append("./batch.sh ");
          buf.append(Debug.num(curveMedian) + " ");
          buf.append(Debug.num(speed) + " ");
          buf.append(Debug.num(splineThresh) + " ");
          buf.append(round + ".pdf");
          Process p;
          try {
            System.out.println(buf.toString());
            p = Runtime.getRuntime().exec(buf.toString());
            int result = p.waitFor();
            if (result != 0) {
              System.out.println("Batch exited abnormally with status " + result + " on command: "
                  + buf.toString());
              System.out.println("Quitting.");
              System.exit(result);
            }
          } catch (IOException ex) {
            ex.printStackTrace();
            System.exit(-1);
          } catch (InterruptedException ex) {
            ex.printStackTrace();
            System.exit(-1);
          }
          round = round + 1;
          splineThresh = splineThresh + splineThreshStep;
        }
        speed = speed + speedStep;
      }
      curveMedian = curveMedian + curveMedianStep;
    }
  }

}
