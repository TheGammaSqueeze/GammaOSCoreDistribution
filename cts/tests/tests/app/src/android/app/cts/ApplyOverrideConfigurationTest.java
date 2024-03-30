/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package android.app.cts;

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import android.app.ActivityOptions;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.SmallTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Tests the {@link android.view.ContextThemeWrapper#applyOverrideConfiguration(Configuration)}
 * method and how it affects the Activity's resources and lifecycle callbacks.
 */
public class ApplyOverrideConfigurationTest {
    private static ActivityScenarioRule<ApplyOverrideConfigurationActivity>
            getActivityScenarioRule() {
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN);

        final Intent intent = new Intent(
                getInstrumentation().getTargetContext(), ApplyOverrideConfigurationActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return new ActivityScenarioRule<>(intent, options.toBundle());
    }

    private final ActivityScenarioRule<ApplyOverrideConfigurationActivity> mActivityScenarioRule =
            getActivityScenarioRule();

    @Rule
    public TestRule mRule = RuleChain.outerRule(new ExternalResource() {
        @Override
        protected void before() {
            getInstrumentation().getUiAutomation().setRotation(UiAutomation.ROTATION_FREEZE_0);
        }

        @Override
        protected void after() {
            getInstrumentation().getUiAutomation().setRotation(UiAutomation.ROTATION_UNFREEZE);
        }
    }).around(mActivityScenarioRule);

    private <R> R onActivity(Function<ApplyOverrideConfigurationActivity, R> func) {
        Object[] result = new Object[1];
        mActivityScenarioRule.getScenario().onActivity(activity -> {
            result[0] = func.apply(activity);
        });
        return (R) result[0];
    }

    @SmallTest
    @Test
    public void testOverriddenConfigurationIsPassedIntoCallback() throws Exception {
        // This test instruments display rotation; disable it on devices that do not
        // support auto-rotation.

        if (!onActivity(ApplyOverrideConfigurationTest::isRotationSupported)) {
            return;
        }

        final Configuration originalConfig =
                onActivity(activity -> activity.getResources().getConfiguration());
        assertEquals(ApplyOverrideConfigurationActivity.OVERRIDE_SMALLEST_WIDTH,
                originalConfig.smallestScreenWidthDp);

        final Future<Configuration> callbackConfigurationFuture = onActivity(
                ApplyOverrideConfigurationActivity::watchForSingleOnConfigurationChangedCallback);
        getInstrumentation().getUiAutomation().setRotation(UiAutomation.ROTATION_FREEZE_90);
        final Configuration callbackConfig = callbackConfigurationFuture.get();
        assertNotNull(callbackConfig);

        final Configuration newConfig = onActivity(
                activity -> activity.getResources().getConfiguration());
        assertNotEquals(originalConfig.orientation, newConfig.orientation);
        assertEquals(ApplyOverrideConfigurationActivity.OVERRIDE_SMALLEST_WIDTH,
                newConfig.smallestScreenWidthDp);
        assertEquals(newConfig, callbackConfig);
    }

    /**
     * Gets whether the device supports rotation. In general such a
     * device has both portrait and landscape features.
     *
     * @param context Context for accessing system resources.
     * @return Whether the device supports rotation.
     */
    public static boolean isRotationSupported(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_SCREEN_PORTRAIT)
                && pm.hasSystemFeature(PackageManager.FEATURE_SCREEN_LANDSCAPE)
                && !isVrHeadset(context);
    }

    /**
     * Gets whether the DUT is a vr headset.
     *
     * @param context Context for accessing system resources.
     * @return Whether the device is a vr headset.
     */
    public static boolean isVrHeadset(Context context) {
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_VR_HEADSET;
    }
}
