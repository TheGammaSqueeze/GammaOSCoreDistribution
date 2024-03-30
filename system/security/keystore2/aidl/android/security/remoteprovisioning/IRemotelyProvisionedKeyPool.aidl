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

package android.security.remoteprovisioning;

import android.security.remoteprovisioning.RemotelyProvisionedKey;

/**
 * This is the interface providing access to remotely-provisioned attestation keys
 * for an `IRemotelyProvisionedComponent`.
 *
 * @hide
 */
interface IRemotelyProvisionedKeyPool {

    /**
     * Fetches an attestation key for the given uid and `IRemotelyProvisionedComponent`, as
     * identified by the given id.

     * Callers require the keystore2::get_attestation_key permission.
     *
     * ## Error conditions
     * `android.system.keystore2.ResponseCode::PERMISSION_DENIED` if the caller does not have the
     *      `keystore2::get_attestation_key` permission
     *
     * @param clientUid The client application for which an attestation key is needed.
     *
     * @param irpcId The unique identifier for the `IRemotelyProvisionedComponent` for which a key
     *      is requested. This id may be retrieved from a given component via the
     *      `IRemotelyProvisionedComponent::getHardwareInfo` function.
     *
     * @return A `RemotelyProvisionedKey` parcelable containing a key and certification chain for
     *      the given `IRemotelyProvisionedComponent`.
     */
    RemotelyProvisionedKey getAttestationKey(in int clientUid, in @utf8InCpp String irpcId);
}
