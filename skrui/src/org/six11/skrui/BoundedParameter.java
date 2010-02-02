package org.six11.skrui;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class BoundedParameter {

  private String keyName;
  private String humanReadableName;
  private String documentation;
  private double lowerBound;
  private double upperBound;
  private double value;

  public BoundedParameter(String keyName, String humanReadableName, String documentation,
      double lowerBound, double upperBound, double value) {
    this.keyName = keyName;
    this.humanReadableName = humanReadableName;
    this.documentation = documentation;
    this.lowerBound = lowerBound;
    this.upperBound = upperBound;
    this.value = value;
    validate();
  }

  public String getKeyName() {
    return keyName;
  }

  public void setKeyName(String keyName) {
    this.keyName = keyName;
  }

  public String getHumanReadableName() {
    return humanReadableName;
  }

  public void setHumanReadableName(String humanReadableName) {
    this.humanReadableName = humanReadableName;
  }

  public String getDocumentation() {
    return documentation;
  }

  public void setDocumentation(String documentation) {
    this.documentation = documentation;
  }

  public double getLowerBound() {
    return lowerBound;
  }

  public void setLowerBound(double lowerBound) {
    this.lowerBound = lowerBound;
    validate();
  }

  public double getUpperBound() {
    return upperBound;
  }

  public void setUpperBound(double upperBound) {
    this.upperBound = upperBound;
    validate();
  }

  public double getValue() {
    return value;
  }

  public void setValue(double value) {
    this.value = value;
    validate();
  }

  private final void validate() {
    if (value > upperBound) {
      value = upperBound;
    }
    if (value < lowerBound) {
      value = lowerBound;
    }
  }

  public BoundedParameter copy() {
    return new BoundedParameter(keyName, humanReadableName, documentation, lowerBound, upperBound,
        value);
  }
}
