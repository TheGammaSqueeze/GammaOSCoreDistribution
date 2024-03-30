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

package com.android.server.nearby.common.servicemonitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.android.modules.utils.BackgroundThread;

import java.util.Objects;

/**
 * This is mostly from frameworks PackageMonitor.
 * Helper class for watching somePackagesChanged.
 */
public abstract class PackageWatcher extends BroadcastReceiver {
    static final String TAG = "PackageWatcher";
    static final IntentFilter sPackageFilt = new IntentFilter();
    static final IntentFilter sNonDataFilt = new IntentFilter();
    static final IntentFilter sExternalFilt = new IntentFilter();

    static {
        sPackageFilt.addAction(Intent.ACTION_PACKAGE_ADDED);
        sPackageFilt.addAction(Intent.ACTION_PACKAGE_REMOVED);
        sPackageFilt.addAction(Intent.ACTION_PACKAGE_CHANGED);
        sPackageFilt.addDataScheme("package");
        sNonDataFilt.addAction(Intent.ACTION_PACKAGES_SUSPENDED);
        sNonDataFilt.addAction(Intent.ACTION_PACKAGES_UNSUSPENDED);
        sExternalFilt.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        sExternalFilt.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
    }

    Context mRegisteredContext;
    Handler mRegisteredHandler;
    boolean mSomePackagesChanged;

    public PackageWatcher() {
    }

    void register(Context context, Looper thread, boolean externalStorage) {
        register(context, externalStorage,
                (thread == null) ? BackgroundThread.getHandler() : new Handler(thread));
    }

    void register(Context context, boolean externalStorage, Handler handler) {
        if (mRegisteredContext != null) {
            throw new IllegalStateException("Already registered");
        }
        mRegisteredContext = context;
        mRegisteredHandler = Objects.requireNonNull(handler);
        context.registerReceiverForAllUsers(this, sPackageFilt, null, mRegisteredHandler);
        context.registerReceiverForAllUsers(this, sNonDataFilt, null, mRegisteredHandler);
        if (externalStorage) {
            context.registerReceiverForAllUsers(this, sExternalFilt, null, mRegisteredHandler);
        }
    }

    void unregister() {
        if (mRegisteredContext == null) {
            throw new IllegalStateException("Not registered");
        }
        mRegisteredContext.unregisterReceiver(this);
        mRegisteredContext = null;
    }

    // Called when some package has been changed.
    abstract void onSomePackagesChanged();

    String getPackageName(Intent intent) {
        Uri uri = intent.getData();
        String pkg = uri != null ? uri.getSchemeSpecificPart() : null;
        return pkg;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        mSomePackagesChanged = false;

        String action = intent.getAction();
        if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
            // We consider something to have changed regardless of whether
            // this is just an update, because the update is now finished
            // and the contents of the package may have changed.
            mSomePackagesChanged = true;
        } else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
            String pkg = getPackageName(intent);
            if (pkg != null) {
                if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    mSomePackagesChanged = true;
                }
            }
        } else if (Intent.ACTION_PACKAGE_CHANGED.equals(action)) {
            String pkg = getPackageName(intent);
            if (pkg != null) {
                mSomePackagesChanged = true;
            }
        } else if (Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE.equals(action)) {
            mSomePackagesChanged = true;
        } else if (Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE.equals(action)) {
            mSomePackagesChanged = true;
        } else if (Intent.ACTION_PACKAGES_SUSPENDED.equals(action)) {
            mSomePackagesChanged = true;
        } else if (Intent.ACTION_PACKAGES_UNSUSPENDED.equals(action)) {
            mSomePackagesChanged = true;
        }

        if (mSomePackagesChanged) {
            onSomePackagesChanged();
        }
    }
}
