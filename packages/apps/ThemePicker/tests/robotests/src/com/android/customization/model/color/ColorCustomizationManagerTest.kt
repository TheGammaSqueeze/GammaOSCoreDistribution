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
package com.android.customization.model.color

import android.app.WallpaperColors
import android.graphics.Color
import com.android.customization.model.CustomizationManager
import com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_COLOR
import com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_SYSTEM_PALETTE
import com.android.customization.model.color.ColorOptionsProvider.COLOR_SOURCE_HOME
import com.android.customization.model.color.ColorOptionsProvider.COLOR_SOURCE_PRESET
import com.android.customization.model.color.ColorOptionsProvider.OVERLAY_COLOR_BOTH
import com.android.customization.model.color.ColorOptionsProvider.OVERLAY_COLOR_INDEX
import com.android.customization.model.color.ColorOptionsProvider.OVERLAY_COLOR_SOURCE
import com.android.customization.model.theme.OverlayManagerCompat
import com.android.systemui.monet.Style
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Tests of {@link ColorCustomizationManager}. */
// TODO(b/222433744): most of these tests are failing due to the manager apk missing in the image
@RunWith(RobolectricTestRunner::class)
class ColorCustomizationManagerTest {

    @get:Rule val rule: MockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var provider: ColorOptionsProvider
    @Mock private lateinit var mockOM: OverlayManagerCompat

    private lateinit var manager: ColorCustomizationManager

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        val application = RuntimeEnvironment.application
        manager = ColorCustomizationManager(provider, application.contentResolver, mockOM)
    }

    @Test
    @Ignore("b/260925899")
    fun testParseSettings() {
        val source = COLOR_SOURCE_HOME
        val style = Style.SPRITZ
        val someColor = "aabbcc"
        val someOtherColor = "bbccdd"
        val settings =
            mapOf(
                OVERLAY_CATEGORY_SYSTEM_PALETTE to someColor,
                OVERLAY_CATEGORY_COLOR to someOtherColor,
                OVERLAY_COLOR_SOURCE to source,
                ColorOption.TIMESTAMP_FIELD to "12345"
            )
        val json = JSONObject(settings).toString()

        manager.parseSettings(json)

        assertThat(manager.currentColorSource).isEqualTo(source)
        assertThat(manager.currentStyle).isEqualTo(style)
        assertThat(manager.currentOverlays.size).isEqualTo(2)
        assertThat(manager.currentOverlays.get(OVERLAY_CATEGORY_COLOR)).isEqualTo(someOtherColor)
        assertThat(manager.currentOverlays.get(OVERLAY_CATEGORY_SYSTEM_PALETTE))
            .isEqualTo(someColor)
    }

    @Test
    @Ignore("b/260925899")
    fun apply_ColorBundle_index() {
        testApplyColorBundle(1, "1")
        testApplyColorBundle(2, "2")
        testApplyColorBundle(3, "3")
        testApplyColorBundle(4, "4")
    }

    private fun testApplyColorBundle(index: Int, value: String) {
        manager.apply(
            getColorBundle(index),
            object : CustomizationManager.Callback {
                override fun onSuccess() {}
                override fun onError(throwable: Throwable?) {}
            }
        )

        val overlaysJson = JSONObject(manager.storedOverlays)

        assertThat(overlaysJson.getString(OVERLAY_COLOR_INDEX)).isEqualTo(value)
    }

    private fun getColorBundle(index: Int): ColorBundle {
        return ColorBundle(
            "fake color",
            mapOf("fake_package" to "fake_color"),
            /* isDefault= */ false,
            null,
            /* index= */ index,
            null
        )
    }

    @Test
    @Ignore("b/260925899")
    fun apply_ColorSeed_index() {
        testApplyColorSeed(1, "1")
        testApplyColorSeed(2, "2")
        testApplyColorSeed(3, "3")
        testApplyColorSeed(4, "4")
    }

    private fun testApplyColorSeed(index: Int, value: String) {
        manager.apply(
            getColorSeed(index),
            object : CustomizationManager.Callback {
                override fun onSuccess() {}
                override fun onError(throwable: Throwable?) {}
            }
        )

        val overlaysJson = JSONObject(manager.storedOverlays)
        assertThat(overlaysJson.getString(OVERLAY_COLOR_INDEX)).isEqualTo(value)
    }

    private fun getColorSeed(index: Int): ColorSeedOption {
        return ColorSeedOption(
            "fake color",
            mapOf("fake_package" to "fake_color"),
            /* isDefault= */ false,
            COLOR_SOURCE_PRESET,
            null,
            index,
            null
        )
    }

    @Test
    @Ignore("b/260925899")
    fun testApply_colorSeedFromWallpaperBoth_shouldReturnBothValue() {
        val wallpaperColor = WallpaperColors(Color.valueOf(Color.RED), null, null)
        manager.setWallpaperColors(wallpaperColor, wallpaperColor)

        manager.apply(
            getColorSeed(anyInt()),
            object : CustomizationManager.Callback {
                override fun onSuccess() {}
                override fun onError(throwable: Throwable?) {}
            }
        )

        val overlaysJson = JSONObject(manager.storedOverlays)
        assertThat(overlaysJson.getString(OVERLAY_COLOR_BOTH)).isEqualTo("1")
    }

    @Test
    @Ignore("b/260925899")
    fun testApply_colorSeedFromWallpaperDifferent_shouldReturnNonBothValue() {
        val wallpaperColor1 = WallpaperColors(Color.valueOf(Color.RED), null, null)
        val wallpaperColor2 = WallpaperColors(Color.valueOf(Color.BLUE), null, null)
        manager.setWallpaperColors(wallpaperColor1, wallpaperColor2)

        manager.apply(
            getColorSeed(anyInt()),
            object : CustomizationManager.Callback {
                override fun onSuccess() {}
                override fun onError(throwable: Throwable?) {}
            }
        )

        val overlaysJson = JSONObject(manager.storedOverlays)
        assertThat(overlaysJson.getString(OVERLAY_COLOR_BOTH)).isEqualTo("0")
    }
}
