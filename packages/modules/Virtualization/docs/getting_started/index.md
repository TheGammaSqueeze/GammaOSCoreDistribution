# Getting started with Protected Virtual Machines

## Prepare a device

First you will need a device that is capable of running virtual machines. On arm64, this means a
device which boots the kernel in EL2 and the kernel was built with KVM enabled. Unfortunately at the
moment, we don't have an arm64 device in AOSP which does that. Instead, use cuttlefish which
provides the same functionalities except that the virtual machines are not protected from the host
(i.e. Android). This however should be enough for functional testing.

We support the following device:

* aosp_cf_x86_64_phone (Cuttlefish a.k.a. Cloud Android)

Building Cuttlefish

```shell
source build/envsetup.sh
lunch aosp_cf_x86_64_phone-userdebug
m
```

Run Cuttlefish locally by

```shell
acloud create --local-instance --local-image
```

## Running demo app

The instruction is [here](../../demo/README.md).

## Running tests

There are various tests that spawn guest VMs and check different aspects of the architecture. They
all can run via `atest`.

```shell
atest VirtualizationTestCases.64
atest MicrodroidHostTestCases
atest MicrodroidTestApp
```

If you run into problems, inspect the logs produced by `atest`. Their location is printed at the
end. The `host_log_*.zip` file should contain the output of individual commands as well as VM logs.

## Spawning your own VMs with custom kernel

You can spawn your own VMs by passing a JSON config file to the VirtualizationService via the `vm`
tool on a rooted KVM-enabled device. If your device is attached over ADB, you can run:

```shell
cat > vm_config.json
{
  "kernel": "/data/local/tmp/kernel",
  "initrd": "/data/local/tmp/ramdisk",
  "params": "rdinit=/bin/init"
}
adb root
adb push <kernel> /data/local/tmp/kernel
adb push <ramdisk> /data/local/tmp/ramdisk
adb push vm_config.json /data/local/tmp/vm_config.json
adb shell "start virtualizationservice"
adb shell "/apex/com.android.virt/bin/vm run /data/local/tmp/vm_config.json"
```

The `vm` command also has other subcommands for debugging; run `/apex/com.android.virt/bin/vm help`
for details.

## Spawning your own VMs with Microdroid

[Microdroid](../../microdroid/README.md) is a lightweight version of Android that is intended to run
on pVM. You can manually run the demo app on top of Microdroid as follows:

```shell
TARGET_BUILD_APPS=MicrodroidDemoApp m apps_only dist
adb shell mkdir -p /data/local/tmp/virt
adb push out/dist/MicrodroidDemoApp.apk /data/local/tmp/virt/
adb shell /apex/com.android.virt/bin/vm run-app \
  --debug full \
  /data/local/tmp/virt/MicrodroidDemoApp.apk \
  /data/local/tmp/virt/MicrodroidDemoApp.apk.idsig \
  /data/local/tmp/virt/instance.img assets/vm_config.json
```

## Building and updating CrosVM and VirtualizationService {#building-and-updating}

You can update CrosVM and the VirtualizationService by updating the `com.android.virt` APEX instead
of rebuilding the entire image.

```shell
banchan com.android.virt aosp_arm64   // or aosp_x86_64 if the device is cuttlefish
UNBUNDLED_BUILD_SDKS_FROM_SOURCE=true m apps_only dist
adb install out/dist/com.android.virt.apex
adb reboot
```

## Building and updating GKI inside Microdroid

Checkout the Android common kernel and build it following the [official
guideline](https://source.android.com/setup/build/building-kernels).

```shell
mkdir android-kernel && cd android-kernel
repo init -u https://android.googlesource.com/kernel/manifest -b common-android12-5.10
repo sync
FAST_BUILD=1 DIST_DIR=out/dist BUILD_CONFIG=common/build.config.gki.aarch64 build/build.sh -j80
```

Replace `build.config.gki.aarch64` with `build.config.gki.x86_64` if building
for x86.

Then copy the built kernel to the Android source tree.

```
cp out/dist/Image <android_root>/kernel/prebuilts/5.10/arm64/kernel-5.10
```

Finally rebuild the `com.android.virt` APEX and install it by following the
steps shown in [Building and updating Crosvm and
Virtualization](#building-and-updating).
