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

package android.service.dreams.cts;

import android.content.ComponentName;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.DreamCoordinator;
import android.view.Display;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@ApiTest(apis = {"com.android.server.dreams.DreamManagerService#setSystemDreamComponent"})
@RunWith(AndroidJUnit4.class)
@SmallTest
public class SystemDreamTest extends ActivityManagerTestBase {
    private static final String USER_DREAM_COMPONENT =
            "android.app.dream.cts.app/.TestDreamService";
    private static final String SYSTEM_DREAM_COMPONENT =
            "android.app.dream.cts.app/.TestSystemDreamService";

    private final DreamCoordinator mDreamCoordinator = new DreamCoordinator(mContext);

    private ComponentName mSystemDream;
    private ComponentName mUserDreamActivity;

    @Before
    public void setup() {
        mDreamCoordinator.setup();

        final ComponentName userDream = ComponentName.unflattenFromString(USER_DREAM_COMPONENT);
        mSystemDream = ComponentName.unflattenFromString(SYSTEM_DREAM_COMPONENT);
        mUserDreamActivity = mDreamCoordinator.setActiveDream(userDream);
    }

    @After
    public void reset()  {
        mDreamCoordinator.restoreDefaults();
    }

    @Test
    public void startDream_systemDreamNotSet_startUserDream() {
        startAndVerifyDreamActivity(mUserDreamActivity);
    }

    @Test
    public void startDream_systemDreamSet_startSystemDream() {
        final ComponentName systemDreamActivity = mDreamCoordinator.setSystemDream(mSystemDream);
        startAndVerifyDreamActivity(systemDreamActivity);
    }

    @Test
    public void switchDream_systemDreamSet_switchToSystemDream() {
        mDreamCoordinator.startDream();

        // Sets system dream.
        final ComponentName systemDreamActivity = mDreamCoordinator.setSystemDream(mSystemDream);
        try {
            // Verifies switched to system dream.
            waitAndAssertTopResumedActivity(systemDreamActivity, Display.DEFAULT_DISPLAY,
                    getDreamActivityVerificationMessage(systemDreamActivity));
        } finally {
            mDreamCoordinator.stopDream();
        }
    }

    @Test
    public void switchDream_systemDreamCleared_switchToUserDream() {
        mDreamCoordinator.setSystemDream(mSystemDream);
        mDreamCoordinator.startDream();

        // Clears system dream.
        mDreamCoordinator.setSystemDream(null);
        try {
            // Verifies switched back to user dream.
            waitAndAssertTopResumedActivity(mUserDreamActivity, Display.DEFAULT_DISPLAY,
                    getDreamActivityVerificationMessage(mUserDreamActivity));
        } finally {
            mDreamCoordinator.stopDream();
        }
    }

    private void startAndVerifyDreamActivity(ComponentName expectedDreamActivity) {
        try {
            mDreamCoordinator.startDream();
            waitAndAssertTopResumedActivity(expectedDreamActivity, Display.DEFAULT_DISPLAY,
                    getDreamActivityVerificationMessage(expectedDreamActivity));
        } finally {
            mDreamCoordinator.stopDream();
        }
    }

    private String getDreamActivityVerificationMessage(ComponentName activity) {
        return activity.flattenToString() + " should be displayed";
    }
}
