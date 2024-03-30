/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.hardware.security.keymint;

/**
 * RpcHardwareInfo is the hardware information returned by calling RemotelyProvisionedComponent
 * getHardwareInfo()
 * @hide
 */
@VintfStability
@RustDerive(Clone=true, Eq=true, PartialEq=true, Ord=true, PartialOrd=true, Hash=true)
parcelable RpcHardwareInfo {
    const int CURVE_NONE = 0;
    const int CURVE_P256 = 1;
    const int CURVE_25519 = 2;

    /**
     * Implementation version of the remotely provisioned component hardware.  The version number is
     * implementation defined, and not necessarily globally meaningful.  The version is used to
     * distinguish between different versions of a given implementation.
     */
    int versionNumber;

    /**
     * rpcAuthorName is the name of the author of the IRemotelyProvisionedComponent implementation
     * (organization name, not individual). This name is implementation defined, so it can be used
     * to distinguish between different implementations from the same author.
     */
    @utf8InCpp String rpcAuthorName;

    /**
     * supportedEekCurve returns an int representing which curve is supported for validating
     * signatures over the Endpoint Encryption Key certificate chain and for using the corresponding
     * signed encryption key in ECDH. Only one curve should be supported, with preference for 25519
     * if it's available. These values are defined as constants above.
     *
     * CURVE_NONE is made the default to help ensure that an implementor doesn't accidentally forget
     * to provide the correct information here, as the VTS tests will check to make certain that
     * a passing implementation does not provide CURVE_NONE.
     */
    int supportedEekCurve = CURVE_NONE;

    /**
     * uniqueId is an opaque identifier for this IRemotelyProvisionedComponent implementation. The
     * client should NOT interpret the content of the identifier in any way. The client can only
     * compare identifiers to determine if two IRemotelyProvisionedComponents share the same
     * implementation. Each IRemotelyProvisionedComponent implementation must have a distinct
     * identifier from all other implementations, and it must be consistent across all devices.
     * It's critical that this identifier not be usable to uniquely identify a specific device.
     *
     * This identifier must be consistent across reboots, as it is used to store and track
     * provisioned keys in a persistent, on-device database.
     *
     * uniqueId may not be empty, and must not be any longer than 32 characters.
     *
     * A recommended construction for this value is "[Vendor] [Component Name] [Major Version]",
     * e.g. "Google Trusty KeyMint 1".
     *
     * This field was added in API version 2.
     *
     */
    @nullable @utf8InCpp String uniqueId;
}
