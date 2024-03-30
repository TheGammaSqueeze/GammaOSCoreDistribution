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

package com.android.server.sdksandbox;

import android.annotation.NonNull;
import android.annotation.SdkConstant;
import android.annotation.SystemApi;
import android.content.Intent;
import android.content.pm.ApplicationInfo;

/**
 * Exposes APIs to {@code system_server} components outside of the module boundaries.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
public interface SdkSandboxManagerLocal {

    @SdkConstant(SdkConstant.SdkConstantType.SERVICE_ACTION)
    String SERVICE_INTERFACE = "com.android.sdksandbox.SdkSandboxService";

    /**
     * Broadcast Receiver listen to sufficient verifier requests from Package Manager
     * when install new SDK, to verifier SDK code during installation time
     * and terminate install if SDK not compatible with privacy sandbox restrictions.
     */
    @SdkConstant(SdkConstant.SdkConstantType.BROADCAST_INTENT_ACTION)
    String VERIFIER_RECEIVER = "com.android.server.sdksandbox.SdkSandboxVerifierReceiver";

    /**
     * Enforces that the sdk sandbox process is allowed to broadcast a given intent.
     *
     * @param intent the intent to check.
     * @throws SecurityException if the intent is not allowed to be broadcast.
     */
    void enforceAllowedToSendBroadcast(@NonNull Intent intent);


    /**
     * Enforces that the sdk sandbox process is allowed to start an activity with a given intent.
     *
     * @param intent the intent to check.
     * @throws SecurityException if the activity is not allowed to be started.
     */
    void enforceAllowedToStartActivity(@NonNull Intent intent);

    /**

     * Enforces that the sdk sandbox process is allowed to start or bind to a service with a given
     * intent.
     *
     * @param intent the intent to check.
     * @throws SecurityException if the service is not allowed to be started or bound to.
     */
    void enforceAllowedToStartOrBindService(@NonNull Intent intent);

    /**
     * Returns name of the sdk sandbox process that corresponds to the given client app.
     *
     * @param clientAppInfo {@link ApplicationInfo} of the given client app
     * @return name of the sdk sandbox process to be instrumented
     */
    @NonNull
    String getSdkSandboxProcessNameForInstrumentation(@NonNull ApplicationInfo clientAppInfo);

    /**
     * Called by the {@code ActivityManagerService} to notify that instrumentation of the
     * sdk sandbox process that belongs to the client app is about to start.
     *
     * <p>If there is a running instance of the sdk sandbox process, then it will be stopped.
     * While the instrumentation for the sdk sandbox is running, the corresponding client app
     * won't be allowed to connect to the instrumented sdk sandbox process.
     *
     * @param clientAppPackageName package name of the client app
     * @param clientAppUid         uid of the client app
     */
    void notifyInstrumentationStarted(@NonNull String clientAppPackageName, int clientAppUid);

    /**
     * Called by the {@code ActivityManagerService} to notify that instrumentation of the
     * sdk sandbox process that belongs to the client app finished.
     *
     * <p>This method must be called after the instrumented sdk sandbox process has been stopped.
     *
     * <p>Once the instrumentation finishes, the client app will be able to connect to the new
     * instance of its sdk sandbox process.
     *
     * @param clientAppPackageName package name of the client app
     * @param clientAppUid         uid of the client app
     */
    void notifyInstrumentationFinished(@NonNull String clientAppPackageName, int clientAppUid);
}