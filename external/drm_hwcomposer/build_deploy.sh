#!/bin/bash -e

# To see logs after deploy run: $ HWCLOG=1 TESTDEV=<DEV> ./build_deploy.sh

[ -z "$TESTDEV" ] && echo "Run $ TESTDEV=<Your lunch target> ./build_deploy.sh" && false

cd ../..
. build/envsetup.sh
lunch $TESTDEV
cd -

mm

adb root && adb remount && adb sync vendor

adb shell stop
adb shell stop vendor.hwcomposer-2-1 && adb shell start vendor.hwcomposer-2-1 || true
adb shell stop vendor.hwcomposer-2-2 && adb shell start vendor.hwcomposer-2-2 || true
adb shell stop vendor.hwcomposer-2-3 && adb shell start vendor.hwcomposer-2-3 || true
adb shell stop vendor.hwcomposer-2-4 && adb shell start vendor.hwcomposer-2-4 || true

[ $HWCLOG -eq "1" ] && adb logcat -c

adb shell start

[ $HWCLOG -eq "1" ] && adb logcat | grep -i hwc
