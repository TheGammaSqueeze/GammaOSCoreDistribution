#!/bin/bash -eu

set -o pipefail

TOP="$(readlink -f "$(dirname "$0")"/../../..)"
"$TOP/build/soong/tests/androidmk_test.sh"
"$TOP/build/soong/tests/bootstrap_test.sh"
"$TOP/build/soong/tests/mixed_mode_test.sh"
"$TOP/build/soong/tests/bp2build_bazel_test.sh"
"$TOP/build/soong/tests/soong_test.sh"
"$TOP/build/bazel/ci/rbc_regression_test.sh" aosp_arm64-userdebug
