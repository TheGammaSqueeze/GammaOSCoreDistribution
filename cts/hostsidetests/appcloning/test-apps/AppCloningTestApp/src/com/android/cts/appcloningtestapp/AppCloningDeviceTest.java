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

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.cts.install.lib.InstallUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

@RunWith(JUnit4.class)
public class AppCloningDeviceTest {
    private static final String EMPTY_STRING = "";
    private static final String TAG = "AppCloningDeviceTest";

    private Context mContext;
    private StorageManager mStorageManager;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mStorageManager = mContext.getSystemService(StorageManager.class);

        // Adopts common permission to read and write to shared storage
        InstallUtils.adoptShellPermissionIdentity(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.MANAGE_EXTERNAL_STORAGE);
    }

    @After
    public void tearDown() {
        // Drops adopted shell permissions
        InstallUtils.dropShellPermissionIdentity();
    }

    private String getTestArgumentValueForGivenKey(String testArgumentKey) {
        final Bundle testArguments = InstrumentationRegistry.getArguments();
        String testArgumentValue = testArguments.getString(testArgumentKey, EMPTY_STRING);

        return testArgumentValue;
    }

    private String getImageNameToBeDisplayed() {
        return getTestArgumentValueForGivenKey("imageNameToBeDisplayed");
    }

    private String getImageNameToBeCreated() {
        return getTestArgumentValueForGivenKey("imageNameToBeCreated");
    }

    private String getImageNameToBeVerifiedInOwnerProfile() {
        return getTestArgumentValueForGivenKey("imageNameToBeVerifiedInOwnerProfile");
    }

    private String getImageNameToBeVerifiedInCloneProfile() {
        return getTestArgumentValueForGivenKey("imageNameToBeVerifiedInCloneProfile");
    }

    private String getCloneUserId() {
        return getTestArgumentValueForGivenKey("cloneUserId");
    }

    @Test
    public void testMediaStoreManager_verifyCrossUserImagesInSharedStorage() throws Exception {
        // This method will be called only after writing images in owner and clone profile
        String imageNameToBeVerifiedInOwnerProfile = getImageNameToBeVerifiedInOwnerProfile();
        String imageNameToBeVerifiedInCloneProfile = getImageNameToBeVerifiedInCloneProfile();

        List<Image> imageList = MediaStoreReadOperation.getImageFilesFromMediaStore(mContext);
        boolean verifiedImageInOwnerProfile = false;
        boolean verifiedImageInCloneProfile = false;

        for (Image image: imageList) {

            // Eg: Data: /storage/emulated/<user_id>/Pictures/<imageName>.jpg
            // user_id will be 0 for owner
            if (!verifiedImageInOwnerProfile && image.getData().contains("/emulated/0/")
                    && image.getDisplayName().startsWith(imageNameToBeVerifiedInOwnerProfile)) {
                verifiedImageInOwnerProfile = true;
                continue;
            }

            if (!verifiedImageInCloneProfile && image.getData().contains("/emulated/"
                    + getCloneUserId() + "/")
                    && image.getDisplayName().startsWith(imageNameToBeVerifiedInCloneProfile)) {
                verifiedImageInCloneProfile = true;
            }
        }

        assertThat(verifiedImageInOwnerProfile && verifiedImageInCloneProfile).isTrue();
    }

    @Test
    public void testMediaStoreManager_writeImageToSharedStorage() throws Exception {
        String imageNameToBeCreated = getImageNameToBeCreated();

        int color = 0x00000000;
        if (imageNameToBeCreated.equalsIgnoreCase("clone_profile_image")) {
            // Blue represents clone profile image
            color = Color.BLUE;
        } else if (imageNameToBeCreated.equalsIgnoreCase("owner_profile_image")) {
            // Green represents owner profile image
            color = Color.GREEN;
        }

        Bitmap bitmap = createImage(1000, 1000, color);

        assertThat(MediaStoreWriteOperation.createImageFileToMediaStore(mContext,
                getImageNameToBeDisplayed(), bitmap)).isTrue();
    }

    /**
     * Creates a bitmap unicolor image with the given width, height and color
     * @param width
     * @param height
     * @param color
     * @return A bitmap unicolor image with the given width and height
     */
    public static Bitmap createImage(int width, int height, int color) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setColor(color);
        canvas.drawRect(0F, 0F, (float) width, (float) height, paint);
        return bitmap;
    }

    @Test
    public void testStorageManager_verifyInclusionOfSharedProfileVolumes() throws Exception {
        int cloneUserId = -1;
        try {
            cloneUserId = Integer.valueOf(getCloneUserId());
        } catch (NumberFormatException exception) {
            Log.d(TAG, "Failed to get clone user ID - " + exception.getMessage());
        }
        assertThat(cloneUserId).isNotEqualTo(-1);
        List<StorageVolume> volumeList = mStorageManager.getStorageVolumes();
        List<StorageVolume> volumeListIncludingShared =
                mStorageManager.getStorageVolumesIncludingSharedProfiles();

        // should contain all volumes of volumeList
        assertThat(volumeListIncludingShared.containsAll(volumeList)).isTrue();

        // remove volumes that belong to owner profile
        volumeListIncludingShared.removeAll(volumeList);

        assertThat(volumeListIncludingShared.size()).isGreaterThan(0);

        // remaining volumes should belong to the clone user.
        for (StorageVolume vol : volumeListIncludingShared) {
            assertThat(vol.getOwner().getIdentifier()).isEqualTo(cloneUserId);
        }
    }
}
