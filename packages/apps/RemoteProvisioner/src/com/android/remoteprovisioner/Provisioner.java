/**
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

package com.android.remoteprovisioner;

import android.annotation.NonNull;
import android.content.Context;
import android.hardware.security.keymint.DeviceInfo;
import android.hardware.security.keymint.ProtectedData;
import android.security.IGenerateRkpKeyService;
import android.security.remoteprovisioning.IRemoteProvisioning;
import android.util.Log;

import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Provides an easy package to run the provisioning process from start to finish, interfacing
 * with the remote provisioning system service and the server backend in order to provision
 * attestation certificates to the device.
 */
public class Provisioner {
    protected static final String TAG = "RemoteProvisioningService";

    /**
     * Drives the process of provisioning certs. The method passes the data fetched from the
     * provisioning server along with the requested number of keys to the remote provisioning
     * system backend. The backend will talk to the underlying IRemotelyProvisionedComponent
     * interface in order to get a CSR bundle generated, along with an encrypted package containing
     * metadata that the server needs in order to make decisions about provisioning.
     *
     * This method then passes that bundle back out to the server backend, waits for the response,
     * and, if successful, passes the certificate chains back to the remote provisioning service to
     * be stored and later assigned to apps requesting a key attestation.
     *
     * @param numKeys The number of keys to be signed. The service will do a best-effort to
     *                provision the number requested, but if the number requested is larger
     *                than the number of unsigned attestation key pairs available, it will
     *                only sign the number that is available at time of calling.
     * @param secLevel Which KM instance should be used to provision certs.
     * @param geekChain The certificate chain that signs the endpoint encryption key.
     * @param challenge A server provided challenge to ensure freshness of the response.
     * @param binder The IRemoteProvisioning binder interface needed by the method to handle talking
     *               to the remote provisioning system component.
     * @param context The application context object which enables this method to make use of
     *                SettingsManager.
     * @param metrics Datapoint collector for logging metrics.
     * @return The number of certificates provisioned. Ideally, this should equal {@code numKeys}.
     */
    public static int provisionCerts(int numKeys, int secLevel, byte[] geekChain, byte[] challenge,
            @NonNull IRemoteProvisioning binder, Context context,
            ProvisionerMetrics metrics) throws
            RemoteProvisioningException {
        Log.i(TAG, "Request for " + numKeys + " keys to be provisioned.");
        if (numKeys < 1) {
            throw new RemoteProvisioningException(IGenerateRkpKeyService.Status.INTERNAL_ERROR,
                    "Request at least 1 key to be signed. Num requested: " + numKeys);
        }
        DeviceInfo deviceInfo = new DeviceInfo();
        ProtectedData protectedData = new ProtectedData();
        byte[] macedKeysToSign = SystemInterface.generateCsr(SettingsManager.isTestMode(), numKeys,
                secLevel, geekChain, challenge, protectedData, deviceInfo, binder, metrics);
        if (macedKeysToSign == null || protectedData.protectedData == null
                || deviceInfo.deviceInfo == null) {
            throw new RemoteProvisioningException(IGenerateRkpKeyService.Status.INTERNAL_ERROR,
                    "Keystore failed to generate a payload");
        }
        byte[] certificateRequest =
                CborUtils.buildCertificateRequest(deviceInfo.deviceInfo,
                                                  challenge,
                                                  protectedData.protectedData,
                                                  macedKeysToSign,
                                                  CborUtils.buildUnverifiedDeviceInfo());
        if (certificateRequest == null) {
            throw new RemoteProvisioningException(IGenerateRkpKeyService.Status.INTERNAL_ERROR,
                    "Failed to serialize the payload generated by keystore.");
        }
        List<byte[]> certChains = ServerInterface.requestSignedCertificates(context,
                        certificateRequest, challenge, metrics);
        if (certChains == null) {
            // This is marked as an internal error, because ServerInterface should never return
            // null, and if it does it's indicative of a bug.
            throw new RemoteProvisioningException(IGenerateRkpKeyService.Status.INTERNAL_ERROR,
                    "Server response failed on provisioning attempt.");
        }
        Log.i(TAG, "Received " + certChains.size() + " certificate chains from the server.");
        int provisioned = 0;
        for (byte[] certChain : certChains) {
            // DER encoding specifies leaf to root ordering. Pull the public key and expiration
            // date from the leaf.
            X509Certificate cert;
            try {
                cert = X509Utils.formatX509Certs(certChain)[0];
            } catch (CertificateException e) {
                throw new RemoteProvisioningException(IGenerateRkpKeyService.Status.INTERNAL_ERROR,
                        "Failed to interpret DER encoded certificate chain", e);
            }
            // getTime returns the time in *milliseconds* since the epoch.
            long expirationDate = cert.getNotAfter().getTime();
            byte[] rawPublicKey = X509Utils.getAndFormatRawPublicKey(cert);
            if (rawPublicKey == null) {
                Log.e(TAG, "Skipping malformed public key.");
                continue;
            }
            try {
                if (SystemInterface.provisionCertChain(rawPublicKey, cert.getEncoded(), certChain,
                                                       expirationDate, secLevel, binder, metrics)) {
                    provisioned++;
                }
            } catch (CertificateEncodingException e) {
                throw new RemoteProvisioningException(IGenerateRkpKeyService.Status.INTERNAL_ERROR,
                        "Error re-encoding the decoded batch cert", e);
            }
        }

        // In lieu of metrics, do our best to spot when things are "going really badly" by looking
        // for edge cases where fewer than half of the key requests succeed. In the future, we
        // should add metrics to get a picture of how healthy this process is.
        if (provisioned < (numKeys / 2)) {
            throw new RemoteProvisioningException(IGenerateRkpKeyService.Status.INTERNAL_ERROR,
                    "Requested " + numKeys + " keys, provisioned " + provisioned);
        }

        Log.i(TAG, "In provisionCerts: Requested " + numKeys + " keys. "
                   + provisioned + " were provisioned.");
        return provisioned;
    }
}
