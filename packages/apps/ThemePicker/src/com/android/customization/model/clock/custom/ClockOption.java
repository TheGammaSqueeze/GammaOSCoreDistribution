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
package com.android.customization.model.clock.custom;

import android.view.View;

import com.android.customization.model.CustomizationManager;
import com.android.customization.model.CustomizationOption;
import com.android.wallpaper.R;

/**
 * {@link CustomizationOption} for a clock face.
 */
public class ClockOption implements CustomizationOption<ClockOption> {

    @Override
    public String getTitle() {
        // TODO(/b241966062) use the title from the clock metadata
        return "title";
    }

    @Override
    public void bindThumbnailTile(View view) {
        // TODO(/b241966062) bind the thumbnail
    }

    @Override
    public boolean isActive(CustomizationManager<ClockOption> manager) {
        return false;
    }


    @Override
    public int getLayoutResId() {
        return R.layout.clock_option;
    }
}
