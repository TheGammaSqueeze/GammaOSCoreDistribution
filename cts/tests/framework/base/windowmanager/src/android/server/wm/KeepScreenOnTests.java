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

package android.server.wm;

import static android.provider.Settings.System.SCREEN_OFF_TIMEOUT;
import static android.server.wm.app.Components.TEST_ACTIVITY;
import static android.server.wm.app.Components.TURN_SCREEN_ON_ACTIVITY;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Intent;
import android.content.res.Resources;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class KeepScreenOnTests extends MultiDisplayTestBase {
    private static final String TAG = "KeepScreenOnTests";
    private String mInitialDisplayTimeout;
    private PowerManager mPowerManager;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mInitialDisplayTimeout =
                Settings.System.getString(mContext.getContentResolver(), SCREEN_OFF_TIMEOUT);
        mPowerManager = mContext.getSystemService(PowerManager.class);
    }

    @After
    public void tearDown() {
        setScreenOffTimeoutMs(mInitialDisplayTimeout);
    }

    @ApiTest(apis = "android.view.WindowManager.LayoutParams#FLAG_KEEP_SCREEN_ON")
    @Test
    public void testKeepScreenOn_activityOnDefaultDisplay_screenStaysOn() {
        setScreenOffTimeoutMs("500");
        launchActivity(TURN_SCREEN_ON_ACTIVITY);
        assertTrue(mPowerManager.isInteractive());

        SystemClock.sleep(getMinimumScreenOffTimeoutMs());

        assertTrue(mPowerManager.isInteractive());
        mWmState.assertVisibility(TURN_SCREEN_ON_ACTIVITY, true);
    }

    @ApiTest(apis = "android.view.WindowManager.LayoutParams#FLAG_KEEP_SCREEN_ON")
    @Test
    public void testKeepScreenOn_activityNotForeground_screenTurnsOff() {
        setScreenOffTimeoutMs("500");

        launchActivity(TURN_SCREEN_ON_ACTIVITY);
        assertTrue(mPowerManager.isInteractive());
        try (BlockingBroadcastReceiver r = BlockingBroadcastReceiver.create(mContext,
                Intent.ACTION_SCREEN_OFF).register()) {
            launchActivity(TEST_ACTIVITY);
        }
        mWmState.waitAndAssertVisibilityGone(TURN_SCREEN_ON_ACTIVITY);
        assertFalse(mPowerManager.isInteractive());
    }

    @ApiTest(apis = "android.view.WindowManager.LayoutParams#FLAG_KEEP_SCREEN_ON")
    @Test
    public void testKeepScreenOn_activityOnVirtualDisplay_screenStaysOn() {
        assumeTrue(supportsMultiDisplay());
        setScreenOffTimeoutMs("500");

        final WindowManagerState.DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true).createDisplay();
        launchActivityOnDisplay(TURN_SCREEN_ON_ACTIVITY, newDisplay.mId);
        mWmState.assertVisibility(TURN_SCREEN_ON_ACTIVITY, true);
        assertTrue(mPowerManager.isInteractive());

        SystemClock.sleep(getMinimumScreenOffTimeoutMs());

        assertTrue(mPowerManager.isInteractive());
        mWmState.assertVisibility(TURN_SCREEN_ON_ACTIVITY, true);
    }

    @ApiTest(apis = "android.view.WindowManager.LayoutParams#FLAG_KEEP_SCREEN_ON")
    @Test
    public void testKeepScreenOn_activityOnVirtualDisplayNotForeground_screenTurnsOff() {
        assumeTrue(supportsMultiDisplay());
        setScreenOffTimeoutMs("500");

        final WindowManagerState.DisplayContent newDisplay = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true).createDisplay();
        launchActivityOnDisplay(TURN_SCREEN_ON_ACTIVITY, newDisplay.mId);
        assertTrue(mPowerManager.isInteractive());
        try (BlockingBroadcastReceiver r = BlockingBroadcastReceiver.create(mContext,
                Intent.ACTION_SCREEN_OFF).register()) {
            launchActivityOnDisplay(TEST_ACTIVITY, newDisplay.mId);
        }
        mWmState.waitAndAssertVisibilityGone(TURN_SCREEN_ON_ACTIVITY);
        assertFalse(mPowerManager.isInteractive());
    }

    private void setScreenOffTimeoutMs(String timeoutMs) {
        Settings.System.putString(
                mContext.getContentResolver(), SCREEN_OFF_TIMEOUT, timeoutMs);
    }

    private int getMinimumScreenOffTimeoutMs() {
        return mContext.getResources().getInteger(
                Resources.getSystem().getIdentifier("config_minimumScreenOffTimeout", "integer",
                        "android"));
    }
}
