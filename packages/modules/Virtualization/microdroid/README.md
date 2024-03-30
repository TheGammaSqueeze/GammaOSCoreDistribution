# Microdroid

Microdroid is a (very) lightweight version of Android that is intended to run on
on-device virtual machines. It is built from the same source code as the regular
Android, but it is much smaller; no system server, no HALs, no GUI, etc. It is
intended to host headless & native workloads only.

## Prerequisites

Any 64-bit target (either x86_64 or arm64) is supported. 32-bit target is not
supported. Note that we currently don't support user builds; only userdebug
builds are supported.

The only remaining requirement is that `com.android.virt` APEX has to be
pre-installed. To do this, add the following line in your product makefile.

```make
$(call inherit-product, packages/modules/Virtualization/apex/product_packages.mk)
```

Build the target after adding the line, and flash it. This step needs to be done
only once for the target.

If you are using `aosp_oriole` (Pixel 6) or `aosp_cf_x86_64_phone` (Cuttlefish),
adding above line is not necessary as it's already done.

## Building and installing microdroid

Microdroid is part of the `com.android.virt` APEX. To build it and install to
the device:

```sh
banchan com.android.virt aosp_arm64
UNBUNDLED_BUILD_SDKS_FROM_SOURCE=true m apps_only dist
adb install out/dist/com.android.virt.apex
adb reboot
```

If your target is x86_64 (e.g. `aosp_cf_x86_64_phone`), replace `aosp_arm64`
with `aosp_x86_64`.

## Building an app

An app in microdroid is a shared library file embedded in an APK. The shared
library should have an entry point `android_native_main` as shown below:

```C++
extern "C" int android_native_main(int argc, char* argv[]) {
  printf("Hello Microdroid!\n");
}
```

Then build it as a shared library:

```
cc_library_shared {
  name: "MyMicrodroidApp",
  srcs: ["**/*.cpp"],
  sdk_version: "current",
}
```

Then you need a configuration file in JSON format that defines what to load and
execute in microdroid. The name of the file can be anything and you may have
multiple configuration files if needed.

```json
{
  "os": { "name": "microdroid" },
  "task": {
    "type": "microdroid_launcher",
    "command": "MyMicrodroidApp.so"
  }
}
```

The value of `task.command` should match with the name of the shared library
defined above. If your app requires APEXes to be imported, you can declare the
list in `apexes` key like following.

```json
{
  "os": ...,
  "task": ...,
  "apexes": [
    {"name": "com.android.awesome_apex"}
  ]
}
```

Embed the shared library and the VM configuration file in an APK:

```
android_app {
  name: "MyApp",
  srcs: ["**/*.java"], // if there is any java code
  jni_libs: ["MyMicrodroidApp"],
  use_embedded_native_libs: true,
  sdk_version: "current",
}

// The VM configuration file can be embedded by simply placing it at `./assets`
// directory.
```

Finally, you build the APK.

```sh
TARGET_BUILD_APPS=MyApp m apps_only dist
```

## Running the app on microdroid

First of all, install the APK to the target device.

```sh
adb install out/dist/MyApp.apk
```

`ALL_CAP`s below are placeholders. They need to be replaced with correct
values:

* `VM_CONFIG_FILE`: the name of the VM config file that you embedded in the APK.
  (e.g. `vm_config.json`)
* `PACKAGE_NAME_OF_YOUR_APP`: package name of your app (e.g. `com.acme.app`).
* `PATH_TO_YOUR_APP`: path to the installed APK on the device. Can be obtained
  via the following command.
  ```sh
  adb shell pm path PACKAGE_NAME_OF_YOUR_APP
  ```
  It shall report a cryptic path similar to `/data/app/~~OgZq==/com.acme.app-HudMahQ==/base.apk`.

Execute the following commands to launch a VM. The VM will boot to microdroid
and then automatically execute your app (the shared library
`MyMicrodroidApp.so`).

```sh
TEST_ROOT=/data/local/tmp/virt
adb shell /apex/com.android.virt/bin/vm run-app \
--log $TEST_ROOT/log.txt \
PATH_TO_YOUR_APP \
$TEST_ROOT/MyApp.apk.idsig \
$TEST_ROOT/instance.img \
assets/VM_CONFIG_FILE
```

The last command lets you know the CID assigned to the VM. The console output
from the VM is stored to `$TEST_ROOT/log.txt` file for debugging purpose. If you
omit the `--log $TEST_ROOT/log.txt` option, it will be emitted to the current
console.

Stopping the VM can be done as follows:

```sh
adb shell /apex/com.android.virt/bin/vm stop $CID
```

, where `$CID` is the reported CID value. This works only when the `vm` was
invoked with the `--daemonize` flag. If the flag was not used, press Ctrl+C on
the console where the `vm run-app` command was invoked.

## ADB

On userdebug builds, you can have an adb connection to microdroid. To do so,

```sh
adb forward tcp:8000 vsock:$CID:5555
adb connect localhost:8000
```

`$CID` should be the CID that `vm` reported upon execution of the `vm run`
command in the above. You can also check it with
`adb shell "/apex/com.android.virt/bin/vm list"`. `5555` must be the value.
`8000` however can be any port on the development machine.

Done. Now you can log into microdroid. Have fun!

```sh
$ adb -s localhost:8000 shell
```
