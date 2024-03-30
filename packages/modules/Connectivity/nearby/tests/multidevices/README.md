# Nearby Mainline Fast Pair end-to-end tests

This document refers to the Mainline Fast Pair project source code in the
packages/modules/Connectivity/nearby. This is not an officially supported Google
product.

## About the Fast Pair Project

The Connectivity Nearby mainline module is created in the Android T to host
Better Together related functionality. Fast Pair is one of the main
functionalities to provide seamless onboarding and integrated experiences for
peripheral devices (for example, headsets like Google Pixel Buds) in the Nearby
component.

## Fully automated test

### Prerequisites

The fully automated end-to-end (e2e) tests are host-driven tests (which means
test logics are in the host test scripts) using Mobly runner in Python. The two
phones are installed with the test snippet
`NearbyMultiDevicesClientsSnippets.apk` in the test time to let the host scripts
control both sides for testing. Here's the overview of the test environment.

Workstation (runs Python test scripts and controls Android devices through USB
ADB) \
├── Phone 1: As Fast Pair seeker role, to scan, pair Fast Pair devices nearby \
└── Phone 2: As Fast Pair provider role, to simulate a Fast Pair device (for
example, a Bluetooth headset)

Note: These two phones need to be physically within 0.3 m of each other.

### Prepare Phone 1 (Fast Pair seeker role)

This is the phone to scan/pair Fast Pair devices nearby using the Nearby
Mainline module. Test it by flashing with the Android T ROM.

### Prepare Phone 2 (Fast Pair provider role)

This is the phone to simulate a Fast Pair device (for example, a Bluetooth
headset). Flash it with a customized ROM with the following changes:

*   Adjust Bluetooth profile configurations. \
    The Fast Pair provider simulator is an opposite role to the seeker. It needs
    to enable/disable the following Bluetooth profile:
    *   Disable A2DP (profile_supported_a2dp)
    *   Disable the AVRCP controller (profile_supported_avrcp_controller)
    *   Enable A2DP sink (profile_supported_a2dp_sink)
    *   Enable the HFP client connection service (profile_supported_hfpclient,
        hfp_client_connection_service_enabled)
    *   Enable the AVRCP target (profile_supported_avrcp_target)
    *   Enable the automatic audio focus request
        (a2dp_sink_automatically_request_audio_focus)
*   Adjust Bluetooth TX power limitation in Bluetooth module and disable the
    Fast Pair in Google Play service (aka GMS)

```shell
adb root
adb shell am broadcast \
  -a 'com.google.android.gms.phenotype.FLAG_OVERRIDE' \
  --es package "com.google.android.gms.nearby" \
  --es user "\*" \
  --esa flags "enabled" \
  --esa types "boolean" \
  --esa values "false" \
  com.google.android.gms
```

### Running tests

To run the tests, enter:

```shell
atest -v CtsNearbyMultiDevicesTestSuite
```

## Manual testing the seeker side with headsets

Use this testing with headsets such as Google Pixel buds.

The `FastPairTestDataProviderService.apk` is a run-time configurable Fast Pair
data provider service (`FastPairDataProviderService`):

`packages/modules/Connectivity/nearby/tests/multidevices/clients/test_service/fastpair_seeker_data_provider`

It has a test data manager(`FastPairTestDataManager`) to receive intent
broadcasts to add or clear the test data cache (`FastPairTestDataCache`). This
cache provides the data to return to the Fast Pair module for onXXX calls (for
example, `onLoadFastPairAntispoofKeyDeviceMetadata`) so you can feed the
metadata for your device.

Here are some sample uses:

*   Send FastPairAntispoofKeyDeviceMetadata for PixelBuds-A to
    FastPairTestDataCache \
    `./fast_pair_data_provider_shell.sh -m=718c17
    -a=../test_data/fastpair/pixelbuds-a_antispoofkey_devicemeta_json.txt`
*   Send FastPairAccountDevicesMetadata for PixelBuds-A to FastPairTestDataCache
    \
    `./fast_pair_data_provider_shell.sh
    -d=../test_data/fastpair/pixelbuds-a_account_devicemeta_json.txt`
*   Send FastPairAntispoofKeyDeviceMetadata for Provider Simulator to
    FastPairTestDataCache \
    `./fast_pair_data_provider_shell.sh -m=00000c
    -a=../test_data/fastpair/simulator_antispoofkey_devicemeta_json.txt`
*   Send FastPairAccountDevicesMetadata for Provider Simulator to
    FastPairTestDataCache \
    `./fast_pair_data_provider_shell.sh
    -d=../test_data/fastpair/simulator_account_devicemeta_json.txt`
*   Clear FastPairTestDataCache \
    `./fast_pair_data_provider_shell.sh -c`

See
[host/tool/fast_pair_data_provider_shell.sh](host/tool/fast_pair_data_provider_shell.sh)
for more documentation.

To install the data provider as system private app, consider remounting the
system partition:

```
adb root && adb remount
```

Push it in:

```
adb push ${ANDROID_PRODUCT_OUT}/system/app/NearbyFastPairSeekerDataProvider
/system/priv-app/
```

Then reboot:

```
adb reboot
```

## Manual testing the seeker side with provider simulator app

The `NearbyFastPairProviderSimulatorApp.apk` is a simple Android app to let you
control the state of the Fast Pair provider simulator. Install this app on phone
2 (Fast Pair provider role) to work correctly.

See
[clients/test_support/fastpair_provider/simulator_app/Android.bp](clients/test_support/fastpair_provider/simulator_app/Android.bp)
for more documentation.
