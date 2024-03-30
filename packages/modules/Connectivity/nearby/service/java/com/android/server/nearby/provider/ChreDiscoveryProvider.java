/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.content.Context;
import android.hardware.location.NanoAppMessage;
import android.nearby.NearbyDevice;
import android.nearby.NearbyDeviceParcelable;
import android.nearby.PresenceScanFilter;
import android.nearby.PublicCredential;
import android.nearby.ScanFilter;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Collections;
import java.util.concurrent.Executor;

import service.proto.Blefilter;

/** Discovery provider that uses CHRE Nearby Nanoapp to do scanning. */
public class ChreDiscoveryProvider extends AbstractDiscoveryProvider {
    // Nanoapp ID reserved for Nearby Presence.
    /** @hide */
    @VisibleForTesting public static final long NANOAPP_ID = 0x476f6f676c001031L;
    /** @hide */
    @VisibleForTesting public static final int NANOAPP_MESSAGE_TYPE_FILTER = 3;
    /** @hide */
    @VisibleForTesting public static final int NANOAPP_MESSAGE_TYPE_FILTER_RESULT = 4;

    private static final int PRESENCE_UUID = 0xFCF1;

    private ChreCommunication mChreCommunication;
    private ChreCallback mChreCallback;
    private boolean mChreStarted = false;
    private Blefilter.BleFilters mFilters = null;
    private int mFilterId;

    public ChreDiscoveryProvider(
            Context context, ChreCommunication chreCommunication, Executor executor) {
        super(context, executor);
        mChreCommunication = chreCommunication;
        mChreCallback = new ChreCallback();
        mFilterId = 0;
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "Start CHRE scan");
        mChreCommunication.start(mChreCallback, Collections.singleton(NANOAPP_ID));
        updateFilters();
    }

    @Override
    protected void onStop() {
        mChreStarted = false;
        mChreCommunication.stop();
    }

    @Override
    protected void invalidateScanMode() {
        onStop();
        onStart();
    }

    public boolean available() {
        return mChreCommunication.available();
    }

    private synchronized void updateFilters() {
        if (mScanFilters == null) {
            Log.e(TAG, "ScanFilters not set.");
            return;
        }
        Blefilter.BleFilters.Builder filtersBuilder = Blefilter.BleFilters.newBuilder();
        for (ScanFilter scanFilter : mScanFilters) {
            PresenceScanFilter presenceScanFilter = (PresenceScanFilter) scanFilter;
            Blefilter.BleFilter filter =
                    Blefilter.BleFilter.newBuilder()
                            .setId(mFilterId)
                            .setUuid(PRESENCE_UUID)
                            .setIntent(presenceScanFilter.getPresenceActions().get(0))
                            .build();
            filtersBuilder.addFilter(filter);
            mFilterId++;
        }
        mFilters = filtersBuilder.build();
        if (mChreStarted) {
            sendFilters(mFilters);
            mFilters = null;
        }
    }

    private void sendFilters(Blefilter.BleFilters filters) {
        NanoAppMessage message =
                NanoAppMessage.createMessageToNanoApp(
                        NANOAPP_ID, NANOAPP_MESSAGE_TYPE_FILTER, filters.toByteArray());
        if (!mChreCommunication.sendMessageToNanoApp(message)) {
            Log.e(TAG, "Failed to send filters to CHRE.");
        }
    }

    private class ChreCallback implements ChreCommunication.ContextHubCommsCallback {

        @Override
        public void started(boolean success) {
            if (success) {
                synchronized (ChreDiscoveryProvider.this) {
                    Log.i(TAG, "CHRE communication started");
                    mChreStarted = true;
                    if (mFilters != null) {
                        sendFilters(mFilters);
                        mFilters = null;
                    }
                }
            }
        }

        @Override
        public void onHubReset() {
            // TODO(b/221082271): hooked with upper level codes.
            Log.i(TAG, "CHRE reset.");
        }

        @Override
        public void onNanoAppRestart(long nanoAppId) {
            // TODO(b/221082271): hooked with upper level codes.
            Log.i(TAG, String.format("CHRE NanoApp %d restart.", nanoAppId));
        }

        @Override
        public void onMessageFromNanoApp(NanoAppMessage message) {
            if (message.getNanoAppId() != NANOAPP_ID) {
                Log.e(TAG, "Received message from unknown nano app.");
                return;
            }
            if (mListener == null) {
                Log.e(TAG, "the listener is not set in ChreDiscoveryProvider.");
                return;
            }
            if (message.getMessageType() == NANOAPP_MESSAGE_TYPE_FILTER_RESULT) {
                try {
                    Blefilter.BleFilterResults results =
                            Blefilter.BleFilterResults.parseFrom(message.getMessageBody());
                    for (Blefilter.BleFilterResult filterResult : results.getResultList()) {
                        Blefilter.PublicCredential credential = filterResult.getPublicCredential();
                        PublicCredential publicCredential =
                                new PublicCredential.Builder(
                                                credential.getSecretId().toByteArray(),
                                                credential.getAuthenticityKey().toByteArray(),
                                                credential.getPublicKey().toByteArray(),
                                                credential.getEncryptedMetadata().toByteArray(),
                                                credential.getEncryptedMetadataTag().toByteArray())
                                        .build();
                        NearbyDeviceParcelable device =
                                new NearbyDeviceParcelable.Builder()
                                        .setScanType(SCAN_TYPE_NEARBY_PRESENCE)
                                        .setMedium(NearbyDevice.Medium.BLE)
                                        .setTxPower(filterResult.getTxPower())
                                        .setRssi(filterResult.getRssi())
                                        .setAction(filterResult.getIntent())
                                        .setPublicCredential(publicCredential)
                                        .build();
                        mExecutor.execute(() -> mListener.onNearbyDeviceDiscovered(device));
                    }
                } catch (InvalidProtocolBufferException e) {
                    Log.e(
                            TAG,
                            String.format("Failed to decode the filter result %s", e.toString()));
                }
            }
        }
    }
}
