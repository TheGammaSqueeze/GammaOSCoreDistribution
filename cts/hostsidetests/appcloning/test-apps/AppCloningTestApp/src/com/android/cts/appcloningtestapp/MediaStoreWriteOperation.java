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

package com.android.cts.appcloningtestapp;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Calendar;

public class MediaStoreWriteOperation {

    private static final String TAG = "MediaStoreWriteOperation";
    private static final int ANDROID_Q = 29;

    // Write an image to primary external storage using MediaStore API
    public static boolean createImageFileToMediaStore(Context context, String displayName,
            Bitmap bitmap) {

        /*
           1. Find all media files on the primary external storage device
           2. Build.VERSION_CODES.Q = 29
        */
        Uri imageCollection = (Build.VERSION.SDK_INT >= ANDROID_Q)
                ? MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) :
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        // Publish a new image
        ContentValues newImageDetails = new ContentValues();
        newImageDetails.put(MediaStore.Images.Media.DISPLAY_NAME,
                displayName + "_" + Calendar.getInstance().getTime() + ".jpg");
        newImageDetails.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        newImageDetails.put(MediaStore.Images.Media.WIDTH, bitmap.getWidth());
        newImageDetails.put(MediaStore.Images.Media.HEIGHT, bitmap.getHeight());

        // Add a specific media item
        ContentResolver resolver = context.getContentResolver();

        try {
            // Keeps a handle to the new image's URI in case we need to modify it later
            Uri newImageUri = resolver.insert(imageCollection, newImageDetails);

            if (newImageUri == null) {
                throw new IOException("Couldn't create MediaStore entry");
            }

            // Now you got the URI of an image, finally save it in the MediaStore
            OutputStream outputStream = resolver.openOutputStream(newImageUri);
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                throw new IOException("Couldn't save bitmap");
            }

            outputStream.flush();
            outputStream.close();
            return true;
        } catch (IOException exception) {
            exception.printStackTrace();
            return false;
        }
    }
}
