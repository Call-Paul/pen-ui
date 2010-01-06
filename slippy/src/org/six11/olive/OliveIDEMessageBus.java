package org.six11.olive;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.six11.slippy.MessageBus;
import org.six11.slippy.SymbolTable;
import org.six11.slippy.Thing;
import org.six11.util.Debug;

/**
 * A simple bus for putting messages (Strings) that are routed to registered listeners.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class OliveIDEMessageBus extends MessageBus {

  private Map<String, SignalHandler> signalHandlers;
  private OliveIDE ide;

  /**
   * Creates the message bus and attach it with the given applet.
   */
  public OliveIDEMessageBus(final OliveIDE applet) {
    this.setIDE(applet);
    signalHandlers = new HashMap<String, SignalHandler>();

    signalHandlers.put("addPoint", new SignalHandler() {
      public void handle(List<Thing> args) {
        applet.getSoup().drawCurrentSequence((Thing.Array) args.get(0));
        applet.getDrawingSurface().repaint();
      }
    });

    signalHandlers.put("clear log", new SignalHandler() {
      public void handle(List<Thing> args) {
        // applet.logClear();
        applet.getDrawingSurface().repaint();
      }
    });

    signalHandlers.put("addBuffer", new SignalHandler() {
      public void handle(List<Thing> args) {
        Thing.JavaObject t = (Thing.JavaObject) args.get(0);
        applet.getSoup().forgetCurrentSequence();
        DrawingBuffer buf = (DrawingBuffer) t.obj;
        applet.getSoup().addBuffer(buf);
        applet.getDrawingSurface().repaint();
      }
    });

    signalHandlers.put("clearBuffers", new SignalHandler() {
      public void handle(List<Thing> args) {
        applet.getSoup().clearBuffers();
        applet.getDrawingSurface().repaint();
      }
    });
  }

  private static interface SignalHandler {
    void handle(List<Thing> args);
  }

  public void relay(List<Thing> paramValues, SymbolTable context) {
    if (paramValues.size() > 0) {
      String kind = paramValues.get(0).toString();
      List<Thing> rest = paramValues.subList(1, paramValues.size());
      if (signalHandlers.containsKey(kind)) {
        signalHandlers.get(kind).handle(rest);
      }
    }
  }

  @SuppressWarnings("unused")
  private static void bug(String what) {
    Debug.out("OliveAppletMessageBus", what);
  }

  public void setIDE(OliveIDE ide) {
    this.ide = ide;
  }

  public OliveIDE getIDE() {
    return ide;
  }
}
