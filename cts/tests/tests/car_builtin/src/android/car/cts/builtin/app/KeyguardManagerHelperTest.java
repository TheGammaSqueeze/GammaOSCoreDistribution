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

package android.car.cts.builtin.app;

import static android.server.wm.UiDeviceUtils.pressUnlockButton;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.car.builtin.app.KeyguardManagerHelper;
import android.server.wm.ActivityManagerTestBase;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class KeyguardManagerHelperTest extends ActivityManagerTestBase {

    private static final String TAG = KeyguardManagerHelperTest.class.getSimpleName();

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        assumeTrue(supportsSecureLock());
    }

    @Test
    public void testIsKeyguardLocked() throws Exception {
        try (LockScreenSession lockScreenSession = createManagedLockScreenSession()) {
            lockScreenSession.setLockCredential().gotoKeyguard();
            assertThat(KeyguardManagerHelper.isKeyguardLocked()).isTrue();

            unlockDevice();
            lockScreenSession.enterAndConfirmLockCredential();
            mWmState.waitAndAssertKeyguardGone();
            assertThat(KeyguardManagerHelper.isKeyguardLocked()).isFalse();
        }
    }

    private void unlockDevice() {
        touchAndCancelOnDisplayCenterSync(DEFAULT_DISPLAY);
        pressUnlockButton();
    }
}
