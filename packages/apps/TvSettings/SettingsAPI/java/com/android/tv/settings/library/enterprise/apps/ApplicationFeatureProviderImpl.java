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

package com.android.tv.settings.library.enterprise.apps;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ComponentInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.os.RemoteException;
import android.os.UserManager;
import android.util.ArraySet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Forked from:
 * Settings/src/com/android/settings/applications/ApplicationFeatureProviderImpl.java
 */
public class ApplicationFeatureProviderImpl implements ApplicationFeatureProvider {
    private static final String TAG = "AppFeatureProviderImpl";

    protected final Context mContext;
    private final PackageManager mPm;
    private final IPackageManager mPms;
    private final DevicePolicyManager mDpm;
    private final UserManager mUm;

    public ApplicationFeatureProviderImpl(Context context, PackageManager pm,
            IPackageManager pms, DevicePolicyManager dpm) {
        mContext = context.getApplicationContext();
        mPm = pm;
        mPms = pms;
        mDpm = dpm;
        mUm = UserManager.get(mContext);
    }

    @Override
    public void calculateNumberOfPolicyInstalledApps(boolean async, NumberOfAppsCallback callback) {
        final CurrentUserAndManagedProfilePolicyInstalledAppCounter counter =
                new CurrentUserAndManagedProfilePolicyInstalledAppCounter(mContext, mPm, callback);
        if (async) {
            counter.execute();
        } else {
            counter.executeInForeground();
        }
    }

    @Override
    public void listPolicyInstalledApps(ListOfAppsCallback callback) {
        final CurrentUserPolicyInstalledAppLister lister =
                new CurrentUserPolicyInstalledAppLister(mPm, mUm, callback);
        lister.execute();
    }

    @Override
    public void calculateNumberOfAppsWithAdminGrantedPermissions(String[] permissions,
            boolean async, NumberOfAppsCallback callback) {
        final CurrentUserAndManagedProfileAppWithAdminGrantedPermissionsCounter counter =
                new CurrentUserAndManagedProfileAppWithAdminGrantedPermissionsCounter(mContext,
                        permissions, mPm, mPms, mDpm, callback);
        if (async) {
            counter.execute();
        } else {
            counter.executeInForeground();
        }
    }

    @Override
    public void listAppsWithAdminGrantedPermissions(String[] permissions,
            ListOfAppsCallback callback) {
        final CurrentUserAppWithAdminGrantedPermissionsLister lister =
                new CurrentUserAppWithAdminGrantedPermissionsLister(permissions, mPm, mPms, mDpm,
                        mUm, callback);
        lister.execute();
    }

    @Override
    public List<UserAppInfo> findPersistentPreferredActivities(int userId, Intent[] intents) {
        final List<UserAppInfo> preferredActivities = new ArrayList<>();
        final Set<UserAppInfo> uniqueApps = new ArraySet<>();
        final UserInfo userInfo = mUm.getUserInfo(userId);
        for (final Intent intent : intents) {
            try {
                final ResolveInfo resolveInfo =
                        mPms.findPersistentPreferredActivity(intent, userId);
                if (resolveInfo != null) {
                    ComponentInfo componentInfo = null;
                    if (resolveInfo.activityInfo != null) {
                        componentInfo = resolveInfo.activityInfo;
                    } else if (resolveInfo.serviceInfo != null) {
                        componentInfo = resolveInfo.serviceInfo;
                    } else if (resolveInfo.providerInfo != null) {
                        componentInfo = resolveInfo.providerInfo;
                    }
                    if (componentInfo != null) {
                        UserAppInfo info = new UserAppInfo(userInfo, componentInfo.applicationInfo);
                        if (uniqueApps.add(info)) {
                            preferredActivities.add(info);
                        }
                    }
                }
            } catch (RemoteException exception) {
            }
        }
        return preferredActivities;
    }


    private static class CurrentUserAndManagedProfilePolicyInstalledAppCounter
            extends InstalledAppCounter {
        private final NumberOfAppsCallback mCallback;

        CurrentUserAndManagedProfilePolicyInstalledAppCounter(Context context,
                PackageManager packageManager, NumberOfAppsCallback callback) {
            super(context, PackageManager.INSTALL_REASON_POLICY, packageManager);
            mCallback = callback;
        }

        @Override
        protected void onCountComplete(int num) {
            mCallback.onNumberOfAppsResult(num);
        }
    }

    private static class CurrentUserAndManagedProfileAppWithAdminGrantedPermissionsCounter
            extends AppWithAdminGrantedPermissionsCounter {
        private final NumberOfAppsCallback mCallback;

        CurrentUserAndManagedProfileAppWithAdminGrantedPermissionsCounter(Context context,
                String[] permissions, PackageManager packageManager,
                IPackageManager packageManagerService,
                DevicePolicyManager devicePolicyManager, NumberOfAppsCallback callback) {
            super(context, permissions, packageManager, packageManagerService, devicePolicyManager);
            mCallback = callback;
        }

        @Override
        protected void onCountComplete(int num) {
            mCallback.onNumberOfAppsResult(num);
        }
    }

    private static class CurrentUserPolicyInstalledAppLister extends InstalledAppLister {
        private final ListOfAppsCallback mCallback;

        CurrentUserPolicyInstalledAppLister(PackageManager packageManager,
                UserManager userManager, ListOfAppsCallback callback) {
            super(packageManager, userManager);
            mCallback = callback;
        }

        @Override
        protected void onAppListBuilt(List<UserAppInfo> list) {
            mCallback.onListOfAppsResult(list);
        }
    }

    private static class CurrentUserAppWithAdminGrantedPermissionsLister extends
            AppWithAdminGrantedPermissionsLister {
        private final ListOfAppsCallback mCallback;

        CurrentUserAppWithAdminGrantedPermissionsLister(String[] permissions,
                PackageManager packageManager, IPackageManager packageManagerService,
                DevicePolicyManager devicePolicyManager, UserManager userManager,
                ListOfAppsCallback callback) {
            super(permissions, packageManager, packageManagerService, devicePolicyManager,
                    userManager);
            mCallback = callback;
        }

        @Override
        protected void onAppListBuilt(List<UserAppInfo> list) {
            mCallback.onListOfAppsResult(list);
        }
    }

    private ComponentInfo getComponentInfo(ResolveInfo resolveInfo) {
        if (resolveInfo.activityInfo != null) {
            return resolveInfo.activityInfo;
        }
        if (resolveInfo.serviceInfo != null) {
            return resolveInfo.serviceInfo;
        }
        if (resolveInfo.providerInfo != null) {
            return resolveInfo.providerInfo;
        }
        throw new IllegalStateException("Missing ComponentInfo!");
    }
}
