#!/bin/bash

# Copyright (C) 2020 The Android Open Source Project
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

set -e
shopt -s extglob
shopt -s globstar

# to use relative paths
cd $(dirname $0)

# when executed directly from commandline, build dependencies
if [[ $(basename $0) == "rundiff.sh" ]]; then
  if [ -z $ANDROID_BUILD_TOP ]; then
    echo "You need to source and lunch before you can use this script"
    exit 1
  fi
  $ANDROID_BUILD_TOP/build/soong/soong_ui.bash --make-mode linkerconfig conv_apex_manifest
else
  # workaround to use host tools(conv_apex_manifest, linkerconfig) on build server
  unzip -qqo linkerconfig_diff_test_host_tools.zip -d tools
  export PATH=$(realpath tools)/bin:$PATH
  export LD_LIBRARY_PATH=$(realpath tools)/lib64:$LD_LIBRARY_PATH
fi

# $1: target libraries.txt file
# $2: list of libs. ex) a.so:b.so:c.so
function write_libraries_txt {
  rm -rf $1
  IFS=':'
  for lib in $2; do
    echo $lib >> $1
  done
  unset IFS
}

# Simulate build process
# $1: input tree (with *.json)
# $2: output tree (*.json files are converted into *.pb)
function build_root {
  cp -R $1/* $2

  for json in $2/**/linker.config.json; do
    conv_linker_config proto -s $json -o ${json%.json}.pb
    rm $json
  done
  for json in $2/**/apex_manifest.json; do
    conv_apex_manifest proto $json -o ${json%.json}.pb
    rm $json
  done
}

# $1: target output directory
function run_linkerconfig_to {
  # delete old output
  rm -rf $1

  TMP_ROOT=$(mktemp -d -t linkerconfig-root-XXXXXXXX)
  # Build the root
  build_root testdata/root $TMP_ROOT

  # Run linkerconfig with various configurations

  ./testdata/prepare_root.sh --root $TMP_ROOT
  mkdir -p $1/stage0
  linkerconfig -v R -r $TMP_ROOT -t $1/stage0

  ./testdata/prepare_root.sh --bootstrap --root $TMP_ROOT
  mkdir -p $1/stage1
  linkerconfig -v R -r $TMP_ROOT -t $1/stage1

  ./testdata/prepare_root.sh --all --root $TMP_ROOT
  mkdir -p $1/stage2
  linkerconfig -v R -r $TMP_ROOT -t $1/stage2

  # skip prepare_root in order to use the same apexs
  mkdir -p $1/product-enabled
  linkerconfig -v R -p R -r $TMP_ROOT -t $1/product-enabled

  # skip prepare_root in order to use the same apexs
  # but with system/etc/vndkcorevariant.libraries.txt
  vndk_core_variant_libs_file=$TMP_ROOT/system/etc/vndkcorevariant.libraries.txt
  write_libraries_txt $vndk_core_variant_libs_file libevent.so:libexif.so:libfmq.so
  mkdir -p $1/vndk-in-system
  linkerconfig -v R -p R -r $TMP_ROOT -t $1/vndk-in-system
  # clean up
  rm -if $vndk_core_variant_libs_file
  vndk_core_variant_libs_file=

  ./testdata/prepare_root.sh --all --block com.android.art:com.android.vndk.vR --root $TMP_ROOT
  mkdir -p $1/guest
  linkerconfig -v R -p R -r $TMP_ROOT -t $1/guest

  # skip prepare_root in order to use the same apexes except VNDK
  rm -iRf $TMP_ROOT/apex/com.android.vndk.vR
  mkdir -p $1/legacy
  linkerconfig -r $TMP_ROOT -t $1/legacy

  # clean up testdata root
  rm -rf $TMP_ROOT
}

# update golden_output
if [[ $1 == "--update" ]]; then
  run_linkerconfig_to ./testdata/golden_output
  echo "Updated"
  exit 0
fi

echo "Running linkerconfig diff test..."

run_linkerconfig_to ./testdata/output
if diff -ruN ./testdata/golden_output ./testdata/output ; then
  echo "No changes."
else
  echo
  echo "------------------------------------------------------------------------------------------"
  echo "if change looks fine, run following:"
  echo "  \$ANDROID_BUILD_TOP/system/linkerconfig/rundiff.sh --update"
  echo "------------------------------------------------------------------------------------------"
  # fail
  exit 1
fi