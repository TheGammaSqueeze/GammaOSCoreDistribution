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

package com.android.tests.codeprovider.storagetest;

import android.app.sdksandbox.SandboxedSdkContext;
import android.app.sdksandbox.SandboxedSdkProvider;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executor;

public class StorageTestSandboxedSdkProvider extends SandboxedSdkProvider {
    private static final String TAG = "StorageTestSandboxedSdkProvider";
    private static final String BUNDLE_KEY_PHASE_NAME = "phase-name";

    @Override
    public void initSdk(SandboxedSdkContext context, Bundle params, Executor executor,
            InitSdkCallback callback) {
        callback.onInitSdkFinished(null);
    }

    @Override
    public View getView(Context windowContext, Bundle params) {
        handlePhase(params);
        return null;
    }

    @Override
    public void onExtraDataReceived(Bundle extraData) {
    }

    private void handlePhase(Bundle params) {
        String phaseName = params.getString(BUNDLE_KEY_PHASE_NAME, "");
        Log.i(TAG, "Handling phase: " + phaseName);
        switch (phaseName) {
            case "testSdkSandboxDataAppDirectory_SharedStorageIsUsable":
                testSdkSandboxDataAppDirectory_SharedStorageIsUsable();
                break;
            case "testSdkDataIsAttributedToApp":
                testSdkDataIsAttributedToApp();
                break;
            default:
        }
    }

    private void testSdkSandboxDataAppDirectory_SharedStorageIsUsable() {
        String sharedPath = getSharedStoragePath();

        try {
            // Read the file
            String input = Files.readAllLines(Paths.get(sharedPath, "readme.txt")).get(0);

            // Create a dir
            Files.createDirectory(Paths.get(sharedPath, "dir"));
            // Write to a file
            Path filepath = Paths.get(sharedPath, "dir", "file");
            Files.createFile(filepath);
            Files.write(filepath, input.getBytes());
        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    private void testSdkDataIsAttributedToApp() {
        final byte[] buffer = new byte[1000000];
        String sharedPath = getSharedStoragePath();
        String sharedCachePath = getSharedStorageCachePath();
        try {
            Files.createDirectory(Paths.get(sharedPath, "attribution"));
            Path filepath = Paths.get(sharedPath, "attribution", "file");
            Files.createFile(filepath);
            Files.write(filepath, buffer);

            Files.createDirectory(Paths.get(sharedCachePath, "attribution"));
            Path cacheFilepath = Paths.get(sharedCachePath, "attribution", "file");
            Files.createFile(cacheFilepath);
            Files.write(cacheFilepath, buffer);
        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    // TODO(209061627): this should be coming from code context instead
    private String getSharedStoragePath() {
        return "/data/misc_ce/0/sdksandbox/com.android.tests.sdksandbox/shared";
    }

    // TODO(209061627): this should be coming from code context instead
    private String getSharedStorageCachePath() {
        return getSharedStoragePath() + "/cache";
    }
}
