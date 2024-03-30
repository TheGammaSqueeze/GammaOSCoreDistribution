#!/bin/bash

# Run cargo2android.py for every crate in crosvm.

set -e

find . -type f -name Cargo.toml | xargs dirname | sort | xargs -L1 ./run_c2a.sh
