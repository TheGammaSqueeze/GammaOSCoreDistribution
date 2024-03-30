/*
 * Copyright 2021 The Android Open Source Project
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

package android.hardware.identity;

import android.hardware.identity.CipherSuite;
import android.hardware.identity.IIdentityCredential;

/**
 * An interface to present multiple credentials in the same session.
 *
 * This interface was introduced in API version 4.
 *
 */
@VintfStability
interface IPresentationSession {
    /**
     * Gets the ephemeral EC key pair to be used in establishing a secure session with a reader.
     * This method returns the private key so the caller can perform an ECDH key agreement operation
     * with the reader.  The reason for generating the key pair in the secure environment is so that
     * the secure environment knows what public key to expect to find in the session transcript
     * when presenting credentials.
     *
     * The generated key matches the selected cipher suite of the presentation session (e.g. EC
     * key using the P-256 curve).
     *
     * @return the private key, in DER format as specified in RFC 5915.
     */
    byte[] getEphemeralKeyPair();

    /**
     * Gets the challenge value to be used for proving successful user authentication. This
     * is to be included in the authToken passed to the IIdentityCredential.startRetrieval()
     * method and the verificationToken passed to the IIdentityCredential.setVerificationToken()
     * method.
     *
     * @return challenge, a non-zero number.
     */
    long getAuthChallenge();

    /**
     * Sets the public part of the reader's ephemeral key pair to be used to complete
     * an ECDH key agreement for the session.
     *
     * The curve of the key must match the curve for the key returned by getEphemeralKeyPair().
     *
     * This method may only be called once per instance. If called more than once, STATUS_FAILED
     * must be returned.
     *
     * @param publicKey contains the reader's ephemeral public key, in uncompressed
     *        form (e.g. 0x04 || X || Y).
     */
    void setReaderEphemeralPublicKey(in byte[] publicKey);

    /**
     * Sets the session transcript for the session.
     *
     * This can be empty but if it's non-empty it must be valid CBOR.
     *
     * This method may only be called once per instance. If called more than once, STATUS_FAILED
     * must be returned.
     *
     * @param sessionTrancsript the session transcript.
     */
    void setSessionTranscript(in byte[] sessionTranscript);

    /**
     * getCredential() retrieves an IIdentityCredential interface for presentation in the
     * current presentation session.
     *
     * On the returned instance only the methods startRetrieval(), startRetrieveEntryValue(),
     * retrieveEntryValue(), finishRetrieval(), setRequestedNamespaces(), setVerificationToken()
     * may be called. Other methods will fail with STATUS_FAILED.
     *
     * The implementation is expected to get the session transcript, ephemeral key, reader
     * ephemeral key, and auth challenge from this instance.
     *
     * @param credentialData is a CBOR-encoded structure containing metadata about the credential
     *     and an encrypted byte array that contains data used to secure the credential.  See the
     *     return argument of the same name in IWritableIdentityCredential.finishAddingEntries().
     *
     *     Note that the format of credentialData may depend on the feature version.
     *     Implementations must support credentialData created by an earlier feature version.
     *
     * @return an IIdentityCredential interface that provides operations on the Credential.
     */
    IIdentityCredential getCredential(in byte[] credentialData);
}
