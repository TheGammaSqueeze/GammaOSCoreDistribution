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

package android.ondevicepersonalization;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.ondevicepersonalization.aidl.IOnDevicePersonalizationManagerService;
import android.os.IBinder;
import android.util.Slog;

import java.util.List;

/**
 * OnDevicePersonalization Manager.
 *
 * @hide
 */
public class OnDevicePersonalizationManager {
    public static final String ON_DEVICE_PERSONALIZATION_SERVICE =
            "on_device_personalization_service";

    private boolean mBound = false;
    private static final String TAG = "OdpManager";

    private IOnDevicePersonalizationManagerService mService;
    private final Context mContext;

    public OnDevicePersonalizationManager(Context context) {
        mContext = context;
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = IOnDevicePersonalizationManagerService.Stub.asInterface(service);
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            mBound = false;
        }
    };

    private static final int BIND_SERVICE_INTERVAL_MS = 1000;
    private static final int BIND_SERVICE_RETRY_TIMES = 3;
    private static final String VERSION = "1.0";

    /**
     * Gets OnDevicePersonalization version.
     *
     * @hide
     */
    public String getVersion() {
        return VERSION;
    }
    /**
     * Find the ComponentName of the service, given its intent and package manager.
     *
     * @return ComponentName of the service. Null if the service is not found.
     */
    private @Nullable ComponentName resolveService(@NonNull Intent intent,
            @NonNull PackageManager pm) {
        List<ResolveInfo> services = pm.queryIntentServices(intent,
                PackageManager.ResolveInfoFlags.of(0));
        if (services == null || services.isEmpty()) {
            Slog.e(TAG, "Failed to find ondevicepersonalization service");
            return null;
        }

        for (int i = 0; i < services.size(); i++) {
            ResolveInfo ri = services.get(i);
            ComponentName resolved = new ComponentName(
                    ri.serviceInfo.packageName, ri.serviceInfo.name);
            // There should only be one matching service inside the given package.
            // If there's more than one, return the first one found.
            return resolved;
        }
        Slog.e(TAG, "Didn't find any matching ondevicepersonalization service.");
        return null;
    }
}
