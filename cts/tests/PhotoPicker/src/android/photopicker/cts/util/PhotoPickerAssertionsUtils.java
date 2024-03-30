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

import static android.provider.MediaStore.PickerMediaColumns;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;

import androidx.test.InstrumentationRegistry;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Photo Picker Utility methods for test assertions.
 */
public class PhotoPickerAssertionsUtils {
    private static final String TAG = "PhotoPickerTestAssertions";

    public static void assertPickerUriFormat(Uri uri, int expectedUserId) {
        // content://media/picker/<user-id>/<media-id>
        final int userId = Integer.parseInt(uri.getPathSegments().get(1));
        assertThat(userId).isEqualTo(expectedUserId);

        final String auth = uri.getPathSegments().get(0);
        assertThat(auth).isEqualTo("picker");
    }

    public static void assertPersistedGrant(Uri uri, ContentResolver resolver) {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

        final List<UriPermission> uriPermissions = resolver.getPersistedUriPermissions();
        final List<Uri> uris = new ArrayList<>();
        for (UriPermission perm : uriPermissions) {
            if (perm.isReadPermission()) {
                uris.add(perm.getUri());
            }
        }

        assertThat(uris).contains(uri);
    }

    public static void assertMimeType(Uri uri, String expectedMimeType) throws Exception {
        final Context context = InstrumentationRegistry.getTargetContext();
        final String resultMimeType = context.getContentResolver().getType(uri);
        assertThat(resultMimeType).isEqualTo(expectedMimeType);
    }

    public static void assertRedactedReadOnlyAccess(Uri uri) throws Exception {
        assertThat(uri).isNotNull();
        final String[] projection = new String[]{ PickerMediaColumns.MIME_TYPE };
        final Context context = InstrumentationRegistry.getTargetContext();
        final ContentResolver resolver = context.getContentResolver();
        try (Cursor c = resolver.query(uri, projection, null, null)) {
            assertThat(c).isNotNull();
            assertThat(c.moveToFirst()).isTrue();

            final String mimeType = c.getString(c.getColumnIndex(PickerMediaColumns.MIME_TYPE));

            if (mimeType.startsWith("image")) {
                assertImageRedactedReadOnlyAccess(uri, resolver);
            } else if (mimeType.startsWith("video")) {
                assertVideoRedactedReadOnlyAccess(uri, resolver);
            } else {
                fail("The mime type is not as expected: " + mimeType);
            }
        }
    }

    private static void assertVideoRedactedReadOnlyAccess(Uri uri, ContentResolver resolver)
            throws Exception {
        // The location is redacted
        // TODO(b/201505595): Make this method work for test_video.mp4. Currently it works only for
        //  test_video_dng.mp4
        try (InputStream in = resolver.openInputStream(uri);
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            FileUtils.copy(in, out);
            byte[] bytes = out.toByteArray();
            byte[] xmpBytes = Arrays.copyOfRange(bytes, 3269, 3269 + 13197);
            String xmp = new String(xmpBytes);
            assertWithMessage("Failed to redact XMP longitude")
                    .that(xmp.contains("10,41.751000E")).isFalse();
            assertWithMessage("Failed to redact XMP latitude")
                    .that(xmp.contains("53,50.070500N")).isFalse();
            assertWithMessage("Redacted non-location XMP")
                    .that(xmp.contains("13166/7763")).isTrue();
        }

        // assert no write access
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "w")) {
            fail("Does not grant write access to uri " + uri.toString());
        } catch (SecurityException | FileNotFoundException expected) {
        }
    }

    private static void assertImageRedactedReadOnlyAccess(Uri uri, ContentResolver resolver)
            throws Exception {
        // Assert URI access
        // The location is redacted
        try (InputStream is = resolver.openInputStream(uri)) {
            assertImageExifRedacted(is);
        }

        // Assert no write access
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "w")) {
            fail("Does not grant write access to uri " + uri.toString());
        } catch (SecurityException | FileNotFoundException expected) {
        }

        // Assert file path access
        try (Cursor c = resolver.query(uri, null, null, null)) {
            assertThat(c).isNotNull();
            assertThat(c.moveToFirst()).isTrue();

            File file = new File(c.getString(c.getColumnIndex(PickerMediaColumns.DATA)));

            // The location is redacted
            try (InputStream is = new FileInputStream(file)) {
                assertImageExifRedacted(is);
            }

            // Assert no write access
            try (ParcelFileDescriptor pfd =
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE)) {
                fail("Does not grant write access to file " + file);
            } catch (IOException e) {
            }
        }
    }

    private static void assertImageExifRedacted(InputStream is) throws IOException {
        final ExifInterface exif = new ExifInterface(is);
        final float[] latLong = new float[2];
        exif.getLatLong(latLong);
        assertWithMessage("Failed to redact latitude")
                .that(latLong[0]).isWithin(0.001f).of(0);
        assertWithMessage("Failed to redact longitude")
                .that(latLong[1]).isWithin(0.001f).of(0);

        String xmp = exif.getAttribute(ExifInterface.TAG_XMP);
        assertWithMessage("Failed to redact XMP longitude")
                .that(xmp.contains("10,41.751000E")).isFalse();
        assertWithMessage("Failed to redact XMP latitude")
                .that(xmp.contains("53,50.070500N")).isFalse();
        assertWithMessage("Redacted non-location XMP")
                .that(xmp.contains("LensDefaults")).isTrue();
    }
}
