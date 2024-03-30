#!/bin/bash -eux
#
# Script to run some local APEX tests while APEX support is WIP and not easily testable on CI

set -o pipefail

# TODO: Refactor build/make/envsetup.sh to make gettop() available elsewhere
function gettop
{
    # Function uses potentially uninitialzied variables
    set +u

    local TOPFILE=build/bazel/bazel.sh
    if [ -n "$TOP" -a -f "$TOP/$TOPFILE" ] ; then
        # The following circumlocution ensures we remove symlinks from TOP.
        (cd "$TOP"; PWD= /bin/pwd)
    else
        if [ -f $TOPFILE ] ; then
            # The following circumlocution (repeated below as well) ensures
            # that we record the true directory name and not one that is
            # faked up with symlink names.
            PWD= /bin/pwd
        else
            local HERE=$PWD
            local T=
            while [ \( ! \( -f $TOPFILE \) \) -a \( "$PWD" != "/" \) ]; do
                \cd ..
                T=`PWD= /bin/pwd -P`
            done
            \cd "$HERE"
            if [ -f "$T/$TOPFILE" ]; then
                echo "$T"
            fi
        fi
    fi

    set -u
}

AOSP_ROOT=`gettop`

# Generate BUILD files into out/soong/bp2build
"${AOSP_ROOT}/build/soong/soong_ui.bash" --make-mode BP2BUILD_VERBOSE=1 bp2build --skip-soong-tests

BUILD_FLAGS_LIST=(
  --color=no
  --curses=no
  --show_progress_rate_limit=5
  --config=bp2build
)
BUILD_FLAGS="${BUILD_FLAGS_LIST[@]}"

TEST_FLAGS_LIST=(
  --keep_going
  --test_output=errors
)
TEST_FLAGS="${TEST_FLAGS_LIST[@]}"

BUILD_TARGETS_LIST=(
  //build/bazel/examples/apex/minimal:build.bazel.examples.apex.minimal
  //system/timezone/apex:com.android.tzdata
)
BUILD_TARGETS="${BUILD_TARGETS_LIST[@]}"

echo "Building APEXes with Bazel..."
${AOSP_ROOT}/tools/bazel --max_idle_secs=5 build ${BUILD_FLAGS} --platforms //build/bazel/platforms:android_x86 -k ${BUILD_TARGETS}
${AOSP_ROOT}/tools/bazel --max_idle_secs=5 build ${BUILD_FLAGS} --platforms //build/bazel/platforms:android_x86_64 -k ${BUILD_TARGETS}
${AOSP_ROOT}/tools/bazel --max_idle_secs=5 build ${BUILD_FLAGS} --platforms //build/bazel/platforms:android_arm -k ${BUILD_TARGETS}
${AOSP_ROOT}/tools/bazel --max_idle_secs=5 build ${BUILD_FLAGS} --platforms //build/bazel/platforms:android_arm64 -k ${BUILD_TARGETS}

set +x
echo
echo "All tests passed, you are awesome!"
