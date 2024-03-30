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

package com.android.settings.development;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.content.Context;
import android.os.SystemProperties;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSystemProperties;

@RunWith(RobolectricTestRunner.class)
@Config(
        shadows = {
            ShadowSystemProperties.class,
        })
public class RebootWithMtePreferenceControllerTest {
    private Context mContext;
    private RebootWithMtePreferenceController mController;
    @Mock private DevelopmentSettingsDashboardFragment mSettings;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        mContext = ApplicationProvider.getApplicationContext();
        mController = new RebootWithMtePreferenceController(mContext, mSettings);
    }

    @Test
    public void onAvailable_falseByDefault() {
        assertFalse(mController.isAvailable());
    }

    @Test
    public void onAvailable_sysPropEnabled() {
        SystemProperties.set("ro.arm64.memtag.bootctl_supported", "1");
        assertTrue(mController.isAvailable());
    }
}
