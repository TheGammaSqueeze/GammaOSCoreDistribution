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

import android.annotation.Nullable;
import android.content.ServiceConnection;

import com.android.sdksandbox.ISdkSandboxService;

import java.io.PrintWriter;

/**
 * Interface to get hold of SdkSandbox service
 *
 * @hide
 */
public interface SdkSandboxServiceProvider {
    /**
     * Bind to and establish a connection with SdkSandbox service.
     * @param appPackageName is the package of the calling app.
     * @param appUid is the calling app Uid.
     * @param serviceConnection recieves information when service is started and stopped.
     */
    void bindService(int appUid, String appPackageName, ServiceConnection serviceConnection);

    /**
     * Unbind the SdkSandbox service associated with the app.
     */
    void unbindService(int appUid);

    /**
     * Return bound {@link ISdkSandboxService} connected for {@code appUid} or otherwise
     * {@code null}.
    */
    @Nullable
    ISdkSandboxService getBoundServiceForApp(int appUid);

    /**
     * Set bound SdkSandbox service for {@code appUid}.
     */
    void setBoundServiceForApp(int appUid, @Nullable ISdkSandboxService service);

    /** Dump debug information for adb shell dumpsys */
    default void dump(PrintWriter writer) {
    }
}
