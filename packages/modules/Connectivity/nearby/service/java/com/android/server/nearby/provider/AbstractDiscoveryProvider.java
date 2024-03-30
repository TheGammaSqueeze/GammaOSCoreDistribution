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

import static com.android.server.nearby.NearbyService.TAG;

import android.content.Context;
import android.nearby.NearbyDeviceParcelable;
import android.nearby.ScanFilter;
import android.nearby.ScanRequest;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Base class for all discovery providers.
 *
 * @hide
 */
public abstract class AbstractDiscoveryProvider {

    protected final Context mContext;
    protected final DiscoveryProviderController mController;
    protected final Executor mExecutor;
    protected Listener mListener;
    protected List<ScanFilter> mScanFilters;

    /** Interface for listening to discovery providers. */
    public interface Listener {
        /**
         * Called when a provider has a new nearby device available. May be invoked from any thread.
         */
        void onNearbyDeviceDiscovered(NearbyDeviceParcelable nearbyDevice);
    }

    protected AbstractDiscoveryProvider(Context context, Executor executor) {
        mContext = context;
        mExecutor = executor;
        mController = new Controller();
    }

    /**
     * Callback invoked when the provider is started, and signals that other callback invocations
     * can now be expected. Always implies that the provider request is set to the empty request.
     * Always invoked on the provider executor.
     */
    protected void onStart() {}

    /**
     * Callback invoked when the provider is stopped, and signals that no further callback
     * invocations will occur (until a further call to {@link #onStart()}. Always invoked on the
     * provider executor.
     */
    protected void onStop() {}

    /**
     * Callback invoked to inform the provider of a new provider request which replaces any prior
     * provider request. Always invoked on the provider executor.
     */
    protected void invalidateScanMode() {}

    /**
     * Retrieves the controller for this discovery provider. Should never be invoked by subclasses,
     * as a discovery provider should not be controlling itself. Using this method from subclasses
     * could also result in deadlock.
     */
    protected DiscoveryProviderController getController() {
        return mController;
    }

    private class Controller implements DiscoveryProviderController {

        private boolean mStarted = false;
        private @ScanRequest.ScanMode int mScanMode;

        @Override
        public void setListener(@Nullable Listener listener) {
            mListener = listener;
        }

        @Override
        public boolean isStarted() {
            return mStarted;
        }

        @Override
        public void start() {
            if (mStarted) {
                Log.d(TAG, "Provider already started.");
                return;
            }
            mStarted = true;
            mExecutor.execute(AbstractDiscoveryProvider.this::onStart);
        }

        @Override
        public void stop() {
            if (!mStarted) {
                Log.d(TAG, "Provider already stopped.");
                return;
            }
            mStarted = false;
            mExecutor.execute(AbstractDiscoveryProvider.this::onStop);
        }

        @Override
        public void setProviderScanMode(@ScanRequest.ScanMode int scanMode) {
            if (mScanMode == scanMode) {
                Log.d(TAG, "Provider already in desired scan mode.");
                return;
            }
            mScanMode = scanMode;
            mExecutor.execute(AbstractDiscoveryProvider.this::invalidateScanMode);
        }

        @ScanRequest.ScanMode
        @Override
        public int getProviderScanMode() {
            return mScanMode;
        }

        @Override
        public void setProviderScanFilters(List<ScanFilter> filters) {
            mScanFilters = filters;
        }
    }
}
