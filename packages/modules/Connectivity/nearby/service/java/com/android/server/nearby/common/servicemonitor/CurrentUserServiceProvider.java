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

import static android.content.pm.PackageManager.GET_META_DATA;
import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_AUTO;
import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_AWARE;
import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
import static android.content.pm.PackageManager.MATCH_SYSTEM_ONLY;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;

import com.android.internal.util.Preconditions;
import com.android.server.nearby.common.servicemonitor.ServiceMonitor.ServiceChangedListener;
import com.android.server.nearby.common.servicemonitor.ServiceMonitor.ServiceProvider;

import java.util.Comparator;
import java.util.List;

/**
 * This is mostly borrowed from frameworks CurrentUserServiceSupplier.
 * Provides services based on the current active user and version as defined in the service
 * manifest. This implementation uses {@link android.content.pm.PackageManager#MATCH_SYSTEM_ONLY} to
 * ensure only system (ie, privileged) services are matched. It also handles services that are not
 * direct boot aware, and will automatically pick the best service as the user's direct boot state
 * changes.
 */
public final class CurrentUserServiceProvider extends BroadcastReceiver implements
        ServiceProvider<CurrentUserServiceProvider.BoundServiceInfo> {

    private static final String TAG = "CurrentUserServiceProvider";

    private static final String EXTRA_SERVICE_VERSION = "serviceVersion";

    // This is equal to the hidden Intent.ACTION_USER_SWITCHED.
    private static final String ACTION_USER_SWITCHED = "android.intent.action.USER_SWITCHED";
    // This is equal to the hidden Intent.EXTRA_USER_HANDLE.
    private static final String EXTRA_USER_HANDLE = "android.intent.extra.user_handle";
    // This is equal to the hidden UserHandle.USER_NULL.
    private static final int USER_NULL = -10000;

    private static final Comparator<BoundServiceInfo> sBoundServiceInfoComparator = (o1, o2) -> {
        if (o1 == o2) {
            return 0;
        } else if (o1 == null) {
            return -1;
        } else if (o2 == null) {
            return 1;
        }

        // ServiceInfos with higher version numbers always win.
        return Integer.compare(o1.getVersion(), o2.getVersion());
    };

    /** Bound service information with version information. */
    public static class BoundServiceInfo extends ServiceMonitor.BoundServiceInfo {

        private static int parseUid(ResolveInfo resolveInfo) {
            return resolveInfo.serviceInfo.applicationInfo.uid;
        }

        private static int parseVersion(ResolveInfo resolveInfo) {
            int version = Integer.MIN_VALUE;
            if (resolveInfo.serviceInfo.metaData != null) {
                version = resolveInfo.serviceInfo.metaData.getInt(EXTRA_SERVICE_VERSION, version);
            }
            return version;
        }

        private final int mVersion;

        protected BoundServiceInfo(String action, ResolveInfo resolveInfo) {
            this(
                    action,
                    parseUid(resolveInfo),
                    new ComponentName(
                            resolveInfo.serviceInfo.packageName,
                            resolveInfo.serviceInfo.name),
                    parseVersion(resolveInfo));
        }

        protected BoundServiceInfo(String action, int uid, ComponentName componentName,
                int version) {
            super(action, uid, componentName);
            mVersion = version;
        }

        public int getVersion() {
            return mVersion;
        }

        @Override
        public String toString() {
            return super.toString() + "@" + mVersion;
        }
    }

    /**
     * Creates an instance with the specific service details.
     *
     * @param context the context the provider is to use
     * @param action the action the service must declare in its intent-filter
     */
    public static CurrentUserServiceProvider create(Context context, String action) {
        return new CurrentUserServiceProvider(context, action);
    }

    private final Context mContext;
    private final Intent mIntent;
    private volatile ServiceChangedListener mListener;

    private CurrentUserServiceProvider(Context context, String action) {
        mContext = context;
        mIntent = new Intent(action);
    }

    @Override
    public boolean hasMatchingService() {
        int intentQueryFlags =
                MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE | MATCH_SYSTEM_ONLY;
        List<ResolveInfo> resolveInfos = mContext.getPackageManager().queryIntentServicesAsUser(
                mIntent, intentQueryFlags, UserHandle.SYSTEM);
        return !resolveInfos.isEmpty();
    }

    @Override
    public void register(ServiceChangedListener listener) {
        Preconditions.checkState(mListener == null);

        mListener = listener;

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_USER_SWITCHED);
        intentFilter.addAction(Intent.ACTION_USER_UNLOCKED);
        mContext.registerReceiverForAllUsers(this, intentFilter, null,
                ForegroundThread.getHandler());
    }

    @Override
    public void unregister() {
        Preconditions.checkArgument(mListener != null);

        mListener = null;
        mContext.unregisterReceiver(this);
    }

    @Override
    public BoundServiceInfo getServiceInfo() {
        BoundServiceInfo bestServiceInfo = null;

        // only allow services in the correct direct boot state to match
        int intentQueryFlags = MATCH_DIRECT_BOOT_AUTO | GET_META_DATA | MATCH_SYSTEM_ONLY;
        List<ResolveInfo> resolveInfos = mContext.getPackageManager().queryIntentServicesAsUser(
                mIntent, intentQueryFlags, UserHandle.of(ActivityManager.getCurrentUser()));
        for (ResolveInfo resolveInfo : resolveInfos) {
            BoundServiceInfo serviceInfo =
                    new BoundServiceInfo(mIntent.getAction(), resolveInfo);

            if (sBoundServiceInfoComparator.compare(serviceInfo, bestServiceInfo) > 0) {
                bestServiceInfo = serviceInfo;
            }
        }

        return bestServiceInfo;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) {
            return;
        }
        int userId = intent.getIntExtra(EXTRA_USER_HANDLE, USER_NULL);
        if (userId == USER_NULL) {
            return;
        }
        ServiceChangedListener listener = mListener;
        if (listener == null) {
            return;
        }

        switch (action) {
            case ACTION_USER_SWITCHED:
                listener.onServiceChanged();
                break;
            case Intent.ACTION_USER_UNLOCKED:
                // user unlocked implies direct boot mode may have changed
                if (userId == ActivityManager.getCurrentUser()) {
                    listener.onServiceChanged();
                }
                break;
            default:
                break;
        }
    }
}
