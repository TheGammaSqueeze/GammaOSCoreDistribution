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

package com.android.sdksandbox;

import android.os.Bundle;
import android.os.IBinder;
import android.view.SurfaceControlViewHost.SurfacePackage;

import com.android.sdksandbox.ISdkSandboxManagerToSdkSandboxCallback;

/** @hide */
oneway interface ISdkSandboxToSdkSandboxManagerCallback {
    const int LOAD_SDK_ALREADY_LOADED = 1;
    const int LOAD_SDK_PROVIDER_INIT_ERROR = 2;
    const int LOAD_SDK_NOT_FOUND = 3;
    const int LOAD_SDK_INSTANTIATION_ERROR = 3;

    const int SURFACE_PACKAGE_INTERNAL_ERROR = 700;

    void onLoadSdkSuccess(in Bundle params, in ISdkSandboxManagerToSdkSandboxCallback callback);
    void onLoadSdkError(int errorCode, in String errorMessage);

    void onSurfacePackageReady(in SurfacePackage surfacePackage, int surfacePackageId, in Bundle params);
    void onSurfacePackageError(int errorCode, in String errorMessage);
}
