#!/bin/bash
set -e
docker build -t ndkports .
# We need to specify the full argument list for gradle explicitly because
# there's no way to append to docker's CMD. This should be kept the same as the
# default, but adding -Prelease.
docker run --rm -v $(pwd):/src ndkports \
  --stacktrace -PndkPath=/ndk -Prelease release
