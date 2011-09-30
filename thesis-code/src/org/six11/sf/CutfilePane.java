package org.six11.sf;

import java.awt.Color;
import java.awt.Dimension;

import javax.swing.JPanel;

import org.six11.util.pen.PenEvent;
import org.six11.util.pen.PenListener;

import static org.six11.util.Debug.bug;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class CutfilePane extends JPanel implements GestureListener, PenListener {
  public CutfilePane() {
    setName("CutfilePane");
    setBackground(new Color(250, 240, 200));
    setPreferredSize(new Dimension(300, 200));
  }

  public void gestureComplete(GestureEvent gcev) {
    
  }

  public void gestureStart(GestureEvent ev) {
    // TODO Auto-generated method stub
    
  }

  public void gestureProgress(GestureEvent ev) {
    // TODO Auto-generated method stub
    
  }

  public void handlePenEvent(PenEvent ev) {
    // TODO Auto-generated method stub
    
  }
}
