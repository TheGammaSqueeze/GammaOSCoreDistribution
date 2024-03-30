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

package com.android.tests.sdksandbox.endtoend;

import static com.google.common.truth.Truth.assertThat;

import android.app.sdksandbox.IRemoteSdkCallback;
import android.app.sdksandbox.SdkSandboxManager;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.view.SurfaceControlViewHost;

import androidx.test.InstrumentationRegistry;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
// These tests seems to be broken, TODO(b/220920259): re-enable tests once they are fixed
public class SdkSandboxManagerTest {

    private static SdkSandboxManager sSdkSandboxManager;
    private static final String CODE_PROVIDER_KEY = "sdk-provider-class";
    private static FakeIniSdkCallback sCallback = new FakeIniSdkCallback();
    private static final String CODE_PROVDER_NAME = "com.android.sdksandboxcode.v1";

    @BeforeClass
    public static void setup() {
        Context context = InstrumentationRegistry.getContext();
        sSdkSandboxManager = context.getSystemService(SdkSandboxManager.class);
        sSdkSandboxManager.loadSdk(
                CODE_PROVDER_NAME, new Bundle(), sCallback);
    }

    @Test
    public void loadSdkSuccess() throws Exception {
        // This should be successful, because this test uses version 1 of the sdk library. If
        // version 2 is loaded, the test will fail.
        assertThat(sCallback.isLoadSdkSuccessful()).isTrue();
    }

    @Test
    public void loadSdkFailureAlreadyLoaded() {
        FakeIniSdkCallback cb = new FakeIniSdkCallback();
        sSdkSandboxManager.loadSdk(
                CODE_PROVDER_NAME, new Bundle(), cb);
        assertThat(cb.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.LOAD_SDK_SDK_ALREADY_LOADED);
    }

    @Test
    public void loadCodeFailureNotFound() {
        FakeIniSdkCallback cb = new FakeIniSdkCallback();
        sSdkSandboxManager.loadSdk(
                "nonexistent.shared.lib", new Bundle(), cb);
        assertThat(cb.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.LOAD_SDK_SDK_NOT_FOUND);
    }

    @Test
    public void surfacePackageSuccess() throws Exception {
        IBinder codeToken = sCallback.getSdkToken();
        assertThat(codeToken).isNotNull();

        sSdkSandboxManager.requestSurfacePackage(codeToken, new Binder(), 0,
                new Bundle());
        assertThat(sCallback.isRequestSurfacePackageSuccessful()).isTrue();
    }

    @Test
    public void testResourcesAndAssets() {
        Bundle params = new Bundle();
        params.putString(CODE_PROVIDER_KEY,
                "com.android.codeproviderresources.ResourceSandboxedSdkProvider");
        FakeIniSdkCallback callback = new FakeIniSdkCallback();
        sSdkSandboxManager.loadSdk("com.android.codeproviderresources",
                params, callback);
        assertThat(callback.getErrorMessage()).isNull();
    }

    private static class FakeIniSdkCallback extends IRemoteSdkCallback.Stub {
        private final CountDownLatch mLoadSdkLatch = new CountDownLatch(1);
        private final CountDownLatch mSurfacePackageLatch = new CountDownLatch(1);

        private boolean mLoadSdkSuccess;
        private boolean mSurfacePackageSuccess;
        private String mErrorMsg;

        private int mErrorCode;

        private IBinder mSdkToken;

        @Override
        public void onLoadSdkSuccess(IBinder sdkToken, Bundle params) {
            mSdkToken = sdkToken;
            mLoadSdkSuccess = true;
            mLoadSdkLatch.countDown();
        }

        @Override
        public void onLoadSdkFailure(int errorCode, String errorMsg) {
            mLoadSdkSuccess = false;
            mErrorCode = errorCode;
            mErrorMsg = errorMsg;
            mLoadSdkLatch.countDown();
        }

        @Override
        public void onSurfacePackageError(int errorCode, String errorMsg) {
            mSurfacePackageSuccess = false;
            mErrorCode = errorCode;
            mSurfacePackageLatch.countDown();
        }

        @Override
        public void onSurfacePackageReady(SurfaceControlViewHost.SurfacePackage surfacePackage,
                int surfacePackageId, Bundle params) {
            mSurfacePackageSuccess = true;
            mSurfacePackageLatch.countDown();
        }

        void waitForLatch(CountDownLatch latch) {
            try {
                // Wait for callback to be called
                if (!latch.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Callback not called within 2 seconds");
                }
            } catch (InterruptedException e) {
                throw new IllegalStateException(
                        "Interrupted while waiting on callback: " + e.getMessage());
            }
        }

        boolean isLoadSdkSuccessful() throws InterruptedException {
            waitForLatch(mLoadSdkLatch);
            return mLoadSdkSuccess;
        }

        boolean isRequestSurfacePackageSuccessful() throws InterruptedException {
            waitForLatch(mSurfacePackageLatch);
            return mSurfacePackageSuccess;
        }

        int getLoadSdkErrorCode() {
            waitForLatch(mLoadSdkLatch);
            assertThat(mLoadSdkSuccess).isFalse();
            return mErrorCode;
        }

        String getErrorMessage() {
            waitForLatch(mLoadSdkLatch);
            return mErrorMsg;
        }

        IBinder getSdkToken() {
            waitForLatch(mLoadSdkLatch);
            assertThat(mLoadSdkSuccess).isTrue();
            return mSdkToken;
        }
    }
}
