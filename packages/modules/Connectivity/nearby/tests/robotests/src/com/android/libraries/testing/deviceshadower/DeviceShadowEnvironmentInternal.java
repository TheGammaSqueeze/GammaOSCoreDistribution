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

package com.android.libraries.testing.deviceshadower;

import com.android.libraries.testing.deviceshadower.internal.DeviceShadowEnvironmentImpl;
import com.android.libraries.testing.deviceshadower.internal.sms.SmsContentProvider;

/**
 * Internal interface for device shadower.
 */
public class DeviceShadowEnvironmentInternal {

    /**
     * Set an interruptible point to tested code.
     * <p>
     * This should only make changes when DeviceShadowEnvironment initialized, which means only in
     * test cases.
     */
    public static void setInterruptibleBluetooth(int identifier) {
        if (DeviceShadowEnvironment.isInitialized()) {
            assert identifier > 0;
            DeviceShadowEnvironmentImpl.setInterruptibleBluetooth(identifier);
        }
    }

    /**
     * Mark all bluetooth operation broken after identifier in tested code.
     */
    public static void interruptBluetooth(String address, int identifier) {
        DeviceShadowEnvironmentImpl.interruptBluetooth(address, identifier);
    }

    /**
     * Return SMS content provider to be registered by robolectric context.
     */
    public static Class<SmsContentProvider> getSmsContentProviderClass() {
        return SmsContentProvider.class;
    }

}
