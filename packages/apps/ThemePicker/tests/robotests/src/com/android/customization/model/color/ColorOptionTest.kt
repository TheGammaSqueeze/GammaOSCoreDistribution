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

import com.android.customization.model.color.ColorOptionsProvider.COLOR_SOURCE_HOME
import com.android.customization.model.color.ColorOptionsProvider.COLOR_SOURCE_LOCK
import com.android.customization.model.color.ColorOptionsProvider.COLOR_SOURCE_PRESET
import com.android.systemui.monet.Style
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.robolectric.RobolectricTestRunner

/** Tests of {@link ColorOption}. */
@RunWith(RobolectricTestRunner::class)
class ColorOptionTest {

    @get:Rule val rule: MockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var manager: ColorCustomizationManager

    @Test
    fun colorOption_Source_Preset() {
        val bundleOption: ColorOption =
            ColorBundle(
                "fake color",
                mapOf("fake_package" to "fake_color"),
                false,
                null,
                /* index= */ 0,
                null
            )
        assertEquals(COLOR_SOURCE_PRESET, bundleOption.source)
    }

    @Test
    fun colorOption_bundle_index() {
        testBundleOptionIndex(1)
        testBundleOptionIndex(2)
        testBundleOptionIndex(3)
        testBundleOptionIndex(4)
    }

    private fun testBundleOptionIndex(index: Int) {
        val bundleOption: ColorBundle =
            ColorBundle(
                "fake color",
                mapOf("fake_package" to "fake_color"),
                false,
                null,
                /* index= */ index,
                null
            )
        assertThat(bundleOption.index).isEqualTo(index)
    }

    @Test
    fun colorOption_Source_Seed() {
        testSeedOptionSource(COLOR_SOURCE_HOME)
        testSeedOptionSource(COLOR_SOURCE_LOCK)
    }

    private fun testSeedOptionSource(source: String) {
        val seedOption: ColorOption =
            ColorSeedOption(
                "fake color",
                mapOf("fake_package" to "fake_color"),
                false,
                source,
                null,
                /* index= */ 0,
                null
            )
        assertThat(seedOption.source).isEqualTo(source)
    }

    @Test
    fun colorOption_seed_style() {
        testSeedOptionStyle(Style.TONAL_SPOT)
        testSeedOptionStyle(Style.SPRITZ)
        testSeedOptionStyle(Style.VIBRANT)
        testSeedOptionStyle(Style.EXPRESSIVE)
    }

    private fun testSeedOptionStyle(style: Style) {
        val seedOption: ColorOption =
            ColorSeedOption(
                "fake color",
                mapOf("fake_package" to "fake_color"),
                /* isDefault= */ false,
                "fake_source",
                style,
                0,
                null
            )
        assertThat(seedOption.style).isEqualTo(style)
    }

    @Test
    fun colorOption_seed_index() {
        testSeedOptionIndex(1)
        testSeedOptionIndex(2)
        testSeedOptionIndex(3)
        testSeedOptionIndex(4)
    }

    private fun testSeedOptionIndex(index: Int) {
        val seedOption: ColorOption =
            ColorSeedOption(
                "fake color",
                mapOf("fake_package" to "fake_color"),
                /* isDefault= */ false,
                "fake_source",
                Style.TONAL_SPOT,
                index,
                /* previewInfo= */ null
            )
        assertThat(seedOption.index).isEqualTo(index)
    }

    private fun setUpSeedOption(
        isDefault: Boolean,
        source: String = "some_source"
    ): ColorSeedOption {
        val overlays =
            if (isDefault) {
                HashMap()
            } else {
                mapOf("package" to "value", "otherPackage" to "otherValue")
            }
        `when`(manager.currentOverlays).thenReturn(overlays)
        return ColorSeedOption(
            "seed",
            overlays,
            isDefault,
            source,
            Style.TONAL_SPOT,
            /* index= */ 0,
            /* previewInfo= */ null
        )
    }

    @Test
    fun seedOption_isActive_notDefault_SourceSet() {
        val source = "some_source"
        val seedOption = setUpSeedOption(false, source)
        `when`(manager.currentColorSource).thenReturn(source)

        assertThat(seedOption.isActive(manager)).isTrue()
    }

    @Test
    fun seedOption_isActive_notDefault_NoSource() {
        val seedOption = setUpSeedOption(false)
        `when`(manager.currentColorSource).thenReturn(null)

        assertThat(seedOption.isActive(manager)).isTrue()
    }

    @Test
    fun seedOption_isActive_notDefault_differentSource() {
        val seedOption = setUpSeedOption(false)
        `when`(manager.currentColorSource).thenReturn("some_other_source")

        assertThat(seedOption.isActive(manager)).isFalse()
    }

    @Test
    fun seedOption_isActive_default_emptyJson() {
        val seedOption = setUpSeedOption(true)
        `when`(manager.storedOverlays).thenReturn("")

        assertThat(seedOption.isActive(manager)).isTrue()
    }

    @Test
    fun seedOption_isActive_default_nonEmptyJson() {
        val seedOption = setUpSeedOption(true)

        `when`(manager.storedOverlays).thenReturn("{non-empty-json}")

        // Should still be Active because overlays is empty
        assertThat(seedOption.isActive(manager)).isTrue()
    }

    @Test
    @Ignore("b/260925899")
    fun seedOption_isActive_default_nonEmptyOverlays() {
        val seedOption = setUpSeedOption(true)

        `when`(manager.currentOverlays).thenReturn(mapOf("a" to "b"))
        // TODO(b/222433744): failing as it's true
        assertThat(seedOption.isActive(manager)).isFalse()
    }
}
