#!/usr/bin/env bash

# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# A script to generate an Atest Bazel workspace for execution on the Android CI.

function check_env_var()
{
  if [ ! -n "${!1}" ] ; then
    echo "Necessary environment variable ${1} missing, exiting."
    exit 1
  fi
}

# Check for necessary environment variables.
check_env_var "ANDROID_BUILD_TOP"
check_env_var "TARGET_PRODUCT"
check_env_var "TARGET_BUILD_VARIANT"

function get_build_var()
{
  (${ANDROID_BUILD_TOP}/build/soong/soong_ui.bash --dumpvar-mode --abs $1)
}

out=$(get_build_var PRODUCT_OUT)
JDK_PATH="${ANDROID_BUILD_TOP}/prebuilts/jdk/jdk11/linux-x86"
BAZEL_BINARY="${ANDROID_BUILD_TOP}/prebuilts/bazel/linux-x86_64/bazel"

# Use the versioned JDK and Python binaries in prebuilts/ for a reproducible
# build with minimal reliance on host tools.
export PATH=${ANDROID_BUILD_TOP}/prebuilts/build-tools/path/linux-x86:${ANDROID_BUILD_TOP}/prebuilts/jdk/jdk11/linux-x86/bin:${PATH}

export \
  ANDROID_PRODUCT_OUT=${out} \
  OUT=${out} \
  ANDROID_HOST_OUT=$(get_build_var HOST_OUT) \
  ANDROID_TARGET_OUT_TESTCASES=$(get_build_var TARGET_OUT_TESTCASES)

if [ ! -n "$OUT_DIR" ] ; then
  OUT_DIR=$(get_build_var "OUT_DIR")
fi

if [ ! -n "$DIST_DIR" ] ; then
  echo "dist dir not defined, defaulting to OUT_DIR/dist."
  export DIST_DIR=${OUT_DIR}/dist
fi

# Build Atest from source to pick up the latest changes.
${ANDROID_BUILD_TOP}/build/soong/soong_ui.bash --make-mode atest

# Generate the initial workspace via Atest Bazel mode.
${OUT_DIR}/host/linux-x86/bin/atest-dev --bazel-mode --dry-run -m

# Copy over some needed dependencies. We need Bazel for querying dependencies
# and actually running the test. The JDK is for the Tradefed test runner and
# Java tests.
cp -L ${BAZEL_BINARY} ${OUT_DIR}/atest_bazel_workspace/bazelbin
mkdir ${OUT_DIR}/atest_bazel_workspace/prebuilts/jdk
cp -a ${JDK_PATH}/* ${OUT_DIR}/atest_bazel_workspace/prebuilts/jdk/.

pushd ${OUT_DIR}/atest_bazel_workspace

# TODO(b/201242197): Create a stub workspace for the remote_coverage_tools
# package so that Bazel does not attempt to fetch resources online which is not
# allowed on build bots.
mkdir remote_coverage_tools
touch remote_coverage_tools/WORKSPACE
cat << EOF > remote_coverage_tools/BUILD
package(default_visibility = ["//visibility:public"])

filegroup(
    name = "coverage_report_generator",
    srcs = ["coverage_report_generator.sh"],
)
EOF

# Make directories for temporary output.
JAVA_TEMP_DIR=$(mktemp -d)
trap "rm -rf ${JAVA_TEMP_DIR}" EXIT

BAZEL_TEMP_DIR=$(mktemp -d)
trap "rm -rf ${BAZEL_TEMP_DIR}" EXIT

# Query the list of dependencies needed by the tests.
# TODO(b/217658764): Consolidate Bazel query functions into a separate script
# that other components can use.
JAVA_HOME="${JDK_PATH}" \
  "${BAZEL_BINARY}" \
  --server_javabase="${JDK_PATH}" \
  --host_jvm_args=-Djava.io.tmpdir=${JAVA_TEMP_DIR} \
  --output_user_root=${BAZEL_TEMP_DIR} \
  --max_idle_secs=5 \
  cquery \
  --override_repository=remote_coverage_tools=${ANDROID_BUILD_TOP}/out/atest_bazel_workspace/remote_coverage_tools \
  --output=starlark \
  --starlark:file=${ANDROID_BUILD_TOP}/tools/asuite/atest/bazel/format_as_soong_module_name.cquery \
  "deps( $(${BAZEL_BINARY} \
  --server_javabase="${JDK_PATH}" \
  --host_jvm_args=-Djava.io.tmpdir=${JAVA_TEMP_DIR} \
  --output_user_root=${BAZEL_TEMP_DIR} \
  --max_idle_secs=5 query "tests(...)" | paste -sd "+" -) )"  | \
  sed '/^$/d' | \
  sort -u \
> build_targets

popd

# Build all test dependencies.
${ANDROID_BUILD_TOP}/build/soong/soong_ui.bash --make-mode $(cat $OUT_DIR/atest_bazel_workspace/build_targets)

# Create the workspace archive which will be downloaded by the Tradefed hosts.
tar zcfh ${DIST_DIR}/atest_bazel_workspace.tar.gz out/atest_bazel_workspace/
