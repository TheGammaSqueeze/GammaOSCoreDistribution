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

package com.android.bedstead.nene.location;

import static android.Manifest.permission.WRITE_SECURE_SETTINGS;

import android.content.Context;
import android.location.LocationManager;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.compatibility.common.util.BlockingCallback;

import java.util.function.Consumer;

/** Helper methods related to the location of the device. */
public final class Locations {

    private static final String DEFAULT_TEST_PROVIDER = "test_provider";
    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final LocationManager sLocationManager = sContext.getSystemService(
            LocationManager.class);

    public static final Locations sInstance = new Locations();

    private Locations() {
    }

    public LocationProvider addLocationProvider(String providerName) {
        return new LocationProvider(providerName);
    }

    /**
     * Add a default location provider with the name "test_provider".
     */
    public LocationProvider addLocationProvider() {
        return new LocationProvider(DEFAULT_TEST_PROVIDER);
    }

    /**
     * Set location enabled or disabled for the instrumented user
     */
    public void setLocationEnabled(boolean enabled) {
        try (PermissionContext p = TestApis.permissions().withPermission(
                WRITE_SECURE_SETTINGS)) {
            sLocationManager.setLocationEnabledForUser(enabled,
                    TestApis.users().instrumented().userHandle());
        }
    }

    public static class BlockingLostModeLocationUpdateCallback extends
            BlockingCallback<Boolean> implements Consumer<Boolean> {
        @Override
        public void accept(Boolean result) {
            callbackTriggered(result);
        }
    }
}
