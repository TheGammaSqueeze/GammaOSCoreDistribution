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

package android.security.dice;

import android.hardware.security.dice.Bcc;
import android.hardware.security.dice.BccHandover;
import android.hardware.security.dice.InputValues;
import android.hardware.security.dice.Signature;

/**
 * An implementation of IDiceNode provides access to DICE secrets to its clients. It
 * uses binder's caller UID and security context to identify its callers and assures
 * That clients can only access their specific DICE secrets.
 * It may operate in two different modes, resident mode and proxy mode.
 *
 * ## Resident mode.
 * In resident mode, the node is in possession of the secrets corresponding to its level in
 * the dice tree. It can act as root of the sub tree that it serves. The secrets are memory
 * resident in the node. It identifies its callers and prepends the caller's identity to the
 * request's vector of input values. It then derives the required secrets by iterating through
 * the request's vector of input values in ascending order.
 *
 * ## Proxy mode.
 * In proxy mode, the node has a connection to a parent node. It serves its callers by verifying
 * their identity, by prefixing the client's vector of input values with client's identity, and
 * forwarding the request to the next level up.
 *
 * The modes are implementation details that are completely transparent to the clients.
 *
 * Privacy: Unprivileged apps may not use this service ever because it may provide access to a
 * device specific id that is stable across reinstalls, reboots, and applications.
 *
 * @hide
 */
@SensitiveData
interface IDiceNode {
    /**
     * Uses the a key derived from the caller's attestation secret to sign the payload using
     * RFC 8032 PureEd25519 and returns the signature. The payload is limited to 1024 bytes.
     *
     * ## Error as service specific exception:
     *     ResponseCode::PERMISSION_DENIED if the caller does not have the use_sign permission.
     */
    Signature sign(in InputValues[] id, in byte[] payload);

    /**
     * Returns the attestation certificate chain of the caller if `inputValues` is empty or the
     * chain to the given child of the caller identified by the `inputValues` vector.
     *
     * ## Error as service specific exception:
     *     ResponseCode::PERMISSION_DENIED if the caller does not have the get_attestation_chain
     *          permission.
     */
    Bcc getAttestationChain(in InputValues[] inputValues);

    /**
     * This function allows a client to become a resident node. Called with empty InputValues
     * vectors, an implementation returns the client's DICE secrets. If inputValues is
     * not empty, the appropriate derivations are performed starting from the client's level.
     * The function must never return secrets pertaining to the implementation or a parent
     * thereof in the DICE hierarchy.
     *
     * ## Error as service specific exception:
     *     ResponseCode::PERMISSION_DENIED if the implementation does not allow resident nodes
     *     at the client's level.
     */
    BccHandover derive(in InputValues[] inputValues);

    /**
     * The client demotes itself to the given identity. When serving the calling client,
     * the implementation must append the given identities. Essentially, the client assumes
     * the identity of one of its children. This operation is not reversible, i.e., there
     * is no promotion. Further demotion is possible.
     *
     * If the operation fails for any reason. No further services must be provided. Ideally,
     * a device shutdown/reboot is triggered.
     *
     * ## Error as service specific exception:
     *     ResponseCode::PERMISSION_DENIED if the caller does not have the demote permission.
     */
    void demote(in InputValues[] inputValues);
}
