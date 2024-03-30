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

package com.android.bluetooth.opp;

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static org.mockito.Mockito.mock;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.intent.Intents;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bluetooth.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

public class BluetoothOppBtEnableActivityTest {

    Intent mIntent;
    Context mTargetContext;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTargetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mIntent = new Intent();
        mIntent.setClass(mTargetContext, BluetoothOppBtEnableActivity.class);
        Intents.init();
        BluetoothOppTestUtils.enableOppActivities(true, mTargetContext);
    }

    @After
    public void tearDown() {
        Intents.release();
        BluetoothOppTestUtils.enableOppActivities(false, mTargetContext);
    }

    @Test
    public void onCreate_clickOnEnable_launchEnablingActivity() {
        ActivityScenario<BluetoothOppBtEnableActivity> activityScenario = ActivityScenario.launch(
                mIntent);
        activityScenario.onActivity(
                activity -> activity.mOppManager = mock(BluetoothOppManager.class));
        onView(withText(mTargetContext.getText(R.string.bt_enable_ok).toString())).inRoot(
                isDialog()).check(matches(isDisplayed())).perform(click());
        intended(hasComponent(BluetoothOppBtEnablingActivity.class.getName()));
    }
}
