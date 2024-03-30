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

package com.android.server.nearby;

import android.provider.DeviceConfig;

import androidx.annotation.VisibleForTesting;

/**
 * A utility class for encapsulating Nearby feature flag configurations.
 */
public class NearbyConfiguration {

    /**
     * Flag use to enable presence legacy broadcast.
     */
    public static final String NEARBY_ENABLE_PRESENCE_BROADCAST_LEGACY =
            "nearby_enable_presence_broadcast_legacy";

    private boolean mEnablePresenceBroadcastLegacy;

    public NearbyConfiguration() {
        mEnablePresenceBroadcastLegacy = getDeviceConfigBoolean(
                NEARBY_ENABLE_PRESENCE_BROADCAST_LEGACY, false /* defaultValue */);

    }

    /**
     * Returns whether broadcasting legacy presence spec is enabled.
     */
    public boolean isPresenceBroadcastLegacyEnabled() {
        return mEnablePresenceBroadcastLegacy;
    }

    private boolean getDeviceConfigBoolean(final String name, final boolean defaultValue) {
        final String value = getDeviceConfigProperty(name);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    @VisibleForTesting
    protected String getDeviceConfigProperty(String name) {
        return DeviceConfig.getProperty(DeviceConfig.NAMESPACE_TETHERING, name);
    }
}
