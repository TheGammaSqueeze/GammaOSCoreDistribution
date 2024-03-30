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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.support.test.uiautomator.UiDevice;
import android.view.KeyEvent;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Integration test for back navigation
 */
@Presubmit
public class BackNavigationTests {
    @Rule
    public final ActivityScenarioRule<BackNavigationActivity> mScenarioRule =
            new ActivityScenarioRule<>(BackNavigationActivity.class);
    private ActivityScenario<BackNavigationActivity> mScenario;
    private Instrumentation mInstrumentation;

    @Before
    public void setup() {
        mScenario = mScenarioRule.getScenario();
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        try {
            UiDevice.getInstance(mInstrumentation).wakeUp();
        } catch (RemoteException ignored) {
        }
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity();
    }

    @Test
    public void registerCallback_initialized() {
        CountDownLatch latch = registerBackCallback();
        mScenario.moveToState(Lifecycle.State.RESUMED);
        invokeBackAndAssertCallbackIsCalled(latch);
    }

    @Test
    public void registerCallback_created() {
        mScenario.moveToState(Lifecycle.State.CREATED);
        CountDownLatch latch = registerBackCallback();
        mScenario.moveToState(Lifecycle.State.STARTED);
        mScenario.moveToState(Lifecycle.State.RESUMED);
        invokeBackAndAssertCallbackIsCalled(latch);
    }

    @Test
    public void registerCallback_resumed() {
        mScenario.moveToState(Lifecycle.State.CREATED);
        mScenario.moveToState(Lifecycle.State.STARTED);
        mScenario.moveToState(Lifecycle.State.RESUMED);
        CountDownLatch latch = registerBackCallback();
        invokeBackAndAssertCallbackIsCalled(latch);
    }

    @Test
    public void onBackPressedNotCalled() {
        mScenario.moveToState(Lifecycle.State.CREATED)
                .moveToState(Lifecycle.State.STARTED)
                .moveToState(Lifecycle.State.RESUMED);
        CountDownLatch latch = registerBackCallback();
        invokeBackAndAssertCallbackIsCalled(latch);
        mScenario.onActivity((activity) ->
                assertFalse("Activity.onBackPressed should not be called",
                        activity.mOnBackPressedCalled));
    }

    private void invokeBackAndAssertCallbackIsCalled(CountDownLatch latch) {
        try {
            mInstrumentation.getUiAutomation().waitForIdle(500, 1000);
            UiDevice.getInstance(mInstrumentation).pressKeyCode(
                    KeyEvent.KEYCODE_BACK);
            assertTrue("OnBackInvokedCallback.onBackInvoked() was not called",
                    latch.await(500, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Application died before invoking the callback.\n" + ex.getMessage());
        } catch (TimeoutException ex) {
            fail(ex.getMessage());
        }
    }

    private CountDownLatch registerBackCallback() {
        CountDownLatch backInvokedLatch = new CountDownLatch(1);
        CountDownLatch backRegisteredLatch = new CountDownLatch(1);
        mScenario.onActivity(activity -> {
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    0, backInvokedLatch::countDown);
            backRegisteredLatch.countDown();
        });
        try {
            if (!backRegisteredLatch.await(100, TimeUnit.MILLISECONDS)) {
                fail("Back callback was not registered on the Activity thread. This might be "
                        + "an error with the test itself.");
            }
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
        return backInvokedLatch;
    }
}
