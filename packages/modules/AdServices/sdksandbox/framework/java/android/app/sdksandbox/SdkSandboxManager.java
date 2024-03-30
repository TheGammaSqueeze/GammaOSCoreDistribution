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

package android.app.sdksandbox;

import static android.app.sdksandbox.SdkSandboxManager.SDK_SANDBOX_SERVICE;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemService;
import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Provides APIs to load {@link android.content.pm.SharedLibraryInfo#TYPE_SDK_PACKAGE SDKs}
 * into sdk sandbox process, and then interact with them.
 *
 * <p>NOTE: currently {@link SdkSandboxManager} is
 * {@link SdkSandboxManager#SDK_SANDBOX_STATE_DISABLED disabled}. Eventually, this functionality
 * will be enabled via a
 * <a href="https://source.android.com/devices/architecture/modular-system">Mainline update</a>.
 *
 * <p>{@code SdkSandbox} is a java process running in a separate uid range. Each app has its own
 * sdk sandbox process.
 *
 * <p>First app needs to declare {@code SDKs} it depends on in it's {@code AndroidManifest.xml}
 * using {@code <uses-sdk-library>} tag. App can only load {@code SDKs} it depends on into the
 * {@code SdkSandbox}.
 *
 * @see android.content.pm.SharedLibraryInfo#TYPE_SDK_PACKAGE
 * @see
 * <a href="https://developer.android.com/design-for-safety/ads/sdk-runtime">SDK runtime design proposal</a>
 */
@SystemService(SDK_SANDBOX_SERVICE)
public final class SdkSandboxManager {

    /**
     * Use with {@link Context#getSystemService(String)} to retrieve a {@link SdkSandboxManager} for
     * interacting with the SDKs belonging to this application.
     *
     * @hide
     */
    public static final String SDK_SANDBOX_SERVICE = "sdk_sandbox";

    private final ISdkSandboxManager mService;
    private final Context mContext;

    /** @hide */
    public SdkSandboxManager(@NonNull Context context, @NonNull ISdkSandboxManager binder) {
        mContext = context;
        mService = binder;
    }

    /**
     * Sdk Sandbox is disabled.
     *
     * <p>{@link SdkSandboxManager} APIs are hidden. Attempts at calling them will result in
     * {@link UnsupportedOperationException}.
     */
    public static final int SDK_SANDBOX_STATE_DISABLED = 0;

    /**
     * Sdk Sandbox is enabled.
     *
     * <p>App can use {@link SdkSandboxManager} APIs to load {@code SDKs} it depends on into the
     * corresponding {@code SdkSandbox} process.
     */
    public static final int SDK_SANDBOX_STATE_ENABLED_PROCESS_ISOLATION = 2;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "SDK_SANDBOX_STATUS_", value = {
            SDK_SANDBOX_STATE_DISABLED,
            SDK_SANDBOX_STATE_ENABLED_PROCESS_ISOLATION,
    })
    public @interface SdkSandboxState {}

    /**
     * Returns current state of the {@code SdkSandbox}.
     */
    @SdkSandboxState
    public static int getSdkSandboxState() {
        return SDK_SANDBOX_STATE_DISABLED;
    }

    /**
     * Fetches and loads sdk into the sdk sandbox.
     *
     * @hide
     */
    public void loadSdk(String name, Bundle params, IRemoteSdkCallback callback) {
        try {
            mService.loadSdk(mContext.getPackageName(), name, params, callback);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sends a request for a surface package to the remote sdk.
     *
     * @hide
     */
    public void requestSurfacePackage(IBinder sdkToken, IBinder hostToken, int displayId,
            Bundle params) {
        try {
            mService.requestSurfacePackage(sdkToken, hostToken, displayId, params);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sends a bundle to supplemental process.
     *
     * @hide
     */
    public void sendData(int id, Bundle params) {
        try {
            mService.sendData(id, params);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Error code to represent that there is no such code.
     *
     * @hide
     */
    public static final int LOAD_SDK_SDK_NOT_FOUND = 100;

    /**
     * Error code to represent code is already loaded.
     *
     * @hide
     */
    public static final int LOAD_SDK_SDK_ALREADY_LOADED = 101;

    /**
     * Error code representing a generic error that occurred while attempting to load an SDK.
     *
     * @hide
     */
    public static final int LOAD_SDK_INTERNAL_ERROR = 500;

    /**
     * Error code representing a generic error that occurred while attempting to remotely render
     * a view from an SDK inside the SDK sandbox.
     *
     * @hide
     */
    public static final int SURFACE_PACKAGE_INTERNAL_ERROR = 700;
}
