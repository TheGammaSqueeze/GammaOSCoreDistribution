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

package android.photopicker.cts.util;

import android.app.UiAutomation;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.FileUtils;
import android.os.UserHandle;
import android.photopicker.cts.R;
import android.provider.MediaStore;
import android.provider.cts.ProviderTestUtils;
import android.provider.cts.media.MediaStoreUtils;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Photo Picker Utility methods for media files creation and deletion.
 */
public class PhotoPickerFilesUtils {
    public static final String DISPLAY_NAME_PREFIX = "ctsPhotoPicker";

    public static void createImages(int count, int userId, List<Uri> uriList)
            throws Exception {
        createImages(count, userId, uriList, false);
    }

    public static void createImages(int count, int userId, List<Uri> uriList, boolean isFavorite)
            throws Exception {
        for (int i = 0; i < count; i++) {
            final Uri uri = createImage(userId, isFavorite);
            uriList.add(uri);
            clearMediaOwner(uri, userId);
        }
        // Wait for Picker db sync to complete
        MediaStore.waitForIdle(InstrumentationRegistry.getContext().getContentResolver());
    }

    public static void createDNGVideos(int count, int userId, List<Uri> uriList)
            throws Exception {
        for (int i = 0; i < count; i++) {
            final Uri uri = createDNGVideo(userId);
            uriList.add(uri);
            clearMediaOwner(uri, userId);
        }
        // Wait for Picker db sync to complete
        MediaStore.waitForIdle(InstrumentationRegistry.getContext().getContentResolver());
    }

    public static void createVideos(int count, int userId, List<Uri> uriList)
            throws Exception {
        for (int i = 0; i < count; i++) {
            final Uri uri = createVideo(userId);
            uriList.add(uri);
            clearMediaOwner(uri, userId);
        }
        // Wait for Picker db sync to complete
        MediaStore.waitForIdle(InstrumentationRegistry.getContext().getContentResolver());
    }

    public static void deleteMedia(Uri uri, Context context) throws Exception {
        try {
            ProviderTestUtils.setOwner(uri, context.getPackageName());
            context.getContentResolver().delete(uri, Bundle.EMPTY);
        } catch (Exception ignored) {
        }
    }

    private static void clearMediaOwner(Uri uri, int userId) throws Exception {
        final String cmd = String.format(
                "content update --uri %s --user %d --bind owner_package_name:n:", uri, userId);
        ShellUtils.runShellCommand(cmd);
    }

    private static Uri createDNGVideo(int userId) throws Exception {
        final Uri uri = stageMedia(R.raw.test_video_dng,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video/mp4", userId);
        return uri;
    }

    private static Uri createVideo(int userId) throws Exception {
        final Uri uri = stageMedia(R.raw.test_video,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video/mp4", userId);
        return uri;
    }

    private static Uri createImage(int userId, boolean isFavorite) throws Exception {
        final Uri uri = stageMedia(R.raw.lg_g4_iso_800_jpg,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/jpeg", userId, isFavorite);
        return uri;
    }

    private static Uri stageMedia(int resId, Uri collectionUri, String mimeType, int userId,
            boolean isFavorite) throws
            Exception {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity(
                android.Manifest.permission.INTERACT_ACROSS_USERS,
                android.Manifest.permission.INTERACT_ACROSS_USERS_FULL);
        try {
            final Context context = InstrumentationRegistry.getTargetContext();
            final Context userContext = userId == context.getUserId() ? context :
                    context.createPackageContextAsUser("android", /* flags= */ 0,
                            UserHandle.of(userId));
            return stageMedia(resId, collectionUri, mimeType, userContext, isFavorite);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private static Uri stageMedia(int resId, Uri collectionUri, String mimeType, int userId) throws
            Exception {
        return stageMedia(resId, collectionUri, mimeType, userId, false);
    }

    private static Uri stageMedia(int resId, Uri collectionUri, String mimeType, Context context,
            boolean isFavorite)
            throws IOException {
        final String displayName = DISPLAY_NAME_PREFIX + System.nanoTime();
        final MediaStoreUtils.PendingParams params = new MediaStoreUtils.PendingParams(
                collectionUri, displayName, mimeType);
        params.setIsFavorite(isFavorite);
        final Uri pendingUri = MediaStoreUtils.createPending(context, params);
        try (MediaStoreUtils.PendingSession session = MediaStoreUtils.openPending(context,
                pendingUri)) {
            try (InputStream source = context.getResources().openRawResource(resId);
                 OutputStream target = session.openOutputStream()) {
                FileUtils.copy(source, target);
            }
            return session.publish();
        }
    }
}
