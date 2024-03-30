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
 * Fast Pair Discovery Item.
 * @hide
 */
// TODO(b/204780849): remove unnecessary fields and polish comments.
parcelable FastPairDiscoveryItemParcel {
  // Offline item: unique ID generated on client.
  // Online item: unique ID generated on server.
  String id;

  // The most recent all upper case mac associated with this item.
  // (Mac-to-DiscoveryItem is a many-to-many relationship)
  String macAddress;

  String actionUrl;

  // The bluetooth device name from advertisement
  String deviceName;

  // Item's title
  String title;

  // Item's description.
  String description;

  // The URL for display
  String displayUrl;

  // Client timestamp when the beacon was last observed in BLE scan.
  long lastObservationTimestampMillis;

  // Client timestamp when the beacon was first observed in BLE scan.
  long firstObservationTimestampMillis;

  // Item's current state. e.g. if the item is blocked.
  int state;

  // The resolved url type for the action_url.
  int actionUrlType;

  // The timestamp when the user is redirected to Play Store after clicking on
  // the item.
  long pendingAppInstallTimestampMillis;

  // Beacon's RSSI value
  int rssi;

  // Beacon's tx power
  int txPower;

  // Human readable name of the app designated to open the uri
  // Used in the second line of the notification, "Open in {} app"
  String appName;

  // Package name of the App that owns this item.
  String packageName;

  // TriggerId identifies the trigger/beacon that is attached with a message.
  // It's generated from server for online messages to synchronize formatting
  // across client versions.
  // Example:
  // * BLE_UID: 3||deadbeef
  // * BLE_URL: http://trigger.id
  // See go/discovery-store-message-and-trigger-id for more details.
  String triggerId;

  // Bytes of item icon in PNG format displayed in Discovery item list.
  byte[] iconPng;

  // A FIFE URL of the item icon displayed in Discovery item list.
  String iconFifeUrl;

  // Fast Pair antispoof key.
  byte[] authenticationPublicKeySecp256r1;
}