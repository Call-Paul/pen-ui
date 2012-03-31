package org.six11.sf;

public interface RecognitionListener {

  public static enum What {
    LineSeg, //    linework
    ArcSeg, //     linework
    CurveSeg, //   linework
    CircleArc, //  linework
    CircleSeg, //  linework
    EllipseSeg, // linework
    BlogSeg, //    linework

    Colinear, //           constraint
    RightAngle, //         constraint
    SameLengthVague, //    constraint
    SameLengthSpecific, // constraint
    SameAngle, //          constraint 

    EndpointLatch, //     latch
    TJunctLatch, //       latch
    ContinuationLatch, // latch 

    DotCreate, // dot
    DotSelect, // dot
    DotMove, //   dot
    DotDelete, // dot

    Erase, // erase

    SelectStencil, // select
    SelectSegment, // select
    SelectNone, //    select

    PanZoomShowWidget, // pan/zoom
    Pan, //               pan/zoom
    Zoom, //              pan/zoom
    
    FSHeat, //   flow selection
    FSMove, //   flow selection
    FSSmooth, // flow selection
    
    UndoRedoStart,
    Undo,
    Redo,
    UndoRedoDone,
    
    Unknown
  }

  public void somethingRecognized(What what);

}
