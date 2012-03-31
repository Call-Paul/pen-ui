package org.six11.sf;

public class TimedMessage {
  
  private String msg;
  private long timeEnd;
  
  public TimedMessage(long timeout, String msg) {
    this.timeEnd = System.currentTimeMillis()  + timeout;
    this.msg = msg;
  }
  
  public boolean isValid() {
    return System.currentTimeMillis() < timeEnd;
  }
  
  public String getMsg() {
    return msg;
  }

}
