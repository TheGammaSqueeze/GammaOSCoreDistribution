/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.nearby.aidl;

/**
 * Fast Pair Device Metadata for a given device model ID.
 * @hide
 */
// TODO(b/204780849): remove unnecessary fields and polish comments.
parcelable FastPairDeviceMetadataParcel {
    // The image to show on the notification.
    String imageUrl;

    // The intent that will be launched via the notification.
    String intentUri;

    // The transmit power of the device's BLE chip.
    int bleTxPower;

    // The distance that the device must be within to show a notification.
    // If no distance is set, we default to 0.6 meters. Only Nearby admins can
    // change this.
    float triggerDistance;

    // The image icon that shows in the notification.
    byte[] image;

    // The name of the device.
    String name;

    int deviceType;

    // The image urls for device with device type "true wireless".
    String trueWirelessImageUrlLeftBud;
    String trueWirelessImageUrlRightBud;
    String trueWirelessImageUrlCase;

    // The notification description for when the device is initially discovered.
    String initialNotificationDescription;

    // The notification description for when the device is initially discovered
    // and no account is logged in.
    String initialNotificationDescriptionNoAccount;

    // The notification description for once we have finished pairing and the
    // companion app has been opened. For Bisto devices, this String will point
    // users to setting up the assistant.
    String openCompanionAppDescription;

    // The notification description for once we have finished pairing and the
    // companion app needs to be updated before use.
    String updateCompanionAppDescription;

    // The notification description for once we have finished pairing and the
    // companion app needs to be installed.
    String downloadCompanionAppDescription;

    // The notification title when a pairing fails.
    String unableToConnectTitle;

    // The notification summary when a pairing fails.
    String unableToConnectDescription;

    // The description that helps user initially paired with device.
    String initialPairingDescription;

    // The description that let user open the companion app.
    String connectSuccessCompanionAppInstalled;

    // The description that let user download the companion app.
    String connectSuccessCompanionAppNotInstalled;

    // The description that reminds user there is a paired device nearby.
    String subsequentPairingDescription;

    // The description that reminds users opt in their device.
    String retroactivePairingDescription;

    // The description that indicates companion app is about to launch.
    String waitLaunchCompanionAppDescription;

    // The description that indicates go to bluetooth settings when connection
    // fail.
    String failConnectGoToSettingsDescription;
}