/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.wallpaper.model;

import android.app.WallpaperInfo;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * Live wallpaper-specific wrapper for user-facing wallpaper metadata.
 */
public class LiveWallpaperMetadata extends WallpaperMetadata {
    public LiveWallpaperMetadata(android.app.WallpaperInfo wallpaperComponent) {
        super(null, null, 0, 0, null, null, wallpaperComponent);
    }

    @Override
    public List<String> getAttributions() {
        throw new UnsupportedOperationException("Not implemented for live wallpapers");
    }

    @Override
    public String getActionUrl() {
        throw new UnsupportedOperationException("Not implemented for live wallpapers");
    }

    @Override
    public int getActionLabelRes() {
        throw new UnsupportedOperationException("Not implemented for live wallpapers");
    }

    @Override
    public int getActionIconRes() {
        throw new UnsupportedOperationException("Not implemented for live wallpapers");
    }

    @Override
    public String getCollectionId() {
        throw new UnsupportedOperationException("Not implemented for live wallpapers");
    }

    @Nullable
    @Override
    public String getBackingFileName() {
        throw new UnsupportedOperationException("Not implemented for live wallpapers");
    }

    @Override
    public WallpaperInfo getWallpaperComponent() {
        return mWallpaperComponent;
    }
}
