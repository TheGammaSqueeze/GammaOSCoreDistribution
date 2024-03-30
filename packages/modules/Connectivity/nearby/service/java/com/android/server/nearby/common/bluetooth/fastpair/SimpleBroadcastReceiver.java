/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.server.nearby.common.bluetooth.fastpair;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.common.util.concurrent.SettableFuture;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Like {@link BroadcastReceiver}, but:
 *
 * <ul>
 *   <li>Simpler to create and register, with a list of actions.
 *   <li>Implements AutoCloseable. If used as a resource in try-with-resources (available on
 *       KitKat+), unregisters itself automatically.
 *   <li>Lets you block waiting for your state transition with {@link #await}.
 * </ul>
 */
// AutoCloseable only available on KitKat+.
@TargetApi(VERSION_CODES.KITKAT)
public abstract class SimpleBroadcastReceiver extends BroadcastReceiver implements AutoCloseable {

    private static final String TAG = SimpleBroadcastReceiver.class.getSimpleName();

    /**
     * Creates a one shot receiver.
     */
    public static SimpleBroadcastReceiver oneShotReceiver(
            Context context, Preferences preferences, String... actions) {
        return new SimpleBroadcastReceiver(context, preferences, actions) {
            @Override
            protected void onReceive(Intent intent) {
                close();
            }
        };
    }

    private final Context mContext;
    private final SettableFuture<Void> mIsClosedFuture = SettableFuture.create();
    private long mAwaitExtendSecond;

    // Nullness checker complains about 'this' being @UnderInitialization
    @SuppressWarnings("nullness")
    public SimpleBroadcastReceiver(
            Context context, Preferences preferences, @Nullable Handler handler,
            String... actions) {
        Log.v(TAG, this + " listening for actions " + Arrays.toString(actions));
        this.mContext = context;
        IntentFilter intentFilter = new IntentFilter();
        if (preferences.getIncreaseIntentFilterPriority()) {
            intentFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        }
        for (String action : actions) {
            intentFilter.addAction(action);
        }
        context.registerReceiver(this, intentFilter, /* broadcastPermission= */ null, handler);
    }

    public SimpleBroadcastReceiver(Context context, Preferences preferences, String... actions) {
        this(context, preferences, /* handler= */ null, actions);
    }

    /**
     * Any exception thrown by this method will be delivered via {@link #await}.
     */
    protected abstract void onReceive(Intent intent) throws Exception;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.v(TAG, "Got intent with action= " + intent.getAction());
        try {
            onReceive(intent);
        } catch (Exception e) {
            closeWithError(e);
        }
    }

    @Override
    public void close() {
        closeWithError(null);
    }

    void closeWithError(@Nullable Exception e) {
        try {
            mContext.unregisterReceiver(this);
        } catch (IllegalArgumentException ignored) {
            // Ignore. Happens if you unregister twice.
        }
        if (e == null) {
            mIsClosedFuture.set(null);
        } else {
            mIsClosedFuture.setException(e);
        }
    }

    /**
     * Extends the awaiting time.
     */
    public void extendAwaitSecond(int awaitExtendSecond) {
        this.mAwaitExtendSecond = awaitExtendSecond;
    }

    /**
     * Blocks until this receiver has closed (i.e. the state transition that this receiver is
     * interested in has completed). Throws an exception on any error.
     */
    public void await(long timeout, TimeUnit timeUnit)
            throws InterruptedException, ExecutionException, TimeoutException {
        Log.v(TAG, this + " waiting on future for " + timeout + " " + timeUnit);
        try {
            mIsClosedFuture.get(timeout, timeUnit);
        } catch (TimeoutException e) {
            if (mAwaitExtendSecond <= 0) {
                throw e;
            }
            Log.i(TAG, "Extend timeout for " + mAwaitExtendSecond + " seconds");
            mIsClosedFuture.get(mAwaitExtendSecond, TimeUnit.SECONDS);
        }
    }
}
