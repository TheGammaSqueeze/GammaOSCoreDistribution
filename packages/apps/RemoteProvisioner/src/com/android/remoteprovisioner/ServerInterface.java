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

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.security.IGenerateRkpKeyService;
import android.util.Base64;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.List;

/**
 * Provides convenience methods for interfacing with the remote provisioning server.
 */
public class ServerInterface {

    private static final int TIMEOUT_MS = 20000;

    private static final String TAG = "ServerInterface";
    private static final String GEEK_URL = ":fetchEekChain";
    private static final String CERTIFICATE_SIGNING_URL = ":signCertificates?challenge=";

    /**
     * Ferries the CBOR blobs returned by KeyMint to the provisioning server. The data sent to the
     * provisioning server contains the MAC'ed CSRs and encrypted bundle containing the MAC key and
     * the hardware unique public key.
     *
     * @param context The application context which is required to use SettingsManager.
     * @param csr The CBOR encoded data containing the relevant pieces needed for the server to
     *                    sign the CSRs. The data encoded within comes from Keystore / KeyMint.
     * @param challenge The challenge that was sent from the server. It is included here even though
     *                    it is also included in `cborBlob` in order to allow the server to more
     *                    easily reject bad requests.
     * @return A List of byte arrays, where each array contains an entire DER-encoded certificate
     *                    chain for one attestation key pair.
     */
    public static List<byte[]> requestSignedCertificates(Context context, byte[] csr,
            byte[] challenge, ProvisionerMetrics metrics) throws RemoteProvisioningException {
        checkDataBudget(context, metrics);
        int bytesTransacted = 0;
        try (ProvisionerMetrics.StopWatch serverWaitTimer = metrics.startServerWait()) {
            URL url = new URL(SettingsManager.getUrl(context) + CERTIFICATE_SIGNING_URL
                              + Base64.encodeToString(challenge, Base64.URL_SAFE));
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setConnectTimeout(TIMEOUT_MS);
            con.setReadTimeout(TIMEOUT_MS);

            // May not be able to use try-with-resources here if the connection gets closed due to
            // the output stream being automatically closed.
            try (OutputStream os = con.getOutputStream()) {
                os.write(csr, 0, csr.length);
                bytesTransacted += csr.length;
            }

            metrics.setHttpStatusError(con.getResponseCode());
            if (con.getResponseCode() != HttpURLConnection.HTTP_OK) {
                serverWaitTimer.stop();
                int failures = SettingsManager.incrementFailureCounter(context);
                Log.e(TAG, "Server connection for signing failed, response code: "
                        + con.getResponseCode() + "\nRepeated failure count: " + failures);
                SettingsManager.consumeErrDataBudget(context, bytesTransacted);
                RemoteProvisioningException ex =
                        RemoteProvisioningException.createFromHttpError(con.getResponseCode());
                if (ex.getErrorCode() == IGenerateRkpKeyService.Status.DEVICE_NOT_REGISTERED) {
                    metrics.setStatus(ProvisionerMetrics.Status.SIGN_CERTS_DEVICE_NOT_REGISTERED);
                } else {
                    metrics.setStatus(ProvisionerMetrics.Status.SIGN_CERTS_HTTP_ERROR);
                }
                throw ex;
            }
            serverWaitTimer.stop();
            SettingsManager.clearFailureCounter(context);
            BufferedInputStream inputStream = new BufferedInputStream(con.getInputStream());
            ByteArrayOutputStream cborBytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read = 0;
            serverWaitTimer.start();
            while ((read = inputStream.read(buffer, 0, buffer.length)) != -1) {
                cborBytes.write(buffer, 0, read);
                bytesTransacted += read;
            }
            serverWaitTimer.stop();
            return CborUtils.parseSignedCertificates(cborBytes.toByteArray());
        } catch (SocketTimeoutException e) {
            Log.e(TAG, "Server timed out", e);
            metrics.setStatus(ProvisionerMetrics.Status.SIGN_CERTS_TIMED_OUT);
        } catch (IOException e) {
            Log.e(TAG, "Failed to request signed certificates from the server", e);
            metrics.setStatus(ProvisionerMetrics.Status.SIGN_CERTS_IO_EXCEPTION);
        }
        SettingsManager.incrementFailureCounter(context);
        SettingsManager.consumeErrDataBudget(context, bytesTransacted);
        throw makeNetworkError(context, "Error getting CSR signed.", metrics);
    }

    /**
     * Calls out to the specified backend servers to retrieve an Endpoint Encryption Key and
     * corresponding certificate chain to provide to KeyMint. This public key will be used to
     * perform an ECDH computation, using the shared secret to encrypt privacy sensitive components
     * in the bundle that the server needs from the device in order to provision certificates.
     *
     * A challenge is also returned from the server so that it can check freshness of the follow-up
     * request to get keys signed.
     *
     * @param context The application context which is required to use SettingsManager.
     * @param metrics
     * @return A GeekResponse object which optionally contains configuration data.
     */
    public static GeekResponse fetchGeek(Context context,
            ProvisionerMetrics metrics) throws RemoteProvisioningException {
        checkDataBudget(context, metrics);
        int bytesTransacted = 0;
        try {
            URL url = new URL(SettingsManager.getUrl(context) + GEEK_URL);
            ByteArrayOutputStream cborBytes = new ByteArrayOutputStream();
            try (ProvisionerMetrics.StopWatch serverWaitTimer = metrics.startServerWait()) {
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("POST");
                con.setConnectTimeout(TIMEOUT_MS);
                con.setReadTimeout(TIMEOUT_MS);
                con.setDoOutput(true);

                byte[] config = CborUtils.buildProvisioningInfo(context);
                try (OutputStream os = con.getOutputStream()) {
                    os.write(config, 0, config.length);
                    bytesTransacted += config.length;
                }

                metrics.setHttpStatusError(con.getResponseCode());
                if (con.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    serverWaitTimer.stop();
                    int failures = SettingsManager.incrementFailureCounter(context);
                    Log.e(TAG, "Server connection for GEEK failed, response code: "
                            + con.getResponseCode() + "\nRepeated failure count: " + failures);
                    SettingsManager.consumeErrDataBudget(context, bytesTransacted);
                    metrics.setStatus(ProvisionerMetrics.Status.FETCH_GEEK_HTTP_ERROR);
                    throw RemoteProvisioningException.createFromHttpError(con.getResponseCode());
                }
                serverWaitTimer.stop();
                SettingsManager.clearFailureCounter(context);
                BufferedInputStream inputStream = new BufferedInputStream(con.getInputStream());
                byte[] buffer = new byte[1024];
                int read = 0;
                serverWaitTimer.start();
                while ((read = inputStream.read(buffer, 0, buffer.length)) != -1) {
                    cborBytes.write(buffer, 0, read);
                    bytesTransacted += read;
                }
                inputStream.close();
            }
            GeekResponse resp = CborUtils.parseGeekResponse(cborBytes.toByteArray());
            if (resp == null) {
                metrics.setStatus(ProvisionerMetrics.Status.FETCH_GEEK_HTTP_ERROR);
                throw new RemoteProvisioningException(
                        IGenerateRkpKeyService.Status.HTTP_SERVER_ERROR,
                        "Response failed to parse.");
            }
            return resp;
        } catch (SocketTimeoutException e) {
            Log.e(TAG, "Server timed out", e);
            metrics.setStatus(ProvisionerMetrics.Status.FETCH_GEEK_TIMED_OUT);
        } catch (IOException e) {
            // This exception will trigger on a completely malformed URL.
            Log.e(TAG, "Failed to fetch GEEK from the servers.", e);
            metrics.setStatus(ProvisionerMetrics.Status.FETCH_GEEK_IO_EXCEPTION);
        }
        SettingsManager.incrementFailureCounter(context);
        SettingsManager.consumeErrDataBudget(context, bytesTransacted);
        throw makeNetworkError(context, "Error fetching GEEK", metrics);
    }

    private static void checkDataBudget(Context context, ProvisionerMetrics metrics)
            throws RemoteProvisioningException {
        if (!SettingsManager.hasErrDataBudget(context, null /* curTime */)) {
            metrics.setStatus(ProvisionerMetrics.Status.OUT_OF_ERROR_BUDGET);
            int bytesConsumed = SettingsManager.getErrDataBudgetConsumed(context);
            throw makeNetworkError(context,
                    "Out of data budget due to repeated errors. Consumed "
                    + bytesConsumed + " bytes.", metrics);
        }
    }

    private static RemoteProvisioningException makeNetworkError(Context context, String message,
            ProvisionerMetrics metrics) {
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            return new RemoteProvisioningException(
                    IGenerateRkpKeyService.Status.NETWORK_COMMUNICATION_ERROR, message);
        }
        metrics.setStatus(ProvisionerMetrics.Status.NO_NETWORK_CONNECTIVITY);
        return new RemoteProvisioningException(
                IGenerateRkpKeyService.Status.NO_NETWORK_CONNECTIVITY, message);
    }
}
