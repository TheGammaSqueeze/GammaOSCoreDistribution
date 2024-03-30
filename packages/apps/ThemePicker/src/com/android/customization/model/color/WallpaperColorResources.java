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

import android.app.WallpaperColors;
import android.content.Context;
import android.util.SparseIntArray;
import android.widget.RemoteViews.ColorResources;

import com.android.systemui.monet.ColorScheme;
import com.android.systemui.monet.TonalPalette;

/** A class to override colors in a {@link Context} with wallpaper colors. */
public class WallpaperColorResources {

    private final SparseIntArray mColorOverlay = new SparseIntArray();

    public WallpaperColorResources(WallpaperColors wallpaperColors) {
        ColorScheme wallpaperColorScheme = new ColorScheme(wallpaperColors, /* darkTheme= */ false);
        addOverlayColor(wallpaperColorScheme.getNeutral1(), android.R.color.system_neutral1_10);
        addOverlayColor(wallpaperColorScheme.getNeutral2(), android.R.color.system_neutral2_10);
        addOverlayColor(wallpaperColorScheme.getAccent1(), android.R.color.system_accent1_10);
        addOverlayColor(wallpaperColorScheme.getAccent2(), android.R.color.system_accent2_10);
        addOverlayColor(wallpaperColorScheme.getAccent3(), android.R.color.system_accent3_10);
    }

    /** Applies the wallpaper color resources to the {@code context}. */
    public void apply(Context context) {
        ColorResources.create(context, mColorOverlay).apply(context);
    }

    private void addOverlayColor(TonalPalette colorSchemehue, int firstResourceColorId) {
        int resourceColorId = firstResourceColorId;
        for (int color : colorSchemehue.getAllShades()) {
            mColorOverlay.put(resourceColorId, color);
            resourceColorId++;
        }
    }
}
