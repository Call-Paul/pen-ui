package org.six11.slippy.example;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.six11.olive.DiskEnvironment;
import org.six11.slippy.Environment;
import org.six11.slippy.SlippyInterpreter;
import org.six11.slippy.SlippyMachine;
import org.six11.slippy.SlippyObject;
import org.six11.slippy.SlippyObjectType;
import org.six11.slippy.Thing;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Example {

  public static void main(String[] args) throws FileNotFoundException, IOException {
    // This is a simple example of how Slippy and Java can communicate.
    if (args.length != 2) {
      bug("param 1: path to root of slippy code. param 2: name of main slippy class to run.");
      bug("Example: run Example src org.six11.slippy.example.Foo");
      System.exit(0);
    }
    String loadPath = args[0];
    String className = args[1];
    SlippyInterpreter interp = new SlippyInterpreter();
    SlippyMachine machine = interp.getMachine();
    Environment env = new DiskEnvironment(loadPath);
    machine.setEnvironment(env);// machine.setLoadPath(loadPath);

    int stop = Math.max(0, className.lastIndexOf('.') + 1);
    String baseName = className.substring(stop);
    String codesetStr = stop > 0 ? className.substring(0, stop - 1) : className;
    Thing foo = interp.importFromFile(baseName, codesetStr);
    bug("Foo is of type: " + foo.type);
    SlippyObjectType type = new SlippyObjectType(foo, interp);
    bug("Made my java bound object.");
    bug("\tSlippy type   : " + type.getTypeName());
    bug("\tSlippy codeset: " + type.getCodeset());
    List<String> symbolNames = type.getSymbolNames();
    bug("\tSlippy symbols: (" + symbolNames.size() + " total)");

    for (String s : symbolNames) {
      bug("\t\t" + s);
    }

    bug("Making instance of " + type.getTypeName());
    SlippyObject obj = type.make(new ArrayList<Object>());
    symbolNames = obj.getSymbolNames();
    bug("Slippy object symbols: ( " + symbolNames.size() + ")");
    for (String s : symbolNames) {
      bug("\t" + s);
    }

    bug("Calling function 'go(2,5)' on that instance.");
    Thing v = obj.call("go", 2, 5);
    bug("'go' returns: " + v + " (of type: " + v.type + ")");

    SampleJavaBinding b = new SampleJavaBinding(interp);
    bug("Made a binding: " + b);
    bug("\tSlippy type: " + b.getTypeName());
    bug("\tSlippy codeset: " + b.getCodeset());
    symbolNames = b.getSymbolNames();
    bug("Bound object's symbols: (" + symbolNames.size() + ")");
    for (String s : symbolNames) {
      bug("\t" + s);
    }
    bug("Running the 'UseBinding.slippy' file...");
    interp.runFile(interp.getMachine().getEnvironment().getLoadPath() + File.separator
        + "org/six11/slippy/example/UseBinding.slippy");
    bug("'UseBinding.slippy' complete.");
  }

  private static void bug(String what) {
    System.out.println(what);
  }
}
