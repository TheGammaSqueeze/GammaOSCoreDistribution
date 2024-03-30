// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package android.nearby.aidl;

import android.nearby.aidl.FastPairDeviceMetadataParcel;
import android.nearby.aidl.FastPairDiscoveryItemParcel;

/**
 * Metadata of a Fast Pair device associated with an account.
 * {@hide}
 */
 // TODO(b/204780849): remove unnecessary fields and polish comments.
parcelable FastPairAccountKeyDeviceMetadataParcel {
    // Key of the Fast Pair device associated with the account.
    byte[] deviceAccountKey;
    // Hash function of device account key and public bluetooth address.
    byte[] sha256DeviceAccountKeyPublicAddress;
    // Fast Pair device metadata for the Fast Pair device.
    FastPairDeviceMetadataParcel metadata;
    // Fast Pair discovery item tied to both the Fast Pair device and the
    // account.
    FastPairDiscoveryItemParcel discoveryItem;
}