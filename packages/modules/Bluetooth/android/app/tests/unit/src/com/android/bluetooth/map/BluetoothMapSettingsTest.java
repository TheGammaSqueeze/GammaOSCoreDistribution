/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.bluetooth.map;

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.R;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class BluetoothMapSettingsTest {

    Context mTargetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    Intent mIntent;

    ActivityScenario<BluetoothMapSettings> mActivityScenario;

    @Before
    public void setUp() {
        Assume.assumeTrue("Ignore test when BluetoothMapService is not enabled",
                BluetoothMapService.isEnabled());
        enableActivity(true);
        mIntent = new Intent();
        mIntent.setClass(mTargetContext, BluetoothMapSettings.class);
        mActivityScenario = ActivityScenario.launch(mIntent);
    }

    @After
    public void tearDown() throws Exception {
        if (mActivityScenario != null) {
            // Workaround for b/159805732. Without this, test hangs for 45 seconds.
            Thread.sleep(1_000);
            mActivityScenario.close();
        }
        enableActivity(false);
    }

    @Test
    public void initialize() throws Exception {
        onView(withId(R.id.bluetooth_map_settings_list_view)).check(matches(isDisplayed()));
    }

    private void enableActivity(boolean enable) {
        int enabledState = enable ? COMPONENT_ENABLED_STATE_ENABLED
                : COMPONENT_ENABLED_STATE_DEFAULT;

        mTargetContext.getPackageManager().setApplicationEnabledSetting(
                mTargetContext.getPackageName(), enabledState, DONT_KILL_APP);

        ComponentName activityName = new ComponentName(mTargetContext, BluetoothMapSettings.class);
        mTargetContext.getPackageManager().setComponentEnabledSetting(
                activityName, enabledState, DONT_KILL_APP);
    }
}
