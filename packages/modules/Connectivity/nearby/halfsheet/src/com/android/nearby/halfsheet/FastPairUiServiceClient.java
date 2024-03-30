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

package com.android.nearby.halfsheet;

import android.content.Context;
import android.nearby.FastPairDevice;
import android.nearby.FastPairStatusCallback;
import android.nearby.PairStatusMetadata;
import android.nearby.aidl.IFastPairStatusCallback;
import android.nearby.aidl.IFastPairUiService;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.BinderThread;
import androidx.annotation.UiThread;

import java.lang.ref.WeakReference;

/**
 *  A utility class for connecting to the {@link IFastPairUiService} and receive callbacks.
 *
 * @hide
 */
@UiThread
public class FastPairUiServiceClient {

    private static final String TAG = "FastPairHalfSheet";

    private final IBinder mBinder;
    private final WeakReference<Context> mWeakContext;
    IFastPairUiService mFastPairUiService;
    PairStatusCallbackIBinder mPairStatusCallbackIBinder;

    /**
     * The Ibinder instance should be from
     * {@link com.android.server.nearby.fastpair.halfsheet.FastPairUiServiceImpl} so that the client can
     * talk with the service.
     */
    public FastPairUiServiceClient(Context context, IBinder binder) {
        mBinder = binder;
        mFastPairUiService = IFastPairUiService.Stub.asInterface(mBinder);
        mWeakContext = new WeakReference<>(context);
    }

    /**
     * Registers a callback at service to get UI updates.
     */
    public void registerHalfSheetStateCallBack(FastPairStatusCallback fastPairStatusCallback) {
        if (mPairStatusCallbackIBinder != null) {
            return;
        }
        mPairStatusCallbackIBinder = new PairStatusCallbackIBinder(fastPairStatusCallback);
        try {
            mFastPairUiService.registerCallback(mPairStatusCallbackIBinder);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to register fastPairStatusCallback", e);
        }
    }

    /**
     * Pairs the device at service.
     */
    public void connect(FastPairDevice fastPairDevice) {
        try {
            mFastPairUiService.connect(fastPairDevice);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to connect Fast Pair device" + fastPairDevice, e);
        }
    }

    /**
     * Cancels Fast Pair connection and dismisses half sheet.
     */
    public void cancel(FastPairDevice fastPairDevice) {
        try {
            mFastPairUiService.cancel(fastPairDevice);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to connect Fast Pair device" + fastPairDevice, e);
        }
    }

    private class PairStatusCallbackIBinder extends IFastPairStatusCallback.Stub {
        private final FastPairStatusCallback mStatusCallback;

        private PairStatusCallbackIBinder(FastPairStatusCallback fastPairStatusCallback) {
            mStatusCallback = fastPairStatusCallback;
        }

        @BinderThread
        @Override
        public synchronized void onPairUpdate(FastPairDevice fastPairDevice,
                PairStatusMetadata pairStatusMetadata) {
            Context context = mWeakContext.get();
            if (context != null) {
                Handler handler = new Handler(context.getMainLooper());
                handler.post(() ->
                        mStatusCallback.onPairUpdate(fastPairDevice, pairStatusMetadata));
            }
        }
    }
}
