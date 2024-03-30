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

package com.android.sdksandboxclient;

import android.app.Activity;
import android.app.sdksandbox.IRemoteSdkCallback;
import android.app.sdksandbox.SdkSandboxManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.android.sdksandbox.SdkSandboxServiceImpl;

public class MainActivity extends Activity {

    private boolean mSdkLoaded = false;
    private SdkSandboxManager mSdkSandboxManager;

    private IBinder mToken;
    private Button mLoadButton;
    private Button mRenderButton;
    private SurfaceView mRenderedView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSdkSandboxManager = getApplicationContext().getSystemService(
                SdkSandboxManager.class);

        mRenderedView = findViewById(R.id.rendered_view);
        mRenderedView.setZOrderOnTop(true);
        mRenderedView.setVisibility(View.INVISIBLE);

        mLoadButton = findViewById(R.id.load_code_button);
        mRenderButton = findViewById(R.id.request_surface_button);
        registerLoadSdkProviderButton();
        registerLoadSurfacePackageButton();
    }


    private class RemoteSdkCallbackImpl extends IRemoteSdkCallback.Stub {

        private RemoteSdkCallbackImpl() {
        }

        @Override
        public void onLoadSdkSuccess(IBinder token, Bundle bundle) {
            mSdkLoaded = true;
            mToken = token;
            makeToast("Loaded successfully!");
        }

        @Override
        public void onLoadSdkFailure(int errorCode, String errorMessage) {
            makeToast("Failed: " + errorMessage);
        }

        @Override
        public void onSurfacePackageReady(SurfaceControlViewHost.SurfacePackage surfacePackage,
                int i, Bundle bundle) {
            new Handler(Looper.getMainLooper()).post(() -> {
                mRenderedView.setChildSurfacePackage(surfacePackage);
                mRenderedView.setVisibility(View.VISIBLE);
            });
            makeToast("Rendered surface view");
        }

        @Override
        public void onSurfacePackageError(int errorCode, String errorMessage) {
            makeToast("Failed: " + errorMessage);
        }
    }


    private void registerLoadSdkProviderButton() {
        mLoadButton.setOnClickListener(v -> {
            Bundle params = new Bundle();
            params.putString(SdkSandboxServiceImpl.SDK_PROVIDER_KEY,
                    "com.android.sdksandboxcode.SampleSandboxedSdkProvider");
            params.putInt(SdkSandboxServiceImpl.WIDTH_KEY, mRenderedView.getWidth());
            params.putInt(SdkSandboxServiceImpl.HEIGHT_KEY, mRenderedView.getHeight());
            final RemoteSdkCallbackImpl callback = new RemoteSdkCallbackImpl();
            mSdkSandboxManager.loadSdk(
                    "com.android.sdksandboxcode.v1",  params, callback);
        });
    }

    private void registerLoadSurfacePackageButton() {
        mRenderButton.setOnClickListener(v -> {
            if (mSdkLoaded) {
                new Handler(Looper.getMainLooper()).post(
                        () -> mSdkSandboxManager.requestSurfacePackage(
                                mToken, new Binder(), getDisplay().getDisplayId(), new Bundle()));
            } else {
                makeToast("Sdk is not loaded");
            }
        });
    }

    private void makeToast(String message) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
    }

}
