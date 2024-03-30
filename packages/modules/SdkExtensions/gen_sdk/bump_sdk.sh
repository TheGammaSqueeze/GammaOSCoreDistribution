#!/bin/bash
if ! [ -e build/soong ]; then
  echo "This script must be run from the top of the tree"
  exit 1
fi

sdk="$1"
if [[ -z "$sdk" ]]; then
  echo "usage: $0 <new-sdk-int> [bug-id]"
  exit 1
fi

bug_text=$(test -n "$2" && echo "\nBug: $2")

SDKEXT="packages/modules/SdkExtensions/"

TARGET_PRODUCT=aosp_arm64 build/soong/soong_ui.bash --make-mode --soong-only gen_sdk
out/soong/host/linux-x86/bin/gen_sdk \
    --database ${SDKEXT}/gen_sdk/extensions_db.textpb \
    --action new_sdk \
    --sdk "$sdk"
sed -E -i -e "/CurrentSystemImageValue/{n;s/[0-9]+/${sdk}/}" \
    ${SDKEXT}/derive_sdk/derive_sdk_test.cpp
sed -E -i -e "/public static final int V = /{s/\S+;/${sdk};/}" \
    ${SDKEXT}/java/com/android/os/ext/testing/CurrentVersion.java
repo start bump-ext ${SDKEXT}

message="Bump SDK Extension version to ${sdk}

Generated with:
$ $0 $@

Database update generated with:
$ gen_sdk --action new_sdk --sdk $sdk
"
message+=$(test -n "$2" && echo -e "\nBug: $2")
message+=$(echo -e "\nTest: presubmit")

git -C ${SDKEXT} commit -a -m "$message"