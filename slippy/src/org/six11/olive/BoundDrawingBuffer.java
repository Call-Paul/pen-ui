package org.six11.olive;

//import java.io.ByteArrayOutputStream;
import java.util.List;

//import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;

import org.six11.olive.DrawingBuffer;
import org.six11.slippy.SlippyInterpreter;
import org.six11.slippy.SlippyJavaClass;
//import org.six11.slippy.SlippyMachine;
import org.six11.slippy.SlippyUtils;
import org.six11.slippy.Thing;
import org.six11.slippy.SymbolTable;
import org.six11.util.Debug;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class BoundDrawingBuffer extends SlippyJavaClass {

  public static int ID_COUNTER = 1;

  public BoundDrawingBuffer(SlippyInterpreter interp) {
    super("BoundDrawingBuffer", interp.getMachine().getCodeset("org.six11.olive"),
        "BoundDrawingBuffer.java", interp);
    addSymbol(new Thing.Function("init", SlippyUtils.mkList(), "BoundDrawingBuffer.java") {
      public Thing eval(List<Thing> paramValues, SymbolTable context) {
        DrawingBuffer b = new DrawingBuffer();
        context.setThing("buffer", new Thing.JavaObject(b));
        context.setThing("id", new Thing.Str("" + ID_COUNTER++));
        return Thing.NIL;
      }
    });

    addSymbol(new Thing.Function("turn", SlippyUtils.mkList("v"), "BoundDrawingBuffer.java") {
      public Thing eval(List<Thing> paramValues, SymbolTable context) {
        buffer(context).turn(SlippyUtils.toDouble(paramValues.get(0).toString()));
        return Thing.NIL;
      }
    });

    addSymbol(new Thing.Function("forward", SlippyUtils.mkList("v"), "BoundDrawingBuffer.java") {
      public Thing eval(List<Thing> paramValues, SymbolTable context) {
        buffer(context).forward(SlippyUtils.toDouble(paramValues.get(0).toString()));
        return Thing.NIL;
      }
    });

    addSymbol(new Thing.Function("up", SlippyUtils.mkList(), "BoundDrawingBuffer.java") {
      public Thing eval(List<Thing> paramValues, SymbolTable context) {
        buffer(context).up();
        return Thing.NIL;
      }
    });

    addSymbol(new Thing.Function("down", SlippyUtils.mkList(), "BoundDrawingBuffer.java") {
      public Thing eval(List<Thing> paramValues, SymbolTable context) {
        buffer(context).down();
        return Thing.NIL;
      }
    });

    addSymbol(new Thing.Function("up", SlippyUtils.mkList(), "BoundDrawingBuffer.java") {
      public Thing eval(List<Thing> paramValues, SymbolTable context) {
        buffer(context).up();
        return Thing.NIL;
      }
    });

    addSymbol(new Thing.Function("moveTo", SlippyUtils.mkList("x", "y"), "BoundDrawingBuffer.java") {
      public Thing eval(List<Thing> paramValues, SymbolTable context) {
        buffer(context).moveTo(SlippyUtils.toDouble(paramValues.get(0).toString()),
            SlippyUtils.toDouble(paramValues.get(1).toString()));
        return Thing.NIL;
      }
    });

    addSymbol(new Thing.Function("setColor", SlippyUtils.mkList("r", "g", "b", "a"),
        "BoundDrawingBuffer.java") {
      public Thing eval(List<Thing> paramValues, SymbolTable context) {
        buffer(context).setColor(SlippyUtils.toDouble(paramValues.get(0).toString()),
            SlippyUtils.toDouble(paramValues.get(1).toString()),
            SlippyUtils.toDouble(paramValues.get(2).toString()),
            SlippyUtils.toDouble(paramValues.get(3).toString()));
        return Thing.NIL;
      }
    });

    addSymbol(new Thing.Function("setThickness", SlippyUtils.mkList("t"), "BoundDrawingBuffer.java") {
      public Thing eval(List<Thing> paramValues, SymbolTable context) {
        buffer(context).setThickness(SlippyUtils.toDouble(paramValues.get(0).toString()));
        return Thing.NIL;
      }
    });

    addSymbol(new Thing.Function("setFillColor", SlippyUtils.mkList("r", "g", "b", "a"),
        "BoundDrawingBuffer.java") {
      public Thing eval(List<Thing> paramValues, SymbolTable context) {
        buffer(context).setFillColor(SlippyUtils.toDouble(paramValues.get(0).toString()),
            SlippyUtils.toDouble(paramValues.get(1).toString()),
            SlippyUtils.toDouble(paramValues.get(2).toString()),
            SlippyUtils.toDouble(paramValues.get(3).toString()));
        return Thing.NIL;
      }
    });

    addSymbol(new Thing.Function("setFilling", SlippyUtils.mkList("f"), "BoundDrawingBuffer.java") {
      public Thing eval(List<Thing> paramValues, SymbolTable context) {
        buffer(context).setFilling(SlippyUtils.toTruth(paramValues.get(0).toString()));
        return Thing.NIL;
      }
    });
    
//    addSymbol(new Thing.Function("savePDF", SlippyUtils.mkList("filename"), "BoundDrawingBuffer.java") {
//      public Thing eval(List<Thing> paramValues, SymbolTable context) {
//        ByteArrayOutputStream byteOS = new ByteArrayOutputStream();
//        buffer(context).generatePdf(byteOS);
//        String asText = Base64.encode(byteOS.toByteArray());
//        SlippyMachine.outputStream.println("I have the following PDF, encoded in base 64...");
//        SlippyMachine.outputStream.println(asText);
//        return Thing.NIL;
//      }
//    });
  }

  private DrawingBuffer buffer(SymbolTable context) {
    return (DrawingBuffer) ((JavaObject) context.getThing("buffer")).obj;
  }

  @SuppressWarnings("unused")
  private static void bug(String what) {
    Debug.out("BoundDrawingBuffer", what);
  }
}
