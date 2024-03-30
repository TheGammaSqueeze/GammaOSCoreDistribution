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

import static java.lang.Math.min;

import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.security.remoteprovisioning.AttestationPoolStatus;
import android.security.remoteprovisioning.IRemoteProvisioning;
import android.security.remoteprovisioning.ImplInfo;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.time.Duration;

/**
 * A class that extends Worker in order to be scheduled to check the status of the attestation
 * key pool at regular intervals. If the job determines that more keys need to be generated and
 * signed, it drives that process.
 */
public class PeriodicProvisioner extends Worker {

    private static final int FAILURE_MAXIMUM = 5;
    private static final int SAFE_CSR_BATCH_SIZE = 20;

    // How long to wait in between key pair generations to avoid flooding keystore with requests.
    private static final Duration KEY_GENERATION_PAUSE = Duration.ofMillis(1000);

    private static final String SERVICE = "android.security.remoteprovisioning";
    private static final String TAG = "RemoteProvisioningService";
    private Context mContext;

    public PeriodicProvisioner(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        mContext = context;
    }

    /**
     * Overrides the default doWork method to handle checking and provisioning the device.
     */
    @Override
    public Result doWork() {
        Log.i(TAG, "Waking up; checking provisioning state.");
        try (ProvisionerMetrics metrics = ProvisionerMetrics.createScheduledAttemptMetrics(
                mContext)) {
            IRemoteProvisioning binder =
                    IRemoteProvisioning.Stub.asInterface(ServiceManager.getService(SERVICE));
            if (binder == null) {
                Log.e(TAG, "Binder returned null pointer to RemoteProvisioning service.");
                metrics.setStatus(ProvisionerMetrics.Status.INTERNAL_ERROR);
                return Result.failure();
            }
            ImplInfo[] implInfos = binder.getImplementationInfo();
            if (implInfos == null) {
                Log.e(TAG, "No instances of IRemotelyProvisionedComponent registered in "
                           + SERVICE);
                metrics.setStatus(ProvisionerMetrics.Status.NO_PROVISIONING_NEEDED);
                return Result.failure();
            }
            int[] keysNeededForSecLevel = new int[implInfos.length];
            GeekResponse resp = null;
            if (SettingsManager.getExtraSignedKeysAvailable(mContext) == 0) {
                // Provisioning has been purposefully disabled in the past. Go ahead and grab
                // an EEK just to see if provisioning should resume.
                resp = fetchGeekAndUpdate(binder, metrics);
                if (resp.numExtraAttestationKeys == 0) {
                    metrics.setEnablement(ProvisionerMetrics.Enablement.DISABLED);
                    metrics.setStatus(ProvisionerMetrics.Status.PROVISIONING_DISABLED);
                    return Result.success();
                }
            }
            boolean provisioningNeeded =
                    isProvisioningNeeded(binder,
                                         SettingsManager.getExpirationTime(mContext).toEpochMilli(),
                                         implInfos, keysNeededForSecLevel, metrics);
            if (!provisioningNeeded) {
                metrics.setStatus(ProvisionerMetrics.Status.NO_PROVISIONING_NEEDED);
                return Result.success();
            }
            // Resp may already be populated in the extremely rare case that this job is executing
            // to resume provisioning for the first time after a server-induced RKP shutdown. Grab
            // a fresh response anyways to refresh the challenge.
            resp = fetchGeekAndUpdate(binder, metrics);
            if (resp.numExtraAttestationKeys == 0) {
                metrics.setEnablement(ProvisionerMetrics.Enablement.DISABLED);
                metrics.setStatus(ProvisionerMetrics.Status.PROVISIONING_DISABLED);
                return Result.success();
            } else {
                // Just in case we got an updated config, let's recalculate how many keys need to
                // be provisioned.
                if (!isProvisioningNeeded(binder,
                        SettingsManager.getExpirationTime(mContext).toEpochMilli(),
                        implInfos, keysNeededForSecLevel, metrics)) {
                    metrics.setStatus(ProvisionerMetrics.Status.NO_PROVISIONING_NEEDED);
                    return Result.success();
                }
            }
            for (int i = 0; i < implInfos.length; i++) {
                // Break very large CSR requests into chunks, so as not to overwhelm the
                // backend.
                int keysToProvision = keysNeededForSecLevel[i];
                batchProvision(binder, mContext, keysToProvision, implInfos[i].secLevel,
                               resp.getGeekChain(implInfos[i].supportedCurve), resp.getChallenge(),
                               metrics);
            }
            return Result.success();
        } catch (RemoteException e) {
            Log.e(TAG, "Error on the binder side during provisioning.", e);
            return Result.failure();
        } catch (InterruptedException e) {
            Log.e(TAG, "Provisioner thread interrupted.", e);
            return Result.failure();
        } catch (RemoteProvisioningException e) {
            Log.e(TAG, "Encountered RemoteProvisioningException", e);
            if (SettingsManager.getFailureCounter(mContext) > FAILURE_MAXIMUM) {
                Log.e(TAG, "Too many failures, resetting defaults.");
                SettingsManager.resetDefaultConfig(mContext);
            }
            return Result.failure();
        }
    }

    /**
     * Fetch a GEEK from the server and update SettingsManager appropriately with the return
     * values. This will also delete all keys in the attestation key pool if the server has
     * indicated that RKP should be turned off.
     */
    private GeekResponse fetchGeekAndUpdate(IRemoteProvisioning binder,
            ProvisionerMetrics metrics)
            throws RemoteException, RemoteProvisioningException {
        GeekResponse resp = ServerInterface.fetchGeek(mContext, metrics);
        SettingsManager.setDeviceConfig(mContext,
                    resp.numExtraAttestationKeys,
                    resp.timeToRefresh,
                    resp.provisioningUrl);

        if (resp.numExtraAttestationKeys == 0) {
            // The server has indicated that provisioning is disabled.
            try (ProvisionerMetrics.StopWatch ignored = metrics.startBinderWait()) {
                binder.deleteAllKeys();
            }
        }
        return resp;
    }

    public static void batchProvision(IRemoteProvisioning binder, Context context,
            int keysToProvision, int secLevel,
            byte[] geekChain, byte[] challenge,
            ProvisionerMetrics metrics)
            throws RemoteException, RemoteProvisioningException {
        while (keysToProvision != 0) {
            int batchSize = min(keysToProvision, SAFE_CSR_BATCH_SIZE);
            Log.i(TAG, "Requesting " + batchSize + " keys to be provisioned.");
            Provisioner.provisionCerts(batchSize,
                                       secLevel,
                                       geekChain,
                                       challenge,
                                       binder,
                                       context,
                                       metrics);
            keysToProvision -= batchSize;
        }
        metrics.setStatus(ProvisionerMetrics.Status.KEYS_SUCCESSFULLY_PROVISIONED);
    }

    private boolean isProvisioningNeeded(
            IRemoteProvisioning binder, long expiringBy, ImplInfo[] implInfos,
            int[] keysNeededForSecLevel, ProvisionerMetrics metrics)
            throws InterruptedException, RemoteException {
        if (implInfos == null || keysNeededForSecLevel == null
                || keysNeededForSecLevel.length != implInfos.length) {
            Log.e(TAG, "Invalid argument.");
            return false;
        }
        boolean provisioningNeeded = false;
        for (int i = 0; i < implInfos.length; i++) {
            keysNeededForSecLevel[i] =
                    generateNumKeysNeeded(binder,
                               mContext,
                               expiringBy,
                               implInfos[i].secLevel,
                               metrics);
            if (keysNeededForSecLevel[i] > 0) {
                provisioningNeeded = true;
            }
        }
        return provisioningNeeded;
    }

    /**
     * This method will generate and bundle up keys for signing to make sure that there will be
     * enough keys available for use by the system when current keys expire.
     *
     * Enough keys is defined by checking how many keys are currently assigned to apps and
     * generating enough keys to cover any expiring certificates plus a bit of buffer room
     * defined by {@code sExtraSignedKeysAvailable}.
     *
     * This allows devices to dynamically resize their key pools as the user downloads and
     * removes apps that may also use attestation.
     */
    public static int generateNumKeysNeeded(IRemoteProvisioning binder, Context context,
            long expiringBy, int secLevel,
            ProvisionerMetrics metrics)
            throws InterruptedException, RemoteException {
        AttestationPoolStatus pool =
                SystemInterface.getPoolStatus(expiringBy, secLevel, binder, metrics);
        if (pool == null) {
            Log.e(TAG, "Failed to fetch pool status.");
            return 0;
        }
        Log.i(TAG, "Pool status.\nTotal: " + pool.total
                   + "\nAttested: " + pool.attested
                   + "\nUnassigned: " + pool.unassigned
                   + "\nExpiring: " + pool.expiring);
        StatsProcessor.PoolStats stats = StatsProcessor.processPool(
                    pool, SettingsManager.getExtraSignedKeysAvailable(context));
        if (!stats.provisioningNeeded) {
            Log.i(TAG, "No provisioning needed.");
            return 0;
        }
        Log.i(TAG, "Need to generate " + stats.keysToGenerate + " keys.");
        int generated;
        for (generated = 0; generated < stats.keysToGenerate; generated++) {
            SystemInterface.generateKeyPair(SettingsManager.isTestMode(), secLevel, binder,
                    metrics);
            // Prioritize provisioning if there are no keys available. No keys being available
            // indicates that this is the first time a device is being brought online.
            if (pool.total != 0) {
                Thread.sleep(KEY_GENERATION_PAUSE.toMillis());
            }
        }
        Log.i(TAG, "Generated " + generated + " keys. " + stats.unattestedKeys
                    + " keys were also available for signing previous to generation.");
        return stats.idealTotalSignedKeys;
    }


}
