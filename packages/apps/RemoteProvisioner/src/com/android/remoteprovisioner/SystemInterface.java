/**
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

package com.android.remoteprovisioner;

import android.annotation.NonNull;
import android.hardware.security.keymint.DeviceInfo;
import android.hardware.security.keymint.ProtectedData;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.security.remoteprovisioning.AttestationPoolStatus;
import android.security.remoteprovisioning.IRemoteProvisioning;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;

/**
 * Provides convenience methods for interfacing with the android.security.remoteprovisioning system
 * service. Since the remoteprovisioning API is internal only and subject to change, it is handy
 * to have an abstraction layer to reduce the impact of these changes on the app.
 */
public class SystemInterface {

    private static final String TAG = "RemoteProvisioner";

    private static byte[] makeProtectedHeaders() throws CborException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new CborBuilder()
                .addMap()
                    .put(1, 5)
                .end()
                .build());
        return baos.toByteArray();
    }

    private static byte[] encodePayload(List<DataItem> keys) throws CborException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ArrayBuilder<CborBuilder> builder = new CborBuilder().addArray();
        for (int i = 1; i < keys.size(); i++) {
            builder = builder.add(keys.get(i));
        }
        new CborEncoder(baos).encode(builder.end().build());
        return baos.toByteArray();
    }

    /**
     * Sends a generateCsr request over the binder interface. `dataBlob` is an out parameter that
     * will be populated by the underlying binder service.
     */
    public static byte[] generateCsr(boolean testMode, int numKeys, int secLevel,
            byte[] geekChain, byte[] challenge, ProtectedData protectedData, DeviceInfo deviceInfo,
            @NonNull IRemoteProvisioning binder,
            ProvisionerMetrics metrics) {
        try {
            Log.i(TAG, "Packaging " + numKeys + " keys into a CSR. Test: " + testMode);
            ProtectedData dataBundle = new ProtectedData();
            byte[] macedPublicKeys = null;
            try (ProvisionerMetrics.StopWatch ignored = metrics.startBinderWait()) {
                macedPublicKeys = binder.generateCsr(testMode,
                                                     numKeys,
                                                     geekChain,
                                                     challenge,
                                                     secLevel,
                                                     protectedData,
                                                     deviceInfo);
            }

            if (macedPublicKeys == null) {
                Log.e(TAG, "Keystore didn't generate a CSR successfully.");
                metrics.setStatus(ProvisionerMetrics.Status.GENERATE_CSR_FAILED);
                return null;
            }
            ByteArrayInputStream bais = new ByteArrayInputStream(macedPublicKeys);
            List<DataItem> dataItems = new CborDecoder(bais).decode();
            List<DataItem> macInfo = ((Array) dataItems.get(0)).getDataItems();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new CborEncoder(baos).encode(new CborBuilder()
                    .addArray()
                        .add(makeProtectedHeaders())
                        .addMap() //unprotected headers
                            .end()
                        .add(encodePayload(macInfo))
                        .add(macInfo.get(0))
                        .end()
                    .build());
            return baos.toByteArray();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to generate CSR blob", e);
        } catch (ServiceSpecificException e) {
            Log.e(TAG, "Failure in Keystore or Keymint to facilitate blob generation.", e);
        } catch (CborException e) {
            Log.e(TAG, "Failed to parse/build CBOR", e);
        }
        metrics.setStatus(ProvisionerMetrics.Status.GENERATE_CSR_FAILED);
        return null;
    }

    /**
     * Sends a provisionCertChain request down to the underlying remote provisioning binder service.
     */
    public static boolean provisionCertChain(byte[] rawPublicKey, byte[] encodedCert,
            byte[] certChain,
            long expirationDate, int secLevel,
            IRemoteProvisioning binder,
            ProvisionerMetrics metrics) {
        try (ProvisionerMetrics.StopWatch ignored = metrics.startBinderWait()) {
            binder.provisionCertChain(rawPublicKey, encodedCert, certChain,
                    expirationDate, secLevel);
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, "Error on the binder side when attempting to provision the signed chain",
                    e);
        } catch (ServiceSpecificException e) {
            Log.e(TAG, "Error on the Keystore side", e);
        }
        metrics.setStatus(ProvisionerMetrics.Status.INSERT_CHAIN_INTO_POOL_FAILED);
        return false;
    }

    /**
     * Returns the pool status for a given {@code secLevel}, with the expiration date configured to
     * the value passed in as {@code expiringBy}.
     */
    public static AttestationPoolStatus getPoolStatus(long expiringBy,
                                                      int secLevel,
                                                      IRemoteProvisioning binder,
                                                      ProvisionerMetrics metrics)
            throws RemoteException {
        try (ProvisionerMetrics.StopWatch ignored = metrics.startBinderWait()) {
            AttestationPoolStatus status = binder.getPoolStatus(expiringBy, secLevel);
            Log.i(TAG, "Pool status " + status.attested + ", " + status.unassigned + ", "
                    + status.expiring + ", " + status.total);
            metrics.setIsKeyPoolEmpty(status.unassigned == 0);
            return status;
        } catch (ServiceSpecificException e) {
            Log.e(TAG, "Failure in Keystore", e);
            metrics.setStatus(ProvisionerMetrics.Status.GET_POOL_STATUS_FAILED);
            throw new RemoteException(e);
        }
    }

    /**
     * Generates an attestation key pair.
     *
     * @param isTestMode Whether or not to generate a test key, which would accept any EEK later.
     * @param secLevel Which security level to generate the key for.
     */
    public static void generateKeyPair(boolean isTestMode, int secLevel, IRemoteProvisioning binder,
            ProvisionerMetrics metrics)
            throws RemoteException {
        try (ProvisionerMetrics.StopWatch ignored = metrics.startBinderWait()) {
            binder.generateKeyPair(isTestMode, secLevel);
        } catch (ServiceSpecificException e) {
            Log.e(TAG, "Failure in Keystore or KeyMint.", e);
            metrics.setStatus(ProvisionerMetrics.Status.GENERATE_KEYPAIR_FAILED);
            throw new RemoteException(e);
        }
    }
}
