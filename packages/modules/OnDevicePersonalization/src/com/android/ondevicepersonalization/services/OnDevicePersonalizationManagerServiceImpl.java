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

package com.android.ondevicepersonalization.services;

import android.app.Service;
import android.content.Intent;
import android.ondevicepersonalization.aidl.IOnDevicePersonalizationManagerService;
import android.os.IBinder;

/** Implementation of OnDevicePersonalization Service */
public class OnDevicePersonalizationManagerServiceImpl extends Service {
    private IOnDevicePersonalizationManagerService.Stub mBinder;

    @Override
    public void onCreate() {
        mBinder = new OnDevicePersonalizationManagerServiceDelegate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    String getVersion() {
        return "1.0";
    }

    final class OnDevicePersonalizationManagerServiceDelegate
            extends IOnDevicePersonalizationManagerService.Stub {
        @Override
        public String getVersion() {
            return OnDevicePersonalizationManagerServiceImpl.this.getVersion();
        }
    }
}
