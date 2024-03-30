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

package android.app.sdksandbox.testutils;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.sdksandbox.IRemoteSdkCallback;
import android.os.Bundle;
import android.os.IBinder;
import android.view.SurfaceControlViewHost;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class FakeRemoteSdkCallback extends IRemoteSdkCallback.Stub {
    private final CountDownLatch mLoadSdkLatch = new CountDownLatch(1);
    private final CountDownLatch mSurfacePackageLatch = new CountDownLatch(1);

    private boolean mLoadSdkSuccess;
    private boolean mSurfacePackageSuccess;

    private int mErrorCode;
    private String mErrorMsg;

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
        mErrorMsg = errorMsg;
        mSurfacePackageLatch.countDown();
    }

    @Override
    public void onSurfacePackageReady(SurfaceControlViewHost.SurfacePackage surfacePackage,
                int surfacePackageId, Bundle params) {
        mSurfacePackageSuccess = true;
        mSurfacePackageLatch.countDown();
    }

    public boolean isLoadSdkSuccessful() {
        waitForLatch(mLoadSdkLatch);
        return mLoadSdkSuccess;
    }

    public IBinder getSdkToken() {
        waitForLatch(mLoadSdkLatch);
        assertWithMessage("Failed to load sdk: " + getLoadSdkErrorMsg())
            .that(mLoadSdkSuccess).isTrue();
        return mSdkToken;
    }

    public int getLoadSdkErrorCode() {
        waitForLatch(mLoadSdkLatch);
        assertThat(mLoadSdkSuccess).isFalse();
        return mErrorCode;
    }

    public String getLoadSdkErrorMsg() {
        waitForLatch(mLoadSdkLatch);
        return mErrorMsg;
    }

    public boolean isRequestSurfacePackageSuccessful() throws InterruptedException {
        waitForLatch(mSurfacePackageLatch);
        return mSurfacePackageSuccess;
    }

    public int getSurfacePackageErrorCode() {
        waitForLatch(mSurfacePackageLatch);
        assertThat(mSurfacePackageSuccess).isFalse();
        return mErrorCode;
    }

    public String getSurfacePackageErrorMsg() {
        waitForLatch(mSurfacePackageLatch);
        assertThat(mSurfacePackageSuccess).isFalse();
        return mErrorMsg;
    }

    private void waitForLatch(CountDownLatch latch) {
        try {
            // Wait for callback to be called
            final int waitTime = 5;
            if (!latch.await(waitTime, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                    "Callback not called within " + waitTime + " seconds");
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException(
                    "Interrupted while waiting on callback: " + e.getMessage());
        }
    }
}
