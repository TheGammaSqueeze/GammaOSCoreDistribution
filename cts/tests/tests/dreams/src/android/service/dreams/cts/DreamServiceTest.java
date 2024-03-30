/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.DreamCoordinator;
import android.service.dreams.DreamService;
import android.view.ActionMode;
import android.view.Display;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DreamServiceTest extends ActivityManagerTestBase {
    private static final String DREAM_SERVICE_COMPONENT =
            "android.app.dream.cts.app/.SeparateProcessDreamService";

    private DreamCoordinator mDreamCoordinator = new DreamCoordinator(mContext);

    @Before
    public void setup() {
        mDreamCoordinator.setup();
    }

    @After
    public void reset()  {
        mDreamCoordinator.restoreDefaults();
    }

    @Test
    public void testOnWindowStartingActionMode() {
        DreamService dreamService = new DreamService();

        ActionMode actionMode = dreamService.onWindowStartingActionMode(null);

        assertEquals(actionMode, null);
    }

    @Test
    public void testOnWindowStartingActionModeTyped() {
        DreamService dreamService = new DreamService();

        ActionMode actionMode = dreamService.onWindowStartingActionMode(
                null, ActionMode.TYPE_FLOATING);

        assertEquals(actionMode, null);
    }

    @Test
    public void testDreamInSeparateProcess() {
        assumeFalse(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE));

        final ComponentName dreamService =
                ComponentName.unflattenFromString(DREAM_SERVICE_COMPONENT);
        final ComponentName dreamActivity = mDreamCoordinator.setActiveDream(dreamService);

        mDreamCoordinator.startDream();
        waitAndAssertTopResumedActivity(dreamActivity, Display.DEFAULT_DISPLAY,
                "Dream activity should be the top resumed activity");
        mDreamCoordinator.stopDream();
    }

}
