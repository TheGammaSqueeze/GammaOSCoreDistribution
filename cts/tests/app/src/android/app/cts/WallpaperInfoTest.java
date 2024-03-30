/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.app.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.WallpaperInfo;
import android.app.stubs.LiveWallpaper;
import android.app.stubs.LiveWallpaperNoUnfoldTransition;
import android.app.stubs.R;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.service.wallpaper.WallpaperService;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class WallpaperInfoTest {

    @Test
    public void test_wallpaperServiceQuery() {
        Context context = InstrumentationRegistry.getTargetContext();
        Intent intent = new Intent(WallpaperService.SERVICE_INTERFACE);
        intent.setPackage("android.app.stubs");
        PackageManager pm = context.getPackageManager();

        List<ResolveInfo> result = pm.queryIntentServices(intent, PackageManager.GET_META_DATA);

        assertEquals(2, result.size());
    }

    @Test
    public void test_wallpaperInfoOptions() {
        Context context = InstrumentationRegistry.getTargetContext();
        PackageManager pm = context.getPackageManager();

        WallpaperInfo wallpaperInfo = getInfoForService(LiveWallpaper.class);

        assertEquals(context.getString(R.string.wallpaper_title), wallpaperInfo.loadLabel(pm));
        assertEquals(context.getString(R.string.wallpaper_description),
            wallpaperInfo.loadDescription(pm));
        assertEquals(context.getString(R.string.wallpaper_collection),
            wallpaperInfo.loadAuthor(pm));
        assertEquals(context.getString(R.string.wallpaper_context),
            wallpaperInfo.loadContextDescription(pm));
        assertEquals(context.getString(R.string.wallpaper_context_uri),
            wallpaperInfo.loadContextUri(pm).toString());
        assertEquals(context.getString(R.string.wallpaper_slice_uri),
            wallpaperInfo.getSettingsSliceUri().toString());
        assertTrue(wallpaperInfo.getShowMetadataInPreview());
        assertTrue(wallpaperInfo.supportsMultipleDisplays());
        assertTrue(wallpaperInfo.shouldUseDefaultUnfoldTransition());
        assertNotNull(wallpaperInfo.loadIcon(pm));
        assertNotNull(wallpaperInfo.loadThumbnail(pm));
    }

    @Test
    public void test_defaultUnfoldTransitionDisabled() {
        WallpaperInfo wallpaperInfo = getInfoForService(LiveWallpaperNoUnfoldTransition.class);

        assertFalse(wallpaperInfo.shouldUseDefaultUnfoldTransition());
    }

    private <T extends WallpaperService> WallpaperInfo getInfoForService(Class<T> service) {
        Context context = InstrumentationRegistry.getTargetContext();
        Intent intent = new Intent(WallpaperService.SERVICE_INTERFACE);
        intent.setPackage("android.app.stubs");
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> result = pm.queryIntentServices(intent, PackageManager.GET_META_DATA);

        ResolveInfo info = null;
        for (int i = 0; i < result.size(); i++) {
            ResolveInfo resolveInfo = result.get(i);
            if (resolveInfo.serviceInfo.name.equals(service.getName())) {
                info = resolveInfo;
                break;
            }
        }

        if (info == null) {
            throw new AssertionError(service.getName() + " was not found in the queried "
                    + "wallpaper services list " + result);
        }

        try {
            return new WallpaperInfo(context, info);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
