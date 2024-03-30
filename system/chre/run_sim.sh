#!/bin/bash

# Quit if any command produces an error.
set -e

BUILD_ONLY="false"
while getopts "b" opt; do
  case ${opt} in
    b)
      BUILD_ONLY="true"
      ;;
  esac
done

# Build and run the CHRE simulator.
CHRE_HOST_OS=`uname`
if [[ $CHRE_HOST_OS == 'Darwin' ]]; then
JOB_COUNT=`sysctl -n hw.ncpu`
else
JOB_COUNT=$((`grep -c ^processor /proc/cpuinfo`))
fi

# Export the variant Makefile.
export CHRE_VARIANT_MK_INCLUDES=variant/simulator/variant.mk

make clean && make google_x86_linux_debug -j$JOB_COUNT

if [ "$BUILD_ONLY" = "false" ]; then
./out/google_x86_linux_debug/libchre ${@:1}
fi
