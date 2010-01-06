// $Id$

package org.six11.olive;

import java.awt.BorderLayout;
import java.io.IOException;
import java.net.MalformedURLException;

import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.gui.ApplicationFrame;

public class Main {

  public static final String VERSION = "$Id$";

  public static void main(String[] in) {
    ApplicationFrame af = new ApplicationFrame("Olive");
    af.setLayout(new BorderLayout());
    af.setSize(800, 600);
    af.center();

    Arguments args = new Arguments();
    args.addFlag("load-path", ArgType.ARG_REQUIRED, ValueType.VALUE_REQUIRED,
        "Specifies the root load path for Slippy code.");
    args.addFlag("help", ArgType.ARG_OPTIONAL, ValueType.VALUE_IGNORED, "Shows documentation.");
    args.addFlag("v", ArgType.ARG_OPTIONAL, ValueType.VALUE_IGNORED, "Display version.");
    args.addPositional(0, "fqClass", ValueType.VALUE_OPTIONAL,
        "The fully qualified slippy class to load.");
    args.parseArguments(in);
    if (args.hasFlag("help")) {
      System.out.println(args.getDocumentation());
      System.exit(0);
    }
    if (args.hasFlag("v")) {
      System.out.println(VERSION);
      System.exit(0);
    }
    try {
      args.validate();
    } catch (IllegalArgumentException ex) {
      System.out.println(args.getUsage());
      System.exit(-1);
    }

    String loadPath = args.hasValue("load-path") ? args.getValue("load-path") : ".";
    OliveIDE ide = new OliveIDE(new DiskEnvironment(loadPath));
    ide.attachKeyListener(af.getRootPane());
    ide.setBackground(java.awt.Color.RED);
    af.add(ide, BorderLayout.CENTER);
    af.setVisible(true);
    if (args.hasValue("fqClass")) {
      try {
        ide.openBuffer(args.getValue("fqClass"));
      } catch (MalformedURLException ex) {
        ex.printStackTrace();
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    }
  }
}
