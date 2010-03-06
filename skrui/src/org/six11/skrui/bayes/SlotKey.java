package org.six11.skrui.bayes;

/**
 * This refers to a specific value of a random variable. For example, there may be a random variable
 * that tells you what the sky looks like. It could have four possible values: 'Clear', 'Partly
 * Cloudy', 'Mostly Cloudy', and 'Overcast'. Each of those values has an index, 0..3. This class
 * simply serves as a tuple that lets you speak about a specific variable's slots
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class SlotKey {

  Node node;
  int index;

  public SlotKey(Node node, int i) {
    this.node = node;
    this.index = i;
  }

}
