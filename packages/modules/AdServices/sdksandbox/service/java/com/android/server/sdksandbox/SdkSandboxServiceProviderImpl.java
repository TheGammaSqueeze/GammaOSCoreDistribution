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

package com.android.server.sdksandbox;

import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.sdksandbox.ISdkSandboxService;
import com.android.server.LocalManagerRegistry;
import com.android.server.am.ActivityManagerLocal;

import java.io.PrintWriter;
import java.util.Objects;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Implementation of {@link SdkSandboxServiceProvider}.
 *
 * @hide
 */
@ThreadSafe
class SdkSandboxServiceProviderImpl implements SdkSandboxServiceProvider {

    private static final String TAG = "SdkSandboxManager";

    private final Object mLock = new Object();

    private final Context mContext;
    private final ActivityManagerLocal mActivityManagerLocal;

    @GuardedBy("mLock")
    private final SparseArray<SdkSandboxConnection> mAppSdkSandboxConnections =
            new SparseArray<>();

    SdkSandboxServiceProviderImpl(Context context) {
        mContext = context;
        mActivityManagerLocal = LocalManagerRegistry.getManager(ActivityManagerLocal.class);
    }

    // TODO(b/214240264): Write E2E tests for checking binding from different apps
    @Override
    @Nullable
    public void bindService(int appUid, String appPackageName,
            ServiceConnection serviceConnection) {
        synchronized (mLock) {
            if (getBoundServiceForApp(appUid) != null) {
                Log.i(TAG, "SDK sandbox for " + appUid + " is already bound");
                return;
            }

            Log.i(TAG, "Binding sdk sandbox for " + appUid);

            ComponentName componentName = getServiceComponentName();
            if (componentName == null) {
                Log.e(TAG, "Failed to find sdk sandbox service");
                notifyFailedBinding(serviceConnection);
                return;
            }
            final Intent intent = new Intent().setComponent(componentName);

            SdkSandboxConnection sdkSandboxConnection =
                    new SdkSandboxConnection(serviceConnection);

            final String processName = "sdk_sandbox_" + appUid;
            try {
                boolean bound = mActivityManagerLocal.bindSdkSandboxService(intent,
                        serviceConnection, appUid, appPackageName, processName,
                        Context.BIND_AUTO_CREATE);
                if (!bound) {
                    mContext.unbindService(serviceConnection);
                    notifyFailedBinding(serviceConnection);
                    return;
                }
            } catch (RemoteException e) {
                notifyFailedBinding(serviceConnection);
                return;
            }

            mAppSdkSandboxConnections.append(appUid, sdkSandboxConnection);
            Log.i(TAG, "Sdk sandbox has been bound");
        }
    }

    // a way to notify manager that binding never happened
    private void notifyFailedBinding(ServiceConnection serviceConnection) {
        serviceConnection.onNullBinding(null);
    }

    @Override
    public void dump(PrintWriter writer) {
        synchronized (mLock) {
            if (mAppSdkSandboxConnections.size() == 0) {
                writer.println("mAppSdkSandboxConnections is empty");
            } else {
                writer.print("mAppSdkSandboxConnections size: ");
                writer.println(mAppSdkSandboxConnections.size());
                for (int i = 0; i < mAppSdkSandboxConnections.size(); i++) {
                    writer.printf("Sdk sandbox for UID: %s, isConnected: %s",
                            mAppSdkSandboxConnections.keyAt(i),
                            mAppSdkSandboxConnections.valueAt(i).isConnected());
                    writer.println();
                }
            }
        }
    }

    @Override
    public void unbindService(int appUid) {
        synchronized (mLock) {
            SdkSandboxConnection sandbox = getSdkSandboxConnectionLocked(appUid);

            if (sandbox == null) {
                // Skip, already unbound
                return;
            }

            mContext.unbindService(sandbox.getServiceConnection());
            mAppSdkSandboxConnections.delete(appUid);
            Log.i(TAG, "Sdk sandbox has been unbound");
        }
    }

    @Override
    @Nullable
    public ISdkSandboxService getBoundServiceForApp(int appUid) {
        synchronized (mLock) {
            if (mAppSdkSandboxConnections.contains(appUid)) {
                return Objects.requireNonNull(mAppSdkSandboxConnections.get(appUid))
                        .getSdkSandboxService();
            }
        }
        return null;
    }

    @Override
    public void setBoundServiceForApp(int appUid, ISdkSandboxService service) {
        synchronized (mLock) {
            if (mAppSdkSandboxConnections.contains(appUid)) {
                Objects.requireNonNull(mAppSdkSandboxConnections.get(appUid))
                        .setSdkSandboxService(service);
            }
        }
    }

    @Nullable
    private ComponentName getServiceComponentName() {
        final Intent intent = new Intent(SdkSandboxManagerLocal.SERVICE_INTERFACE);
        intent.setPackage(mContext.getPackageManager().getSdkSandboxPackageName());

        final ResolveInfo resolveInfo = mContext.getPackageManager().resolveService(intent,
                PackageManager.GET_SERVICES | PackageManager.GET_META_DATA);
        if (resolveInfo == null) {
            Log.e(TAG, "Failed to find resolveInfo for sdk sandbox service");
            return null;
        }

        final ServiceInfo serviceInfo = resolveInfo.serviceInfo;
        if (serviceInfo == null) {
            Log.e(TAG, "Failed to find serviceInfo for sdk sandbox service");
            return null;
        }

        return new ComponentName(serviceInfo.packageName, serviceInfo.name);
    }

    @GuardedBy("mLock")
    @Nullable
    private SdkSandboxConnection getSdkSandboxConnectionLocked(int appUid) {
        return mAppSdkSandboxConnections.get(appUid);
    }

    private static class SdkSandboxConnection {
        private final ServiceConnection mServiceConnection;
        @Nullable
        private ISdkSandboxService mSupplementalProcessService = null;

        SdkSandboxConnection(ServiceConnection serviceConnection) {
            mServiceConnection = serviceConnection;
        }

        @Nullable
        public ISdkSandboxService getSdkSandboxService() {
            return mSupplementalProcessService;
        }

        public ServiceConnection getServiceConnection() {
            return mServiceConnection;
        }

        public void setSdkSandboxService(ISdkSandboxService service) {
            mSupplementalProcessService = service;
        }

        boolean isConnected() {
            return mSupplementalProcessService != null;
        }
    }
}
