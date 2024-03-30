# Pandora Android server

The Pandora Android server exposes the [Pandora test interfaces](
go/pandora-doc) over gRPC implemented on top of the Android Bluetooth SDK.

## Getting started

Using Pandora Android server requires to:

* Build AOSP for your DUT, which can be either a physical device or an Android
  Virtual Device (AVD).
* [Only for virtual tests] Build Rootcanal, the Android
  virtual Bluetooth Controller.
* Setup your test environment.
* Build, install, and run Pandora server.
* Run your tests.

### 1. Build and run AOSP code

Refer to the AOSP documentation to [initialize and sync](
https://g3doc.corp.google.com/company/teams/android/developing/init-sync.md)
AOSP code, and [build](
https://g3doc.corp.google.com/company/teams/android/developing/build-flash.md)
it for your DUT (`aosp_cf_x86_64_phone-userdebug` for the emulator).

**If your DUT is a physical device**, flash the built image on it. You may
need to use [Remote Device Proxy](
https://g3doc.corp.google.com/company/teams/android/wfh/adb/remote_device_proxy.md)
if you are using a remote instance to build. If you are also using `adb` on
your local machine, you may need to force kill the local `adb` server (`adb
kill-server` before using Remote Device Proxy.

**If your DUT is a Cuttlefish virtual device**, then proceed with the following steps:

* Connect to your [Chrome Remote Desktop](
  https://remotedesktop.corp.google.com/access/).
* Create a local Cuttlefish instance using your locally built image with the command
  `acloud create --local-instance --local-image` (see [documentation](
  go/acloud-manual#local-instance-using-a-locally-built-image))

### 2. Build Rootcanal [only for virtual tests on a physical device]

Rootcanal is a virtual Bluetooth Controller that allows emulating Bluetooth
communications. It is used by default within Cuttlefish when running it using the [acloud](go/acloud) command (and thus this step is not
needed) and is required for all virtual tests. However, it does not come
preinstalled on a build for a physical device.

Proceed with the [following instructions](
https://docs.google.com/document/d/1-qoK1HtdOKK6sTIKAToFf7nu9ybxs8FQWU09idZijyc/edit#heading=h.x9snb54sjlu9)
to build and install Rootcanal on your DUT.

### 3. Setup your test environment

Each time when starting a new ADB server to communicate with your DUT, proceed
with the following steps to setup the test environment:

* If running virtual tests (such as PTS-bot) on a physical device:
  * Run Rootcanal:
    `adb root` then
    `adb shell ./vendor/bin/hw/android.hardware.bluetooth@1.1-service.sim &`
  * Forward Rootcanal port through ADB:
    `adb forward tcp:<rootcanal-port> tcp:<rootcanal-port>`.
    Rootcanal port number may differ depending on its configuration. It is
    7200 for the AVD, and generally 6211 for physical devices.
* Forward Pandora Android server port through ADB:
  `adb forward tcp:8999 tcp:8999`.

The above steps can be done by executing the `setup.sh` helper script (the
`-rootcanal` option must be used for virtual tests on a physical device).

Finally, you must also make sure that the machine on which tests are executed
can access the ports of the Pandora Android server, Rootcanal (if required),
and ADB (if required).

You can also check the usage examples provided below.

### 4. Build, install, and run Pandora Android server

* `m PandoraServer`
* `adb install -r -g out/target/product/<device>/testcases/Pandora/arm64/Pandora.apk`

* Start the instrumented app:
* `adb shell am instrument -w -e Debug false com.android.pandora/.Server`

### 5. Run your tests

You should now be fully set up to run your tests!

### Usage examples

Here are some usage examples:

* **DUT**: physical
  **Test type**: virtual
  **Test executer**: remote instance (for instance a Cloudtop) accessed via SSH
  **Pandora Android server repository location**: local machine (typically
  using Android Studio)

  * On your local machine: `./setup.sh --rootcanal`.
  * On your local machine: build and install the app on your DUT.
  * Log on your remote instance, and forward Rootcanal port (6211, may change
    depending on your build) and Pandora Android server (8999) port:
    `ssh -R 6211:localhost:6211 -R 8999:localhost:8999 <remote-instance>`.
    Optionnally, you can also share ADB port to your remote instance (if
    needed) by adding `-R 5037:localhost:5037` to the command.
  * On your remote instance: execute your tests.

* **DUT**: virtual (running in remote instance)
  **Test type**: virtual
  **Test executer**: remote instance
  **Pandora Android server repository location**: remote instance

  On your remote instance:
  * `./setup.sh`.
  * Build and install the app on the AVD.
  * Execute your tests.

