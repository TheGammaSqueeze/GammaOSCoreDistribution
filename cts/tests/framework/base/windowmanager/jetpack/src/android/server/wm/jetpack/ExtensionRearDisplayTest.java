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

package android.server.wm.jetpack;

import static android.server.wm.UiDeviceUtils.pressUnlockButton;
import static android.server.wm.UiDeviceUtils.pressWakeupButton;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.PollingCheck.waitFor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.res.Resources;
import android.hardware.devicestate.DeviceStateManager;
import android.hardware.devicestate.DeviceStateRequest;
import android.os.PowerManager;
import android.platform.test.annotations.Presubmit;
import android.server.wm.DeviceStateUtils;
import android.server.wm.jetpack.utils.TestRearDisplayActivity;
import android.server.wm.jetpack.utils.WindowExtensionTestRule;
import android.server.wm.jetpack.utils.WindowManagerJetpackTestBase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.window.extensions.area.WindowAreaComponent;
import androidx.window.extensions.area.WindowAreaComponent.WindowAreaSessionState;
import androidx.window.extensions.area.WindowAreaComponent.WindowAreaStatus;
import androidx.window.extensions.core.util.function.Consumer;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Tests for the {@link androidx.window.extensions.area.WindowAreaComponent} implementation
 * of the rear display functionality provided on the device (and only if one is available).
 *
 * Build/Install/Run:
 * atest CtsWindowManagerJetpackTestCases:ExtensionRearDisplayTest
 */
@LargeTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class ExtensionRearDisplayTest extends WindowManagerJetpackTestBase implements
        DeviceStateManager.DeviceStateCallback {

    private static final int TIMEOUT = 2000;
    private static final int INVALID_DEVICE_STATE = -1;

    private TestRearDisplayActivity mActivity;
    private WindowAreaComponent mWindowAreaComponent;
    private int mCurrentDeviceState;
    private int mCurrentDeviceBaseState;
    private int[] mSupportedDeviceStates;
    @WindowAreaStatus
    private Integer mWindowAreaStatus;
    @WindowAreaSessionState
    private Integer mWindowAreaSessionState;
    private int mRearDisplayState;

    private final Context mInstrumentationContext = getInstrumentation().getTargetContext();
    private final KeyguardManager mKeyguardManager = mInstrumentationContext.getSystemService(
            KeyguardManager.class);
    private final DeviceStateManager mDeviceStateManager = mInstrumentationContext
            .getSystemService(DeviceStateManager.class);

    private final Consumer<Integer> mStatusListener = (status) -> mWindowAreaStatus = status;

    private final Consumer<Integer> mSessionStateListener = (sessionState) -> {
        mWindowAreaSessionState = sessionState;
    };

    @Rule
    public final WindowExtensionTestRule mWindowManagerJetpackTestRule =
            new WindowExtensionTestRule(WindowAreaComponent.class);

    @Before
    @Override
    public void setUp() {
        super.setUp();
        mWindowAreaComponent =
                (WindowAreaComponent) mWindowManagerJetpackTestRule.getExtensionComponent();
        mSupportedDeviceStates = mDeviceStateManager.getSupportedStates();
        assumeTrue(mSupportedDeviceStates.length > 1);
        // TODO(b/236022708) Move rear display state to device state config file
        mRearDisplayState = getInstrumentation().getTargetContext().getResources()
                .getInteger(Resources.getSystem()
                        .getIdentifier("config_deviceStateRearDisplay", "integer", "android"));
        assumeTrue(mRearDisplayState != INVALID_DEVICE_STATE);
        mDeviceStateManager.registerCallback(Runnable::run, this);
        mWindowAreaComponent.addRearDisplayStatusListener(mStatusListener);
        unlockDeviceIfNeeded();
        mActivity = startActivityNewTask(TestRearDisplayActivity.class);
        waitAndAssert(() -> mWindowAreaStatus != null);
    }

    @After
    @Override
    public void tearDown() {
        super.tearDown();
        if (mWindowAreaComponent != null) {
            mWindowAreaComponent.removeRearDisplayStatusListener(mStatusListener);
            try {
                DeviceStateUtils.runWithControlDeviceStatePermission(
                        () -> mDeviceStateManager.cancelStateRequest());
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }

    /**
     * Tests that the RearDisplay status listeners receive the correct {@link WindowAreaStatus}
     * values.
     *
     * The test goes through all supported device states and verifies that the correct status is
     * returned. If the state does not place the device in the active RearDisplay configuration
     * (i.e. the base state of the device is different than the current state, and that current
     * state is the RearDisplay state), then it should receive the
     * {@link WindowAreaStatus#STATUS_AVAILABLE} value, otherwise it should receive the
     * {@link WindowAreaStatus#STATUS_UNAVAILABLE} value.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.area.WindowAreaComponent#addRearDisplayStatusListener",
            "androidx.window.extensions.area.WindowAreaComponent#removeRearDisplayStatusListener"})
    @Test
    public void testRearDisplayStatusListeners() throws Throwable {
        Set<Integer> requestedStates = new HashSet<>();
        while (requestedStates.size() != mSupportedDeviceStates.length) {
            int newState = determineNewState(mCurrentDeviceState, mSupportedDeviceStates,
                    requestedStates);
            if (newState != INVALID_DEVICE_STATE) {
                requestedStates.add(newState);
                DeviceStateRequest request = DeviceStateRequest.newBuilder(newState).build();
                DeviceStateUtils.runWithControlDeviceStatePermission(() ->
                            mDeviceStateManager.requestState(request, null, null));

                waitAndAssert(() -> mCurrentDeviceState == newState);
                // If the state does not put the device into the rear display configuration,
                // then the listener should receive the STATUS_AVAILABLE value.
                if (!isRearDisplayActive(mCurrentDeviceState, mCurrentDeviceBaseState)) {
                    waitAndAssert(
                            () -> mWindowAreaStatus == WindowAreaComponent.STATUS_AVAILABLE);
                } else {
                    waitAndAssert(
                            () -> mWindowAreaStatus == WindowAreaComponent.STATUS_UNAVAILABLE);
                }
            }
        }
    }

    /**
     * Tests that you can start and end rear display mode. Verifies that the {@link Consumer} that
     * is provided when calling {@link WindowAreaComponent#startRearDisplaySession} receives
     * the {@link WindowAreaSessionState#SESSION_STATE_ACTIVE} value when starting the session
     * and {@link WindowAreaSessionState#SESSION_STATE_INACTIVE} when ending the session.
     *
     * This test also verifies that the {@link android.app.Activity} is still visible when rear
     * display mode is started, and that the activity received a configuration change when enabling
     * and disabling rear display mode. This is verifiable due to the current generation of
     * hardware and the fact that there are different screen sizes from the different displays.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.area.WindowAreaComponent#startRearDisplaySession",
            "androidx.window.extensions.area.WindowAreaComponent#endRearDisplaySession"})
    @Test
    public void testStartAndEndRearDisplaySession() throws Throwable {
        assumeTrue(mWindowAreaStatus == WindowAreaComponent.STATUS_AVAILABLE);
        assumeTrue(mCurrentDeviceState != mRearDisplayState);

        mActivity.mConfigurationChanged = false;
        // Running with CONTROL_DEVICE_STATE permission to bypass educational overlay
        DeviceStateUtils.runWithControlDeviceStatePermission(() ->
                mWindowAreaComponent.startRearDisplaySession(mActivity, mSessionStateListener));
        waitAndAssert(() -> mActivity.mConfigurationChanged);
        assertTrue(mWindowAreaSessionState != null
                        && mWindowAreaSessionState == WindowAreaComponent.SESSION_STATE_ACTIVE);
        assertEquals(mCurrentDeviceState, mRearDisplayState);
        assertTrue(isActivityVisible(mActivity));

        mActivity.mConfigurationChanged = false;
        mWindowAreaComponent.endRearDisplaySession();
        waitAndAssert(() -> WindowAreaComponent.SESSION_STATE_INACTIVE == mWindowAreaSessionState);
        waitAndAssert(() -> mActivity.mConfigurationChanged);
        assertTrue(isActivityVisible(mActivity));
        // Cancelling rear display mode should cancel the override, so verifying that the
        // device state is the same as the physical state of the device.
        assertEquals(mCurrentDeviceState, mCurrentDeviceBaseState);
        assertEquals(WindowAreaComponent.STATUS_AVAILABLE, (int) mWindowAreaStatus);

    }

    @Override
    public void onBaseStateChanged(int state) {
        mCurrentDeviceBaseState = state;
    }

    @Override
    public void onStateChanged(int state) {
        mCurrentDeviceState = state;
    }

    /**
     * Returns the next state that we should request that isn't the current state and
     * has not already been requested.
     */
    private int determineNewState(int currentDeviceState, int[] statesToRequest,
            Set<Integer> requestedStates) {
        for (int state : statesToRequest) {
            if (state != currentDeviceState && !requestedStates.contains(state)) {
                return state;
            }
        }
        return INVALID_DEVICE_STATE;
    }

    /**
     * Helper method to determine if a rear display session is currently active by checking
     * if the current device configuration matches that of rear display. This would be true
     * if there is a device override currently active (base state != current state) and the current
     * state is that which corresponds to {@code mRearDisplayState}
     * @return {@code true} if the device is in rear display mode and {@code false} if not
     */
    private boolean isRearDisplayActive(int currentDeviceState, int currentDeviceBaseState) {
        return (currentDeviceState != currentDeviceBaseState)
                && (currentDeviceState == mRearDisplayState);
    }

    private void unlockDeviceIfNeeded() {
        if (isKeyguardLocked() || !Objects.requireNonNull(
                mInstrumentationContext.getSystemService(PowerManager.class)).isInteractive()) {
            pressWakeupButton();
            pressUnlockButton();
        }
    }

    private boolean isKeyguardLocked() {
        return mKeyguardManager != null && mKeyguardManager.isKeyguardLocked();
    }

    private void waitAndAssert(PollingCheck.PollingCheckCondition condition) {
        waitFor(TIMEOUT, condition);
    }
}
