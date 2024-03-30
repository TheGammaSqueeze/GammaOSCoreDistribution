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

/**
 * Metadata of a Fast Pair device keyed by AntispoofKey,
 * Used by initial pairing without account association.
 *
 * {@hide}
 */
parcelable FastPairAntispoofKeyDeviceMetadataParcel {
    // Anti-spoof public key.
    byte[] antispoofPublicKey;

    // Fast Pair device metadata for the Fast Pair device.
    FastPairDeviceMetadataParcel deviceMetadata;
}