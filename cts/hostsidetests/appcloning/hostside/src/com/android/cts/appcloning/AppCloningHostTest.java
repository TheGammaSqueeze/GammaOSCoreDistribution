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

package com.android.cts.appcloning;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.AppModeFull;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Runs the AppCloning tests.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public class AppCloningHostTest extends AppCloningBaseHostTest {

    private static final int CLONE_PROFILE_DIRECTORY_CREATION_TIMEOUT_MS = 20000;
    private static final int CLONE_PROFILE_MEDIA_PROVIDER_OPERATION_TIMEOUT_MS = 30000;
    private static final int CONTENT_PROVIDER_SETUP_TIMEOUT_MS = 50000;

    private static final String IMAGE_NAME_TO_BE_CREATED_KEY = "imageNameToBeCreated";
    private static final String IMAGE_NAME_TO_BE_DISPLAYED_KEY = "imageNameToBeDisplayed";
    private static final String EXTERNAL_STORAGE_PATH = "/storage/emulated/%d/";
    private static final String IMAGE_NAME_TO_BE_VERIFIED_IN_OWNER_PROFILE_KEY =
            "imageNameToBeVerifiedInOwnerProfile";
    private static final String IMAGE_NAME_TO_BE_VERIFIED_IN_CLONE_PROFILE_KEY =
            "imageNameToBeVerifiedInCloneProfile";
    private static final String CLONE_USER_ID = "cloneUserId";
    private static final String MEDIA_PROVIDER_IMAGES_PATH = "/external/images/media/";
    private static final String CLONE_DIRECTORY_CREATION_FAILURE =
            "Failed to setup and user clone directories";

    /**
     * To help avoid flaky tests, give ourselves a unique nonce to be used for
     * all filesystem paths, so that we don't risk conflicting with previous
     * test runs.
     */
    private static final String NONCE = String.valueOf(System.nanoTime());

    private String mCloneUserStoragePath;

    @Before
    public void setup() throws Exception {
        super.baseHostSetup();
        mCloneUserStoragePath = String.format(EXTERNAL_STORAGE_PATH,
                Integer.parseInt(mCloneUserId));
    }

    @After
    public void tearDown() throws Exception {
        super.baseHostTeardown();
    }

    @Test
    public void testCreateCloneUserFile() throws Exception {
        // When we use ITestDevice APIs, they take care of setting up the TradefedContentProvider.
        // TradefedContentProvider has INTERACT_ACROSS_USERS permission which allows it to access
        // clone user's storage as well
        // We retry in all the calls below to overcome the ContentProvider setup issues we sometimes
        // run into. With a retry, the setup usually succeeds.

        Integer mCloneUserIdInt = Integer.parseInt(mCloneUserId);
        // Check that the clone user directories have been created
        eventually(() -> mDevice.doesFileExist(mCloneUserStoragePath, mCloneUserIdInt),
                CLONE_PROFILE_DIRECTORY_CREATION_TIMEOUT_MS,
                CLONE_DIRECTORY_CREATION_FAILURE);

        File tmpFile = FileUtil.createTempFile("tmpFileToPush" + NONCE, ".txt");
        String filePathOnClone = mCloneUserStoragePath + tmpFile.getName();
        try {
            eventually(() -> mDevice.pushFile(tmpFile, filePathOnClone),
                    CLONE_PROFILE_DIRECTORY_CREATION_TIMEOUT_MS,
                    CLONE_DIRECTORY_CREATION_FAILURE);

            eventually(() -> mDevice.doesFileExist(filePathOnClone, mCloneUserIdInt),
                    CLONE_PROFILE_DIRECTORY_CREATION_TIMEOUT_MS,
                    CLONE_DIRECTORY_CREATION_FAILURE);

            mDevice.deleteFile(filePathOnClone);
        } finally {
            tmpFile.delete();
        }
    }

    /**
     * Once the clone profile is removed, the storage directory is deleted and the media provider
     * should be cleaned of any media files associated with clone profile.
     * This test ensures that with removal of clone profile there are no stale reference in
     * media provider for media files related to clone profile.
     * @throws Exception
     */
    @Test
    public void testRemoveClonedProfileMediaProviderCleanup() throws Exception {
        assumeTrue(isAtLeastT());

        String cloneProfileImage = NONCE + "cloneProfileImage.png";

        // Inserting blank image in clone profile
        eventually(() -> {
            assertThat(isSuccessful(
                    runContentProviderCommand("insert", mCloneUserId,
                            MEDIA_PROVIDER_URL, MEDIA_PROVIDER_IMAGES_PATH,
                            String.format("--bind _data:s:/storage/emulated/%s/Pictures/%s",
                                    mCloneUserId, cloneProfileImage),
                            String.format("--bind _user_id:s:%s", mCloneUserId)))).isTrue();
            //Querying to see if image was successfully inserted
            CommandResult queryResult = runContentProviderCommand("query", mCloneUserId,
                    MEDIA_PROVIDER_URL, MEDIA_PROVIDER_IMAGES_PATH,
                    "--projection _id",
                    String.format("--where \"_display_name=\\'%s\\'\"", cloneProfileImage));
            assertThat(isSuccessful(queryResult)).isTrue();
            assertThat(queryResult.getStdout()).doesNotContain("No result found.");
        }, CLONE_PROFILE_MEDIA_PROVIDER_OPERATION_TIMEOUT_MS);


        //Removing the clone profile
        eventually(() -> {
            assertThat(isSuccessful(executeShellV2Command("pm remove-user %s", mCloneUserId)))
                    .isTrue();
        }, CLONE_PROFILE_MEDIA_PROVIDER_OPERATION_TIMEOUT_MS);

        //Checking that added image should not be available in share media provider
        try {
            eventually(() -> {
                CommandResult queryResult = runContentProviderCommand("query",
                        String.valueOf(getCurrentUserId()),
                        MEDIA_PROVIDER_URL, MEDIA_PROVIDER_IMAGES_PATH,
                        "--projection _id",
                        String.format("--where \"_display_name=\\'%s\\'\"", cloneProfileImage));
                assertThat(isSuccessful(queryResult)).isTrue();
                assertThat(queryResult.getStdout()).contains("No result found.");
            }, CLONE_PROFILE_MEDIA_PROVIDER_OPERATION_TIMEOUT_MS);
        } catch (Exception exception) {
            //If the image is available i.e. test have failed, delete the added user
            runContentProviderCommand("delete", String.valueOf(getCurrentUserId()),
                    MEDIA_PROVIDER_URL, MEDIA_PROVIDER_IMAGES_PATH,
                    String.format("--where \"_display_name=\\'%s\\'\"", cloneProfileImage));
            throw exception;
        }
    }

    @Test
    public void testPrivateAppDataDirectoryForCloneUser() throws Exception {
        // Install the app in clone user space
        installPackage(APP_A, "--user " + Integer.valueOf(mCloneUserId));

        eventually(() -> {
            // Wait for finish.
            assertThat(isPackageInstalled(APP_A_PACKAGE, mCloneUserId)).isTrue();
        }, CLONE_PROFILE_DIRECTORY_CREATION_TIMEOUT_MS);
    }

    @Test
    public void testCrossUserMediaAccess() throws Exception {
        assumeTrue(isAtLeastT());

        // Install the app in both the user spaces
        installPackage(APP_A, "--user all");

        int currentUserId = getCurrentUserId();

        // Run save image test in owner user space
        Map<String, String> ownerArgs = new HashMap<>();
        ownerArgs.put(IMAGE_NAME_TO_BE_DISPLAYED_KEY, "WeirdOwnerProfileImage");
        ownerArgs.put(IMAGE_NAME_TO_BE_CREATED_KEY, "owner_profile_image");

        runDeviceTestAsUserInPkgA("testMediaStoreManager_writeImageToSharedStorage",
                currentUserId, ownerArgs);

        // Run save image test in clone user space
        Map<String, String> cloneArgs = new HashMap<>();
        cloneArgs.put(IMAGE_NAME_TO_BE_DISPLAYED_KEY, "WeirdCloneProfileImage");
        cloneArgs.put(IMAGE_NAME_TO_BE_CREATED_KEY, "clone_profile_image");

        runDeviceTestAsUserInPkgA("testMediaStoreManager_writeImageToSharedStorage",
                Integer.valueOf(mCloneUserId), cloneArgs);

        // Run cross user access test
        Map<String, String> args = new HashMap<>();
        args.put(IMAGE_NAME_TO_BE_VERIFIED_IN_OWNER_PROFILE_KEY, "WeirdOwnerProfileImage");
        args.put(IMAGE_NAME_TO_BE_VERIFIED_IN_CLONE_PROFILE_KEY, "WeirdCloneProfileImage");
        args.put(CLONE_USER_ID, mCloneUserId);

        // From owner user space
        runDeviceTestAsUserInPkgA(
                "testMediaStoreManager_verifyCrossUserImagesInSharedStorage", currentUserId, args);

        // From clone user space
        runDeviceTestAsUserInPkgA(
                "testMediaStoreManager_verifyCrossUserImagesInSharedStorage",
                Integer.valueOf(mCloneUserId), args);
    }

    @Test
    public void testGetStorageVolumesIncludingSharedProfiles() throws Exception {
        assumeTrue(isAtLeastT());
        int currentUserId = getCurrentUserId();

        // Install the app in owner user space
        installPackage(APP_A, "--user " + currentUserId);

        Map<String, String> args = new HashMap<>();
        args.put(CLONE_USER_ID, mCloneUserId);
        runDeviceTestAsUserInPkgA("testStorageManager_verifyInclusionOfSharedProfileVolumes",
                currentUserId, args);
    }
}
