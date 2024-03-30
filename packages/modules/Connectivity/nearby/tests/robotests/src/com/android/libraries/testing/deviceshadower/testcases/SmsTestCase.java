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

package com.android.libraries.testing.deviceshadower.testcases;

import android.content.pm.ProviderInfo;
import android.provider.Telephony;

import com.android.libraries.testing.deviceshadower.DeviceShadowEnvironmentInternal;

import org.robolectric.Robolectric;

/**
 * Base class for SMS Test
 */
public class SmsTestCase extends BaseTestCase {

    @Override
    public void setUp() throws Exception {
        super.setUp();
        ProviderInfo info = new ProviderInfo();
        info.authority = Telephony.Sms.CONTENT_URI.getAuthority();
        Robolectric.buildContentProvider(
                        DeviceShadowEnvironmentInternal.getSmsContentProviderClass())
                .create(info);
    }
}
