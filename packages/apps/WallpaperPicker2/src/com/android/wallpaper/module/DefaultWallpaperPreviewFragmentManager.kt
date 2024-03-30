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
package com.android.wallpaper.module

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.android.wallpaper.model.LiveWallpaperInfo
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.picker.ImagePreviewFragment
import com.android.wallpaper.picker.LivePreviewFragment
import com.android.wallpaper.picker.PreviewFragment

class DefaultWallpaperPreviewFragmentManager : WallpaperPreviewFragmentManager {
    override fun getPreviewFragment(
        context: Context,
        wallpaperInfo: WallpaperInfo,
        @PreviewFragment.PreviewMode mode: Int,
        viewAsHome: Boolean,
        viewFullScreen: Boolean,
        testingModeEnabled: Boolean
    ): Fragment {
        val args = Bundle()
        args.putParcelable(PreviewFragment.ARG_WALLPAPER, wallpaperInfo)
        args.putInt(PreviewFragment.ARG_PREVIEW_MODE, mode)
        args.putBoolean(PreviewFragment.ARG_VIEW_AS_HOME, viewAsHome)
        args.putBoolean(PreviewFragment.ARG_FULL_SCREEN, viewFullScreen)
        args.putBoolean(PreviewFragment.ARG_TESTING_MODE_ENABLED, testingModeEnabled)
        val fragment =
            if (wallpaperInfo is LiveWallpaperInfo) LivePreviewFragment()
            else ImagePreviewFragment()
        fragment.arguments = args
        return fragment
    }
}
