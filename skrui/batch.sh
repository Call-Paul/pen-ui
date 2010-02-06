#!/bin/bash

# batch.sh [curve-median-multiplier] [speed-multiplier] [spline-thresh] [pdf]
# e.g.
# batch.sh 2.0 0.75 20 foo.pdf
./run CornerFinder PrettyPrinterSegments \
  --curve-median-multiplier=$1 \
  --speed-multiplier=$2 \
  --spline-thresh=$3 \
  --load-sketch=sketches/test-suite-2.sketch \
  --pdf-output=$4 \
  --corner-size=6.0 \
  --draw-corners \
  --show-arguments \
  --draw-control-points \
  --no-ui
exit 0