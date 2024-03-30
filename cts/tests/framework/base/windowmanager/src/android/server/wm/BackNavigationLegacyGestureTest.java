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

import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.server.wm.WindowManagerState.STATE_STOPPED;
import static android.server.wm.backlegacyapp.Components.BACK_LEGACY;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.os.SystemClock;
import android.server.wm.TestJournalProvider.TestJournalContainer;
import android.server.wm.backlegacyapp.Components;
import android.support.test.uiautomator.UiDevice;
import android.view.InputEvent;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.GestureNavRule;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Integration test for back navigation legacy mode
 */
public class BackNavigationLegacyGestureTest extends ActivityManagerTestBase {
    private Instrumentation mInstrumentation;

    @ClassRule
    public static GestureNavRule GESTURE_NAV_RULE = new GestureNavRule();
    private UiDevice mUiDevice;

    @Before
    public void setup() {
        GESTURE_NAV_RULE.assumeGestureNavigationMode();
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
    }

    @Test
    public void receiveOnBackPressed() {
        TestJournalContainer.start();
        launchActivity(BACK_LEGACY);
        mWmState.assertActivityDisplayed(BACK_LEGACY);
        waitAndAssertActivityState(BACK_LEGACY, STATE_RESUMED, "Activity should be resumed");
        mUiDevice = UiDevice.getInstance(mInstrumentation);
        doBackGesture();
        waitAndAssertActivityState(BACK_LEGACY, STATE_STOPPED, "Activity should be stopped");
        assertTrue("OnBackPressed should have been called",
                TestJournalContainer.get(BACK_LEGACY).extras.getBoolean(
                        Components.KEY_ON_BACK_PRESSED_CALLED));
        assertFalse("OnBackInvoked should not have been called",
                TestJournalContainer.get(BACK_LEGACY).extras.getBoolean(
                        Components.KEY_ON_BACK_INVOKED_CALLED));
    }

    /**
     * Do a back gesture. (Swipe)
     */
    private void doBackGesture() {
        int midHeight = mUiDevice.getDisplayHeight() / 2;
        int midWidth = mUiDevice.getDisplayWidth() / 2;
        quickSwipe(0, midHeight, midWidth, midHeight, 10);
        mUiDevice.waitForIdle();
    }

    private void injectInputEventUnSynced(@NonNull InputEvent event) {
        mInstrumentation.getUiAutomation().injectInputEvent(event, false /* sync */,
                false /* waitForAnimations */);
    }

    /**
     * Injecting a sequence of motion event to simulate swipe without waiting for sync transaction.
     */
    private void quickSwipe(float startX, float startY, float endX, float endY, int steps) {
        if (steps <= 0) {
            steps = 1;
        }
        final long startDownTime = SystemClock.uptimeMillis();
        MotionEvent firstDown = MotionEvent.obtain(startDownTime, startDownTime,
                MotionEvent.ACTION_DOWN, startX, startY, 0);
        injectInputEventUnSynced(firstDown);

        // inject in every 5 ms.
        final int delayMillis = 5;
        long nextEventTime = startDownTime + delayMillis;
        final float stepGapX = (endX - startX) / steps;
        final float stepGapY = (endY - startY) / steps;
        for (int i = 0; i < steps; i++) {
            SystemClock.sleep(delayMillis);
            final float nextX = startX + stepGapX * i;
            final float nextY = startY + stepGapY * i;
            MotionEvent move = MotionEvent.obtain(startDownTime, nextEventTime,
                    MotionEvent.ACTION_MOVE, nextX, nextY, 0);
            injectInputEventUnSynced(move);
            nextEventTime += delayMillis;
        }

        SystemClock.sleep(delayMillis);
        MotionEvent up = MotionEvent.obtain(startDownTime, nextEventTime,
                MotionEvent.ACTION_UP, endX, endY, 0);
        injectInputEventUnSynced(up);
    }
}
