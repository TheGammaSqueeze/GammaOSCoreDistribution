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

package android.scopedstorage.cts.device;

import static android.app.AppOpsManager.permissionToOp;
import static android.os.SystemProperties.getBoolean;
import static android.scopedstorage.cts.lib.TestUtils.allowAppOpsToUid;
import static android.scopedstorage.cts.lib.TestUtils.getPicturesDir;
import static android.scopedstorage.cts.lib.TestUtils.readMaximumRowIdFromDatabaseAs;
import static android.scopedstorage.cts.lib.TestUtils.readMinimumRowIdFromDatabaseAs;
import static android.scopedstorage.cts.lib.TestUtils.waitForMountedAndIdleState;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Instrumentation;
import android.content.ContentResolver;
import android.content.Context;
import android.provider.MediaStore;
import android.scopedstorage.cts.lib.TestUtils;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.cts.install.lib.TestApp;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public final class StableUrisTest extends ScopedStorageBaseDeviceTest {

    private static final String TAG = "StableUrisTest";

    // An app that has file manager (MANAGE_EXTERNAL_STORAGE) permission.
    private static final TestApp APP_FM = new TestApp("TestAppFileManager",
            "android.scopedstorage.cts.testapp.filemanager", 1, false,
            "CtsScopedStorageTestAppFileManager.apk");

    private static final String OPSTR_MANAGE_EXTERNAL_STORAGE =
            permissionToOp(Manifest.permission.MANAGE_EXTERNAL_STORAGE);

    private Context mContext;
    private ContentResolver mContentResolver;
    private UiDevice mDevice;

    @Parameter()
    public String mVolumeName;

    /** Parameters data. */
    @Parameterized.Parameters(name = "volume={0}")
    public static Iterable<?> data() {
        return Arrays.asList(MediaStore.VOLUME_EXTERNAL_PRIMARY);
    }

    @Before
    public void setUp() throws Exception {
        super.setupExternalStorage(mVolumeName);
        Log.d(TAG, "Using volume : " + mVolumeName);
        mContext = ApplicationProvider.getApplicationContext();
        mContentResolver = mContext.getContentResolver();
        final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        mDevice = UiDevice.getInstance(inst);
    }

    @Test
    public void testUrisMapToExistingIds_withoutNextRowIdBackup() throws Exception {
        assumeFalse(getBoolean("persist.sys.fuse.backup.nextrowid_enabled", true));
        testScenario(/* nextRowIdBackupEnabled */ false);
    }

    @Test
    public void testUrisMapToNewIds_withNextRowIdBackup() throws Exception {
        assumeTrue(getBoolean("persist.sys.fuse.backup.nextrowid_enabled", false));
        testScenario(/* nextRowIdBackupEnabled */ true);
    }

    private void testScenario(boolean nextRowIdBackupEnabled) throws Exception {
        List<File> files = new ArrayList<>();

        try {
            // Test App needs to be explicitly granted MES app op.
            final int fmUid = mContext.getPackageManager().getPackageUid(APP_FM.getPackageName(),
                    0);
            allowAppOpsToUid(fmUid, OPSTR_MANAGE_EXTERNAL_STORAGE);

            files = createFilesAsTestApp(APP_FM, 5);

            long maxRowIdOfInternalDbBeforeReset = readMaximumRowIdFromDatabaseAs(APP_FM,
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_INTERNAL));
            Log.d(TAG, "maxRowIdOfInternalDbBeforeReset:" + maxRowIdOfInternalDbBeforeReset);
            long maxRowIdOfExternalDbBeforeReset = readMaximumRowIdFromDatabaseAs(APP_FM,
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL));
            Log.d(TAG, "maxRowIdOfExternalDbBeforeReset:" + maxRowIdOfExternalDbBeforeReset);

            // Clear MediaProvider package data to trigger DB recreation.
            mDevice.executeShellCommand("pm clear com.google.android.providers.media.module");
            waitForMountedAndIdleState(mContentResolver);
            MediaStore.scanVolume(mContentResolver, mVolumeName);

            long minRowIdOfInternalDbAfterReset = readMinimumRowIdFromDatabaseAs(APP_FM,
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_INTERNAL));
            Log.d(TAG, "minRowIdOfInternalDbAfterReset:" + minRowIdOfInternalDbAfterReset);
            long minRowIdOfExternalDbAfterReset = readMinimumRowIdFromDatabaseAs(APP_FM,
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL));
            Log.d(TAG, "minRowIdOfExternalDbAfterReset:" + minRowIdOfExternalDbAfterReset);

            if (nextRowIdBackupEnabled) {
                assertWithMessage(
                        "Expected minimum row id after internal database reset to be greater "
                                + "than max row id before reset").that(
                        minRowIdOfInternalDbAfterReset > maxRowIdOfInternalDbBeforeReset).isTrue();
                assertWithMessage(
                        "Expected minimum row id after external database reset to be greater "
                                + "than max row id before reset").that(
                        minRowIdOfExternalDbAfterReset > maxRowIdOfExternalDbBeforeReset).isTrue();
            } else {
                assertWithMessage(
                        "Expected internal database row ids to be reused without next row id "
                                + "backup").that(
                        minRowIdOfInternalDbAfterReset <= maxRowIdOfInternalDbBeforeReset).isTrue();
                assertWithMessage(
                        "Expected external database row ids to be reused without next row id "
                                + "backup").that(
                        minRowIdOfExternalDbAfterReset <= maxRowIdOfExternalDbBeforeReset).isTrue();
            }

        } finally {
            for (File file : files) {
                file.delete();
            }
        }
    }

    private List<File> createFilesAsTestApp(TestApp app, int count) throws Exception {
        List<File> files = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            final File file = new File(getPicturesDir(),
                    "Cts_" + System.currentTimeMillis() + ".jpg");
            TestUtils.createFileAs(app, file.getAbsolutePath());
            MediaStore.scanFile(mContentResolver, file);
            files.add(file);
        }

        return files;
    }

}
