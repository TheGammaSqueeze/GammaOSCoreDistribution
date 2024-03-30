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

import android.content.Context
import android.os.Bundle
import android.platform.test.rule.ScreenRecordRule.ScreenRecord
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.android.server.nearby.common.eventloop.EventLoop
import com.android.server.nearby.common.locator.Locator
import com.android.server.nearby.common.locator.LocatorContextWrapper
import com.android.server.nearby.fastpair.FastPairController
import com.android.server.nearby.fastpair.cache.FastPairCacheManager
import com.android.server.nearby.fastpair.footprint.FootprintsDeviceManager
import com.android.server.nearby.fastpair.halfsheet.FastPairHalfSheetManager
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.AfterClass
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import service.proto.Cache
import service.proto.FastPairString.FastPairStrings
import java.time.Clock

/** An instrumented test to check Nearby half sheet UI showed correctly.
 *
 * To run this test directly:
 * am instrument -w -r \
 * -e class android.nearby.integration.ui.CheckNearbyHalfSheetUiTest \
 * android.nearby.integration.ui/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class CheckNearbyHalfSheetUiTest : BaseUiTest() {
    private var waitHalfSheetPopupTimeoutMs: Long
    private var halfSheetTitleText: String
    private var halfSheetSubtitleText: String

    init {
        val arguments: Bundle = InstrumentationRegistry.getArguments()
        waitHalfSheetPopupTimeoutMs = arguments.getLong(
            WAIT_HALF_SHEET_POPUP_TIMEOUT_KEY,
            DEFAULT_WAIT_HALF_SHEET_POPUP_TIMEOUT_MS
        )
        halfSheetTitleText =
            arguments.getString(HALF_SHEET_TITLE_KEY, DEFAULT_HALF_SHEET_TITLE_TEXT)
        halfSheetSubtitleText =
            arguments.getString(HALF_SHEET_SUBTITLE_KEY, DEFAULT_HALF_SHEET_SUBTITLE_TEXT)

        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    /** For multidevice test snippet only. Force overwrites the test arguments. */
    fun updateTestArguments(
        waitHalfSheetPopupTimeoutSeconds: Int,
        halfSheetTitleText: String,
        halfSheetSubtitleText: String
    ) {
        this.waitHalfSheetPopupTimeoutMs = waitHalfSheetPopupTimeoutSeconds * 1000L
        this.halfSheetTitleText = halfSheetTitleText
        this.halfSheetSubtitleText = halfSheetSubtitleText
    }

    @Before
    fun setUp() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val locator = Locator(appContext).apply {
            overrideBindingForTest(EventLoop::class.java, EventLoop.newInstance("test"))
            overrideBindingForTest(
                FastPairCacheManager::class.java,
                FastPairCacheManager(appContext)
            )
            overrideBindingForTest(FootprintsDeviceManager::class.java, FootprintsDeviceManager())
            overrideBindingForTest(Clock::class.java, Clock.systemDefaultZone())
        }
        val locatorContextWrapper = LocatorContextWrapper(appContext, locator)
        locator.overrideBindingForTest(
            FastPairController::class.java,
            FastPairController(locatorContextWrapper)
        )
        val scanFastPairStoreItem = Cache.ScanFastPairStoreItem.newBuilder()
            .setDeviceName(DEFAULT_HALF_SHEET_TITLE_TEXT)
            .setFastPairStrings(
                FastPairStrings.newBuilder()
                    .setInitialPairingDescription(DEFAULT_HALF_SHEET_SUBTITLE_TEXT).build()
            )
            .build()
        FastPairHalfSheetManager(locatorContextWrapper).showHalfSheet(scanFastPairStoreItem)
    }

    @Test
    @ScreenRecord
    fun checkNearbyHalfSheetUi() {
        // Check Nearby half sheet showed by checking button "Connect" on the DevicePairingFragment.
        val isConnectButtonShowed = device.wait(
            Until.hasObject(NearbyHalfSheetUiMap.DevicePairingFragment.connectButton),
            waitHalfSheetPopupTimeoutMs
        )
        assertWithMessage("Nearby half sheet didn't show within $waitHalfSheetPopupTimeoutMs ms.")
            .that(isConnectButtonShowed).isTrue()

        val halfSheetTitle =
            device.findObject(NearbyHalfSheetUiMap.DevicePairingFragment.halfSheetTitle)
        assertThat(halfSheetTitle).isNotNull()
        assertThat(halfSheetTitle.text).isEqualTo(halfSheetTitleText)

        val halfSheetSubtitle =
            device.findObject(NearbyHalfSheetUiMap.DevicePairingFragment.halfSheetSubtitle)
        assertThat(halfSheetSubtitle).isNotNull()
        assertThat(halfSheetSubtitle.text).isEqualTo(halfSheetSubtitleText)

        val deviceImage = device.findObject(NearbyHalfSheetUiMap.DevicePairingFragment.deviceImage)
        assertThat(deviceImage).isNotNull()

        val infoButton = device.findObject(NearbyHalfSheetUiMap.DevicePairingFragment.infoButton)
        assertThat(infoButton).isNotNull()
    }

    companion object {
        private const val DEFAULT_WAIT_HALF_SHEET_POPUP_TIMEOUT_MS = 30 * 1000L
        private const val DEFAULT_HALF_SHEET_TITLE_TEXT = "Fast Pair Provider Simulator"
        private const val DEFAULT_HALF_SHEET_SUBTITLE_TEXT = "Fast Pair Provider Simulator will " +
                "appear on devices linked with nearby-mainline-fpseeker@google.com"
        private const val WAIT_HALF_SHEET_POPUP_TIMEOUT_KEY = "WAIT_HALF_SHEET_POPUP_TIMEOUT_MS"
        private const val HALF_SHEET_TITLE_KEY = "HALF_SHEET_TITLE"
        private const val HALF_SHEET_SUBTITLE_KEY = "HALF_SHEET_SUBTITLE"
        private lateinit var device: UiDevice

        @AfterClass
        @JvmStatic
        fun teardownClass() {
            // Cleans up after saving screenshot in TestWatcher, leaves nothing dirty behind.
            DismissNearbyHalfSheetUiTest().dismissHalfSheet()
        }
    }
}