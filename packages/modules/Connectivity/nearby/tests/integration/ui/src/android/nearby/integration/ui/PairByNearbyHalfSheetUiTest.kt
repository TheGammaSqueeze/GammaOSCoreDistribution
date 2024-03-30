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
import androidx.test.uiautomator.Until
import org.junit.AfterClass
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** An instrumented test to start pairing by interacting with Nearby half sheet UI.
 *
 * To run this test directly:
 * am instrument -w -r \
 * -e class android.nearby.integration.ui.PairByNearbyHalfSheetUiTest \
 * android.nearby.integration.ui/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class PairByNearbyHalfSheetUiTest : BaseUiTest() {
    init {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Before
    fun setUp() {
        CheckNearbyHalfSheetUiTest().apply {
            setUp()
            checkNearbyHalfSheetUi()
        }
    }

    @Test
    @ScreenRecord
    fun clickConnectButton() {
        val connectButton = NearbyHalfSheetUiMap.DevicePairingFragment.connectButton
        device.findObject(connectButton).click()
        device.wait(Until.gone(connectButton), CONNECT_BUTTON_TIMEOUT_MILLS)
    }

    companion object {
        private const val CONNECT_BUTTON_TIMEOUT_MILLS = 3000L
        private lateinit var device: UiDevice

        @AfterClass
        @JvmStatic
        fun teardownClass() {
            // Cleans up after saving screenshot in TestWatcher, leaves nothing dirty behind.
            device.pressBack()
            DismissNearbyHalfSheetUiTest().dismissHalfSheet()
        }
    }
}