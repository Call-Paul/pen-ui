package org.six11.sf;

import java.awt.Color;
import java.awt.Dimension;

import javax.swing.JPanel;
import static org.six11.util.Debug.bug;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class CutfilePane extends JPanel implements GestureListener {
  public CutfilePane() {
    setName("CutfilePane");
    setBackground(new Color(250, 240, 200));
    setPreferredSize(new Dimension(300, 200));
  }

  public void gestureComplete(GestureEvent gcev) {
    bug("GestureListener.gestureComplete not implemented yet!");
    
  }

  @Override
  public void gestureStart(GestureEvent ev) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void gestureProgress(GestureEvent ev) {
    // TODO Auto-generated method stub
    
  }
}
