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
package com.android.customization.model.color;

import static com.google.common.truth.Truth.assertThat;

import androidx.appcompat.app.AppCompatActivity;

import com.android.wallpaper.model.WallpaperColorsViewModel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests of {@link ColorSectionController}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class ColorSectionControllerTest {

    private AppCompatActivity mActivity;
    private ColorSectionController mColorSectionController;

    /**
     * Set up the test case.
     */
    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mActivity = Robolectric.buildActivity(AppCompatActivity.class).create().get();
        mColorSectionController = new ColorSectionController(mActivity,
                new WallpaperColorsViewModel(), mActivity, null);
    }

    /**
     * isAvailable()'s test.
     */
    @Test
    @Config(manifest = Config.NONE)
    public void isAvailable_nullContext_shouldReturnFalse() {
        assertThat(mColorSectionController.isAvailable(/* context= */ null)).isFalse();
    }
}

