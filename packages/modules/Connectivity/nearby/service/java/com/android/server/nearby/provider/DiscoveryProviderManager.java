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

package com.android.server.nearby.provider;

import static android.nearby.ScanRequest.SCAN_TYPE_NEARBY_PRESENCE;

import static com.android.server.nearby.NearbyService.TAG;

import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.content.Context;
import android.nearby.IScanListener;
import android.nearby.NearbyDeviceParcelable;
import android.nearby.PresenceScanFilter;
import android.nearby.ScanFilter;
import android.nearby.ScanRequest;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.server.nearby.injector.Injector;
import com.android.server.nearby.metrics.NearbyMetrics;
import com.android.server.nearby.presence.PresenceDiscoveryResult;
import com.android.server.nearby.util.identity.CallerIdentity;
import com.android.server.nearby.util.permissions.DiscoveryPermissions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/** Manages all aspects of discovery providers. */
public class DiscoveryProviderManager implements AbstractDiscoveryProvider.Listener {

    protected final Object mLock = new Object();
    private final Context mContext;
    private final BleDiscoveryProvider mBleDiscoveryProvider;
    @Nullable private final ChreDiscoveryProvider mChreDiscoveryProvider;
    private @ScanRequest.ScanMode int mScanMode;
    private final Injector mInjector;

    @GuardedBy("mLock")
    private Map<IBinder, ScanListenerRecord> mScanTypeScanListenerRecordMap;

    @Override
    public void onNearbyDeviceDiscovered(NearbyDeviceParcelable nearbyDevice) {
        synchronized (mLock) {
            AppOpsManager appOpsManager = Objects.requireNonNull(mInjector.getAppOpsManager());
            for (IBinder listenerBinder : mScanTypeScanListenerRecordMap.keySet()) {
                ScanListenerRecord record = mScanTypeScanListenerRecordMap.get(listenerBinder);
                if (record == null) {
                    Log.w(TAG, "DiscoveryProviderManager cannot find the scan record.");
                    continue;
                }
                CallerIdentity callerIdentity = record.getCallerIdentity();
                if (!DiscoveryPermissions.noteDiscoveryResultDelivery(
                        appOpsManager, callerIdentity)) {
                    Log.w(TAG, "[DiscoveryProviderManager] scan permission revoked "
                            + "- not forwarding results");
                    try {
                        record.getScanListener().onError();
                    } catch (RemoteException e) {
                        Log.w(TAG, "DiscoveryProviderManager failed to report error.", e);
                    }
                    return;
                }

                if (nearbyDevice.getScanType() == SCAN_TYPE_NEARBY_PRESENCE) {
                    List<ScanFilter> presenceFilters =
                            record.getScanRequest().getScanFilters().stream()
                                    .filter(
                                            scanFilter ->
                                                    scanFilter.getType()
                                                            == SCAN_TYPE_NEARBY_PRESENCE)
                                    .collect(Collectors.toList());
                    Log.i(
                            TAG,
                            String.format("match with filters size: %d", presenceFilters.size()));
                    if (!presenceFilterMatches(nearbyDevice, presenceFilters)) {
                        continue;
                    }
                }
                try {
                    record.getScanListener()
                            .onDiscovered(
                                    PrivacyFilter.filter(
                                            record.getScanRequest().getScanType(), nearbyDevice));
                    NearbyMetrics.logScanDeviceDiscovered(
                            record.hashCode(), record.getScanRequest(), nearbyDevice);
                } catch (RemoteException e) {
                    Log.w(TAG, "DiscoveryProviderManager failed to report onDiscovered.", e);
                }
            }
        }
    }

    public DiscoveryProviderManager(Context context, Injector injector) {
        mContext = context;
        mBleDiscoveryProvider = new BleDiscoveryProvider(mContext, injector);
        Executor executor = Executors.newSingleThreadExecutor();
        mChreDiscoveryProvider =
                new ChreDiscoveryProvider(
                        mContext, new ChreCommunication(injector, executor), executor);
        mScanTypeScanListenerRecordMap = new HashMap<>();
        mInjector = injector;
    }

    /**
     * Registers the listener in the manager and starts scan according to the requested scan mode.
     */
    public boolean registerScanListener(ScanRequest scanRequest, IScanListener listener,
            CallerIdentity callerIdentity) {
        synchronized (mLock) {
            IBinder listenerBinder = listener.asBinder();
            if (mScanTypeScanListenerRecordMap.containsKey(listener.asBinder())) {
                ScanRequest savedScanRequest =
                        mScanTypeScanListenerRecordMap.get(listenerBinder).getScanRequest();
                if (scanRequest.equals(savedScanRequest)) {
                    Log.d(TAG, "Already registered the scanRequest: " + scanRequest);
                    return true;
                }
            }
            ScanListenerRecord scanListenerRecord =
                    new ScanListenerRecord(scanRequest, listener, callerIdentity);
            mScanTypeScanListenerRecordMap.put(listenerBinder, scanListenerRecord);

            if (!startProviders(scanRequest)) {
                return false;
            }

            NearbyMetrics.logScanStarted(scanListenerRecord.hashCode(), scanRequest);
            if (mScanMode < scanRequest.getScanMode()) {
                mScanMode = scanRequest.getScanMode();
                invalidateProviderScanMode();
            }
            return true;
        }
    }

    /**
     * Unregisters the listener in the manager and adjusts the scan mode if necessary afterwards.
     */
    public void unregisterScanListener(IScanListener listener) {
        IBinder listenerBinder = listener.asBinder();
        synchronized (mLock) {
            if (!mScanTypeScanListenerRecordMap.containsKey(listenerBinder)) {
                Log.w(
                        TAG,
                        "Cannot unregister the scanRequest because the request is never "
                                + "registered.");
                return;
            }

            ScanListenerRecord removedRecord =
                    mScanTypeScanListenerRecordMap.remove(listenerBinder);
            Log.v(TAG, "DiscoveryProviderManager unregistered scan listener.");
            NearbyMetrics.logScanStopped(removedRecord.hashCode(), removedRecord.getScanRequest());
            if (mScanTypeScanListenerRecordMap.isEmpty()) {
                Log.v(TAG, "DiscoveryProviderManager stops provider because there is no "
                        + "scan listener registered.");
                stopProviders();
                return;
            }

            // TODO(b/221082271): updates the scan with reduced filters.

            // Removes current highest scan mode requested and sets the next highest scan mode.
            if (removedRecord.getScanRequest().getScanMode() == mScanMode) {
                Log.v(TAG, "DiscoveryProviderManager starts to find the new highest scan mode "
                        + "because the highest scan mode listener was unregistered.");
                @ScanRequest.ScanMode int highestScanModeRequested = ScanRequest.SCAN_MODE_NO_POWER;
                // find the next highest scan mode;
                for (ScanListenerRecord record : mScanTypeScanListenerRecordMap.values()) {
                    @ScanRequest.ScanMode int scanMode = record.getScanRequest().getScanMode();
                    if (scanMode > highestScanModeRequested) {
                        highestScanModeRequested = scanMode;
                    }
                }
                if (mScanMode != highestScanModeRequested) {
                    mScanMode = highestScanModeRequested;
                    invalidateProviderScanMode();
                }
            }
        }
    }

    // Returns false when fail to start all the providers. Returns true if any one of the provider
    // starts successfully.
    private boolean startProviders(ScanRequest scanRequest) {
        if (scanRequest.isBleEnabled()) {
            if (mChreDiscoveryProvider.available()
                    && scanRequest.getScanType() == SCAN_TYPE_NEARBY_PRESENCE) {
                startChreProvider();
            } else {
                startBleProvider(scanRequest);
            }
            return true;
        }
        return false;
    }

    private void startBleProvider(ScanRequest scanRequest) {
        if (!mBleDiscoveryProvider.getController().isStarted()) {
            Log.d(TAG, "DiscoveryProviderManager starts Ble scanning.");
            mBleDiscoveryProvider.getController().start();
            mBleDiscoveryProvider.getController().setListener(this);
            mBleDiscoveryProvider.getController().setProviderScanMode(scanRequest.getScanMode());
        }
    }

    private void startChreProvider() {
        Log.d(TAG, "DiscoveryProviderManager starts CHRE scanning.");
        synchronized (mLock) {
            mChreDiscoveryProvider.getController().setListener(this);
            List<ScanFilter> scanFilters = new ArrayList();
            for (IBinder listenerBinder : mScanTypeScanListenerRecordMap.keySet()) {
                ScanListenerRecord record = mScanTypeScanListenerRecordMap.get(listenerBinder);
                List<ScanFilter> presenceFilters =
                        record.getScanRequest().getScanFilters().stream()
                                .filter(
                                        scanFilter ->
                                                scanFilter.getType() == SCAN_TYPE_NEARBY_PRESENCE)
                                .collect(Collectors.toList());
                scanFilters.addAll(presenceFilters);
            }
            mChreDiscoveryProvider.getController().setProviderScanFilters(scanFilters);
            mChreDiscoveryProvider.getController().setProviderScanMode(mScanMode);
            mChreDiscoveryProvider.getController().start();
        }
    }

    private void stopProviders() {
        stopBleProvider();
        stopChreProvider();
    }

    private void stopBleProvider() {
        mBleDiscoveryProvider.getController().stop();
    }

    private void stopChreProvider() {
        mChreDiscoveryProvider.getController().stop();
    }

    private void invalidateProviderScanMode() {
        if (mBleDiscoveryProvider.getController().isStarted()) {
            mBleDiscoveryProvider.getController().setProviderScanMode(mScanMode);
        } else {
            Log.d(
                    TAG,
                    "Skip invalidating BleDiscoveryProvider scan mode because the provider not "
                            + "started.");
        }
    }

    private static boolean presenceFilterMatches(
            NearbyDeviceParcelable device, List<ScanFilter> scanFilters) {
        if (scanFilters.isEmpty()) {
            return true;
        }
        PresenceDiscoveryResult discoveryResult = PresenceDiscoveryResult.fromDevice(device);
        for (ScanFilter scanFilter : scanFilters) {
            PresenceScanFilter presenceScanFilter = (PresenceScanFilter) scanFilter;
            if (discoveryResult.matches(presenceScanFilter)) {
                return true;
            }
        }
        return false;
    }

    private static class ScanListenerRecord {

        private final ScanRequest mScanRequest;

        private final IScanListener mScanListener;

        private final CallerIdentity mCallerIdentity;

        ScanListenerRecord(ScanRequest scanRequest, IScanListener iScanListener,
                CallerIdentity callerIdentity) {
            mScanListener = iScanListener;
            mScanRequest = scanRequest;
            mCallerIdentity = callerIdentity;
        }

        IScanListener getScanListener() {
            return mScanListener;
        }

        ScanRequest getScanRequest() {
            return mScanRequest;
        }

        CallerIdentity getCallerIdentity() {
            return mCallerIdentity;
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof ScanListenerRecord) {
                ScanListenerRecord otherScanListenerRecord = (ScanListenerRecord) other;
                return Objects.equals(mScanRequest, otherScanListenerRecord.mScanRequest)
                        && Objects.equals(mScanListener, otherScanListenerRecord.mScanListener);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mScanListener, mScanRequest);
        }
    }
}
