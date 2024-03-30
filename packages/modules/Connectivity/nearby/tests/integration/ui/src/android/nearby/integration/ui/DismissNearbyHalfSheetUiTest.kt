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

package android.nearby.integration.ui

import android.platform.test.rule.ScreenRecordRule.ScreenRecord
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith

/** An instrumented test to dismiss Nearby half sheet UI.
 *
 * To run this test directly:
 * am instrument -w -r \
 * -e class android.nearby.integration.ui.DismissNearbyHalfSheetUiTest \
 * android.nearby.integration.ui/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class DismissNearbyHalfSheetUiTest : BaseUiTest() {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    @ScreenRecord
    fun dismissHalfSheet() {
        device.pressHome()
        device.waitForIdle()

        assertWithMessage("Fail to dismiss Nearby half sheet.").that(
            device.findObject(NearbyHalfSheetUiMap.DevicePairingFragment.connectButton)
        ).isNull()
    }
}