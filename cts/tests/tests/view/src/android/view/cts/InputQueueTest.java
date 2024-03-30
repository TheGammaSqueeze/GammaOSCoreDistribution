/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.view.cts;

import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.os.SystemClock;
import android.view.InputQueue;
import android.view.MotionEvent;
import android.view.Window;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link AInputQueue}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class InputQueueTest {
    private static final String LOG_TAG = InputQueueTest.class.getSimpleName();
    static {
        System.loadLibrary("ctsview_jni");
    }

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();

    private static native boolean waitForEvent(InputQueue inputQueue);
    private static native void inputQueueTest(InputQueue inputQueue);

    @Rule
    public ActivityTestRule<InputQueueCtsActivity> mTestActivityRule =
            new ActivityTestRule<>(InputQueueCtsActivity.class);

    @Test
    public void testNativeInputQueue() throws Throwable {
        InputQueueCtsActivity activity = mTestActivityRule.getActivity();
        Window window = activity.getWindow();
        InputQueue inputQueue = activity.getInputQueue();

        // An event is created Java-side.
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 0, 0, 0);
        window.injectInputEvent(event);

        assertTrue("Timed out waiting for event", waitForEvent(inputQueue));

        inputQueueTest(inputQueue); // Check the injected event is received on the native side.
    }
}
