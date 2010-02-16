package org.six11.skrui.script;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;

import org.six11.skrui.BoundedParameter;
import org.six11.skrui.DrawingBufferRoutines;
import org.six11.skrui.SkruiScript;
import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.SequenceEvent;
import org.six11.util.pen.SequenceListener;

/**
 * A script that lets callers make animations.
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class Animation extends SkruiScript implements SequenceListener {

  private static final String K_ANIMATION_DIR = "animation-dir";

  private static final String K_RECORD = "anim-record";

  public static Arguments getArgumentSpec() {
    Arguments args = new Arguments();
    args.setProgramName("Provides animation support.");
    args.setDocumentationProgram("Gives other scripts the ability to create sequences of images "
        + "for animation purposes.");

    Map<String, BoundedParameter> defs = getDefaultParameters();
    for (String k : defs.keySet()) {
      BoundedParameter p = defs.get(k);
      args.addFlag(p.getKeyName(), ArgType.ARG_OPTIONAL, ValueType.VALUE_REQUIRED, p
          .getDocumentation()
          + " Defaults to " + p.getValueStr() + ". ");
    }
    return args;
  }

  public static Map<String, BoundedParameter> getDefaultParameters() {
    Map<String, BoundedParameter> defs = new HashMap<String, BoundedParameter>();
    defs.put(K_RECORD, new BoundedParameter.Boolean(K_RECORD, "Make an animated recording.",
        "If present, a screenshot is taken after each stroke is made.", false));
    return defs;
  }

  private static void bug(String what) {
    Debug.out("Animation", what);
  }

  // -------------------------------------------------------------------------- Members.
  File baseDir = null;
  int animationDirCounter = 0;
  int animationFrameCounter = 0;
  File currentAnimationDir;
  Rectangle2D currentRegion;
  String currentFileType;

  @Override
  public void initialize() {
    if (getParam(K_RECORD).getBoolean()) {
      String where = startAnimation(main.getDrawingSurface().getBounds(), "png");
      System.out.println("Recording to " + where + ".");
      main.getDrawingSurface().getSoup().addSequenceListener(this);
    }
  }

  public void handleSequenceEvent(SequenceEvent seqEvent) {
    if (seqEvent.getType() == SequenceEvent.Type.END) {
      if (baseDir == null) {
        startAnimation(main.getDrawingSurface().getBounds(), "png");
      }
      // DrawingBuffer buf = new DrawingBuffer();
      // DrawingBufferRoutines.line(buf, seqEvent.getSeq().getFirst(), seqEvent.getSeq().getLast(),
      // Color.BLUE);
      // DrawingBufferRoutines.dot(buf, seqEvent.getSeq().getFirst(), 6, 1, Color.BLACK,
      // Color.GREEN);
      // DrawingBufferRoutines.dot(buf, seqEvent.getSeq().getLast(), 6, 1, Color.BLACK,
      // Color.RED);

      // addFrame(buf, true);
      addFrame();
    }
  }

  public Map<String, BoundedParameter> initializeParameters(Arguments args) {
    Map<String, BoundedParameter> params = copyParameters(getDefaultParameters());
    for (String k : params.keySet()) {
      if (args.hasFlag(k)) {
        if (args.hasValue(k)) {
          params.get(k).setValue(args.getValue(k));
          bug("Set " + params.get(k).getHumanReadableName() + " to " + params.get(k).getValueStr());
        } else {
          params.get(k).setValue("true");
          bug("Set " + params.get(k).getHumanReadableName() + " to " + params.get(k).getValueStr());
        }
      }
    }
    return params;
  }

  public String startAnimation(Rectangle2D rectangle, String fileType) {
    String ret = "unknown location?";
    if (baseDir == null) {
      if (main.getProperty(K_ANIMATION_DIR) != null) {
        baseDir = new File(main.getProperty(K_ANIMATION_DIR));
      } else {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int result = chooser.showDialog(main.getDrawingSurface(), "Choose Directory");
        if (result == JFileChooser.APPROVE_OPTION) {
          File d = chooser.getSelectedFile();
          if (d.isDirectory() && d.canWrite()) {
            baseDir = d;
            main.setProperty(K_ANIMATION_DIR, baseDir.getAbsolutePath());
          }
        }
      }
    }

    if (baseDir == null) {
      System.out.println("Animation base dir is not set. I won't record anything!");
    } else {
      File next = findNextDir();
      ret = next.getAbsoluteFile().getAbsolutePath();
      this.currentRegion = rectangle;
      this.currentFileType = fileType;
      this.animationFrameCounter = 0;
    }
    return ret;
  }

  public void addFrame() {
    addFrame(main.getContentImage());
  }

  public void addFrame(DrawingBuffer buf, boolean useRawInk) {
    if (useRawInk) {
      BufferedImage raw = main.getRawInkImage();
      Graphics2D g = raw.createGraphics();
      buf.drawToGraphics(g);
      addFrame(raw);
    } else {
      addFrame(buf.getImage());
    }
  }

  public void addFrame(BufferedImage image) {
    File frameFile = getNextAnimationFile(currentFileType);
    try {
      ImageIO.write(image, "PNG", frameFile);
      System.out.println("Wrote " + frameFile.getAbsolutePath());
    } catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  private File getNextAnimationFile(String suffix) {
    if (!suffix.startsWith(".")) {
      suffix = "." + suffix;
    }
    File file = new File(getCurrentAnimationDir(), animationFrameCounter + suffix);
    if (!file.getParentFile().exists()) {
      file.getParentFile().mkdirs();
    }
    animationFrameCounter++;
    return file;
  }

  private File getCurrentAnimationDir() {
    if (currentAnimationDir == null) {
      currentAnimationDir = new File(baseDir, "" + animationDirCounter);
    }
    return currentAnimationDir;
  }

  private File findNextDir() {
    File nextDir = null;
    while (true) {
      nextDir = new File(baseDir, "" + animationDirCounter);
      if (nextDir.exists()) {
        animationDirCounter++;
      } else {
        break;
      }
    }
    return nextDir;
  }
}
