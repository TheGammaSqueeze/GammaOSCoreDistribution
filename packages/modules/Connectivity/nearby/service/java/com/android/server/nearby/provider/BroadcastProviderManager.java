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

import android.content.Context;
import android.nearby.BroadcastCallback;
import android.nearby.BroadcastRequest;
import android.nearby.IBroadcastListener;
import android.nearby.PresenceBroadcastRequest;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.nearby.NearbyConfiguration;
import com.android.server.nearby.injector.Injector;
import com.android.server.nearby.presence.FastAdvertisement;
import com.android.server.nearby.util.ForegroundThread;

import java.util.concurrent.Executor;

/**
 * A manager for nearby broadcasts.
 */
public class BroadcastProviderManager implements BleBroadcastProvider.BroadcastListener {

    private static final String TAG = "BroadcastProvider";

    private final Object mLock;
    private final BleBroadcastProvider mBleBroadcastProvider;
    private final Executor mExecutor;
    private final NearbyConfiguration mNearbyConfiguration;

    private IBroadcastListener mBroadcastListener;

    public BroadcastProviderManager(Context context, Injector injector) {
        this(ForegroundThread.getExecutor(),
                new BleBroadcastProvider(injector, ForegroundThread.getExecutor()));
    }

    @VisibleForTesting
    BroadcastProviderManager(Executor executor, BleBroadcastProvider bleBroadcastProvider) {
        mExecutor = executor;
        mBleBroadcastProvider = bleBroadcastProvider;
        mLock = new Object();
        mNearbyConfiguration = new NearbyConfiguration();
        mBroadcastListener = null;
    }

    /**
     * Starts a nearby broadcast, the callback is sent through the given listener.
     */
    public void startBroadcast(BroadcastRequest broadcastRequest, IBroadcastListener listener) {
        synchronized (mLock) {
            mExecutor.execute(() -> {
                NearbyConfiguration configuration = new NearbyConfiguration();
                if (!configuration.isPresenceBroadcastLegacyEnabled()) {
                    reportBroadcastStatus(listener, BroadcastCallback.STATUS_FAILURE);
                    return;
                }
                if (broadcastRequest.getType() != BroadcastRequest.BROADCAST_TYPE_NEARBY_PRESENCE) {
                    reportBroadcastStatus(listener, BroadcastCallback.STATUS_FAILURE);
                    return;
                }
                PresenceBroadcastRequest presenceBroadcastRequest =
                        (PresenceBroadcastRequest) broadcastRequest;
                if (presenceBroadcastRequest.getVersion() != BroadcastRequest.PRESENCE_VERSION_V0) {
                    reportBroadcastStatus(listener, BroadcastCallback.STATUS_FAILURE);
                    return;
                }
                FastAdvertisement fastAdvertisement = FastAdvertisement.createFromRequest(
                        presenceBroadcastRequest);
                byte[] advertisementPackets = fastAdvertisement.toBytes();
                mBroadcastListener = listener;
                mBleBroadcastProvider.start(advertisementPackets, this);
            });
        }
    }

    /**
     * Stops the nearby broadcast.
     */
    public void stopBroadcast(IBroadcastListener listener) {
        synchronized (mLock) {
            if (!mNearbyConfiguration.isPresenceBroadcastLegacyEnabled()) {
                reportBroadcastStatus(listener, BroadcastCallback.STATUS_FAILURE);
                return;
            }
            mBroadcastListener = null;
            mExecutor.execute(() -> mBleBroadcastProvider.stop());
        }
    }

    @Override
    public void onStatusChanged(int status) {
        IBroadcastListener listener = null;
        synchronized (mLock) {
            listener = mBroadcastListener;
        }
        // Don't invoke callback while holding the local lock, as this could cause deadlock.
        if (listener != null) {
            reportBroadcastStatus(listener, status);
        }
    }

    private void reportBroadcastStatus(IBroadcastListener listener, int status) {
        try {
            listener.onStatusChanged(status);
        } catch (RemoteException exception) {
            Log.e(TAG, "remote exception when reporting status");
        }
    }
}
