/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.wallpaper.module;

import static android.app.WallpaperManager.FLAG_LOCK;

import android.app.WallpaperManager;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.android.wallpaper.compat.WallpaperManagerCompat;

import java.io.IOException;

/**
 * Default implementation of {@link WallpaperStatusChecker}.
 */
public final class DefaultWallpaperStatusChecker implements WallpaperStatusChecker {

    private static final String TAG = "DefaultWallpaperStatusChecker";

    @Override
    public boolean isHomeStaticWallpaperSet(Context context) {
        ParcelFileDescriptor systemWallpaperFile =
                InjectorProvider.getInjector().getWallpaperManagerCompat(context).getWallpaperFile(
                        WallpaperManagerCompat.FLAG_SYSTEM);
        boolean isStaticWallpaperSet = systemWallpaperFile != null;

        if (systemWallpaperFile != null) {
            try {
                systemWallpaperFile.close();
            } catch (IOException e) {
                Log.e(TAG, "Unable to close system wallpaper ParcelFileDescriptor", e);
            }
        }

        return isStaticWallpaperSet;
    }

    @Override
    public boolean isLockWallpaperSet(Context context) {
        return WallpaperManager.getInstance(context).getWallpaperId(FLAG_LOCK) > 0;
    }
}
