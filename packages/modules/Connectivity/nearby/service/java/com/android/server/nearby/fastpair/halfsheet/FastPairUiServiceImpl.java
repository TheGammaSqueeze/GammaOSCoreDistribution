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

package com.android.server.nearby.fastpair.halfsheet;

import static com.android.server.nearby.fastpair.Constant.TAG;

import android.nearby.FastPairDevice;
import android.nearby.FastPairStatusCallback;
import android.nearby.PairStatusMetadata;
import android.nearby.aidl.IFastPairStatusCallback;
import android.nearby.aidl.IFastPairUiService;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.android.server.nearby.fastpair.FastPairController;

/**
 * Service implementing Fast Pair functionality.
 *
 * @hide
 */
public class FastPairUiServiceImpl extends IFastPairUiService.Stub {

    private IBinder mStatusCallbackProxy;
    private FastPairController mFastPairController;
    private FastPairStatusCallback mFastPairStatusCallback;

    /**
     * Registers the Binder call back in the server notifies the proxy when there is an update
     * in the server.
     */
    @Override
    public void registerCallback(IFastPairStatusCallback iFastPairStatusCallback) {
        mStatusCallbackProxy = iFastPairStatusCallback.asBinder();
        mFastPairStatusCallback = new FastPairStatusCallback() {
            @Override
            public void onPairUpdate(FastPairDevice fastPairDevice,
                    PairStatusMetadata pairStatusMetadata) {
                try {
                    iFastPairStatusCallback.onPairUpdate(fastPairDevice, pairStatusMetadata);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to update pair status.", e);
                }
            }
        };
    }

    /**
     * Unregisters the Binder call back in the server.
     */
    @Override
    public void unregisterCallback(IFastPairStatusCallback iFastPairStatusCallback) {
        mStatusCallbackProxy = null;
        mFastPairStatusCallback = null;
    }

    /**
     * Asks the Fast Pair service to pair the device. initial pairing.
     */
    @Override
    public void connect(FastPairDevice fastPairDevice) {
        if (mFastPairController != null) {
            mFastPairController.pair(fastPairDevice);
        } else {
            Log.w(TAG, "Failed to connect because there is no FastPairController.");
        }
    }

    /**
     * Cancels Fast Pair connection and dismisses half sheet.
     */
    @Override
    public void cancel(FastPairDevice fastPairDevice) {
    }

    public FastPairStatusCallback getPairStatusCallback() {
        return mFastPairStatusCallback;
    }

    /**
     * Sets function for Fast Pair controller.
     */
    public void setFastPairController(FastPairController fastPairController) {
        mFastPairController = fastPairController;
    }
}
