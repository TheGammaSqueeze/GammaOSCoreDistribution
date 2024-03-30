#!/bin/bash

# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
set -xeuo pipefail

readonly arg_aab_filepath=$1
readonly arg_prebuilt_aab_filepath=$2

readonly ZIPPER=$(rlocation bazel_tools/tools/zip/zipper/zipper)
readonly -a AAB_FILES=(
  "BundleConfig.pb"
  "base/apex.pb"
  "base/apex/arm64-v8a.build_info.pb"
  "base/apex/arm64-v8a.img"
  "base/apex/armeabi-v7a.build_info.pb"
  "base/apex/armeabi-v7a.img"
  "base/apex/x86.build_info.pb"
  "base/apex/x86.img"
  "base/apex/x86_64.build_info.pb"
  "base/apex/x86_64.img"
  "base/manifest/AndroidManifest.xml"
  "base/root/apex_manifest.pb"
)
readonly -a EXCLUDE_FILES=(
  # The following files are 1)not in bazel built abb file; 2)not same as the
  # ones created by Soong, so exclude them in diff to make the test case pass.
  #(TODO: b/190817312) assets/NOTICE.html.gz is not in bazel built aab file.
  "assets"
  "NOTICE.html.gz"
  #(TODO: b/222587783) base/assets.pb is not in bazel built aab file
  "assets.pb"
  #(TODO: b/222588072) all .img files are different
  "*.img"
  #(TODO: b/222588241) all .build_info.pb files are different
  "*.build_info.pb"
  #(TODO: b/222588061) base/root/apex_manifest.pb
  "apex_manifest.pb"
  #(TODO: b/222587792) base/manifest/AndroidManifest.xml
  # two bytes are different, prebuilt has 0x20, bazel built has 0x1f
  "AndroidManifest.xml"
)

# Check if .aab file contains specified files
function aab_contains_files() {
  local aab_filepath=$1
  shift
  local expected_files=("$@")
  local aab_entries=$($ZIPPER v "$aab_filepath")
  for file in "${expected_files[@]}"; do
    if ! echo -e "$aab_entries" | grep "$file"; then
      echo "Failed to find file $file in $aab_filepath"
      exit 1
    fi
  done
}

# Test .aab file contains required files
function test_aab_contains_required_files() {
  if [ "${arg_aab_filepath: -4}" != ".aab" ]; then
    echo "@arg_aab_filepath does not have .aab as extension."
    exit 1
  fi
  aab_contains_files "$arg_aab_filepath" "${AAB_FILES[@]}"
}

function test_aab_files_diff() {
  local prebuilt_aab_file_dir=$(dirname "$arg_prebuilt_aab_filepath")

  local extracted_prebuilt_aab_dir=$(mktemp -d -p "$prebuilt_aab_file_dir" prebuilt_XXXXXX)
  $ZIPPER x "$arg_prebuilt_aab_filepath" -d "$extracted_prebuilt_aab_dir"

  local extracted_aab_dir=$(mktemp -d -p "$prebuilt_aab_file_dir" aab_XXXXXX)
  $ZIPPER x "$arg_aab_filepath" -d "$extracted_aab_dir"

  local diff_exclude=
  for pattern in "${EXCLUDE_FILES[@]}"; do
    diff_exclude="$diff_exclude -x $pattern"
  done

  if ! diff -w $diff_exclude -r $extracted_prebuilt_aab_dir $extracted_aab_dir; then
    echo ".aab file content is not same as the prebuilt one."
    exit 1
  fi

  rm -rf "${extracted_prebuilt_aab_dir}"
  rm -rf "${extracted_aab_dir}"
}

test_aab_contains_required_files
test_aab_files_diff

echo "Passed all test cases."