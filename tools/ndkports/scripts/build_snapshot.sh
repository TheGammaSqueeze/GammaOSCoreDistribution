#!/bin/bash
set -e
docker build -t ndkports .
# Default command for the docker image handles the NDK location, --stacktrace,
# task list, etc.
docker run --rm -v $(pwd):/src ndkports
