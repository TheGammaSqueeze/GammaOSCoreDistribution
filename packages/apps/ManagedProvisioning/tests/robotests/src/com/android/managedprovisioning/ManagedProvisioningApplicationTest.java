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

package com.android.managedprovisioning;

import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;

import static com.google.common.truth.Truth.assertThat;

import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowWindow;

@RunWith(RobolectricTestRunner.class)
public final class ManagedProvisioningApplicationTest {
    private final ManagedProvisioningApplication mApplication =
            new ManagedProvisioningApplication();

    @Ignore("b/218480743")
    @Test
    public void markKeepScreenOn_works() {
        Activity activity = createActivity();
        ShadowWindow shadowWindow = shadowOf(activity.getWindow());
        mApplication.markKeepScreenOn();

        mApplication.maybeKeepScreenOn(activity);

        assertThat(shadowWindow.getFlag(FLAG_KEEP_SCREEN_ON)).isTrue();
    }

    @Ignore("b/218480743")
    @Test
    public void markKeepScreenOn_screenNotMarkedOn_flagNotSet() {
        Activity activity = createActivity();
        ShadowWindow shadowWindow = shadowOf(activity.getWindow());

        mApplication.maybeKeepScreenOn(activity);

        assertThat(shadowWindow.getFlag(FLAG_KEEP_SCREEN_ON)).isFalse();
    }

    private Activity createActivity() {
        return Robolectric.buildActivity(Activity.class)
                .create()
                .start()
                .resume()
                .visible()
                .get();
    }
}
